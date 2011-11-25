package scala.actors

import scala.util.continuations._
import scala.collection._

trait RichActor extends InternalActor {
  type Receive = PartialFunction[Any, Unit @suspendable]
  
  creationCheck;
  
  // TODO (VJ)
  def self: ActorRef = null
  
  def receiveTimeout: Option[Long] 
  
  /**
   * Migration notes:
   *   this method replaces receiveWithin, receive and react methods from Scala Actors.
   */
  def handle: Receive
      
  /**
   * User overridable callback.
   * <p/>
   * Is called when an Actor is started by invoking 'actor'.
   */
  def preStart() {}

  /**
   * User overridable callback.
   * <p/>
   * Is called when 'actor.stop()' is invoked.
   */
  def postStop() {}
  
  /**
   * User overridable callback.
   * <p/>
   * Is called on a crashed Actor right BEFORE it is restarted to allow clean
   * up of resources before Actor is terminated.
   * By default it calls postStop()
   */
  def preRestart(reason: Throwable, message: Option[Any]) { postStop() }
  
  /**
   * Changes the Actor's behavior to become the new 'Receive' (PartialFunction[Any, Unit]) handler.
   * Puts the behavior on top of the hotswap stack.
   * If "discardOld" is true, an unbecome will be issued prior to pushing the new behavior to the stack
   */
  def become(behavior: Receive, discardOld: Boolean = true) {
	if (discardOld) unbecome()
	behaviorStack.push()
  }

  /**
   * Reverts the Actor behavior to the previous one in the hotswap stack.
   */
  def unbecome() {
    // never unbecome the initial behavior
    if (behaviorStack.size > 1)
      behaviorStack = behaviorStack.pop
  }

  /**
   * User overridable callback.
   * <p/>
   * Is called when a message isn't handled by the current behavior of the actor
   * by default it does: EventHandler.warning(self, message)
   */
  def unhandled(message: Any) {
    message match {
      // TODO(VJ) introduce self that returns ActorRef
      case _ => throw new UnhandledMessageException(message, null) /* self */
    }
  }
  
  /*
   * Deprecated part of the API. Used only for smoother transition between scala and akka actors
   */
  
  @deprecated("use self.reply instead")
  protected[actors] override def reply(msg: Any) = super.reply(msg)
      
  @deprecated("use self.forward instead")
  override def forward(msg: Any) = super.forward(msg)
  
  @deprecated("use handle method")
  override def reactWithin(msec: Long)(handler: PartialFunction[Any, Unit]): Unit @suspendable = 
    super.reactWithin(msec)(handler)    
      
  @deprecated("use handle method")
  override def act(): Unit = internalAct()
  
  @deprecated("use preRestart")
  protected[actors] override def exceptionHandler: PartialFunction[Exception, Unit] = {
    case e => preRestart(e, None) // TODO (VJ) this is not correct. What is the alternative? 
  }   
 
  @deprecated("there will be no replacement in akka")
  protected[actors] override def scheduler: IScheduler = super.scheduler
     
  @deprecated("there will be no replacement in akka")
  protected[actors] override def mailboxSize: Int = super.mailboxSize
  
  @deprecated("there will be no replacement in akka")
  override def getState: Actor.State.Value = super.getState
 
  @deprecated("use postStop instead")
  protected[actors] override def exit(reason: AnyRef): Nothing = {
    super.exit(reason)
  }
  
  @deprecated("use postStop instead")
  protected[actors] override def exit(): Nothing = {
    super.exit()
  }
  
  @deprecated("use preStart instead")
  override def start(): RichActor = synchronized {     
    super.start()
    this
  }
  
  @deprecated("use akka instead")
  override def link(to: AbstractActor): AbstractActor = super.link(to)
  
  @deprecated("use akka instead")
  override def link(body: => Unit): Actor = super.link(body)
  
  @deprecated("use akka instead")
  override def unlink(from: AbstractActor) = super.unlink(from)
    
  /*
   * Internal implementation.
   */
  
  private[actors] var behaviorStack = immutable.Stack[PartialFunction[Any, Unit]]()

  /*
   * Checks that RichActor can be created only by ActorSystem.actorOf method.
   */
  private[this] def creationCheck = {

    // creation check (see ActorRef)
    val context = ActorSystem.contextStack.get
    if (context.isEmpty)
      throw new Exception("must use actorOf to create actor")
    else {
      if (!context.head)
        throw new Exception("must use actorOf to create actor")
      else
        ActorSystem.contextStack.set(context.push(false))
    }
    
  }
  
  private[actors] override def preAct() {
    preStart()
  }
  
  /*
   * Method that models the behavior of Akka actors.  
   */
  private[actors] def internalAct() {
    reset {

      behaviorStack = behaviorStack.push(new PartialFunction[Any, Unit] {
        def isDefinedAt(x: Any) =
          handle.isDefinedAt(x)
        def apply(x: Any) =
          reset { handle(x) }
      } orElse {
        case m => unhandled(m)
      })
      

      while (true)
        if (receiveTimeout.isDefined)
          reactWithin(receiveTimeout.get)(behaviorStack.top) // orElse 
        else
          react(behaviorStack.top) 
      
    }
  }
  
  private[actors] override def internalPostStop() = postStop() 
  
  lazy val ReceiveTimeout = TIMEOUT
}

/**
 * This message is thrown by default when an Actors behavior doesn't match a message
 */
case class UnhandledMessageException(msg: Any, ref: ActorRef = null) extends Exception {

  def this(msg: String) = this(msg, null)

  // constructor with 'null' ActorRef needed to work with client instantiation of remote exception
  override def getMessage =
    if (ref ne null) "Actor %s does not handle [%s]".format(ref, msg)
    else "Actor does not handle [%s]".format(msg)

  override def fillInStackTrace() = this //Don't waste cycles generating stack trace
}