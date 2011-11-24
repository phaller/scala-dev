package scala.actors

import scala.util.continuations._
import scala.collection._

trait RichActor extends InternalActor {
  type Receive = PartialFunction[Any, Unit @suspendable]
  
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
   * Deprecated part of the API. Used only for smoother transition between scala and akka actors
   */
  
  @deprecated("use handle method")
  override def reactWithin(msec: Long)(handler: PartialFunction[Any, Unit]): Unit @suspendable = 
    super.reactWithin(msec)(handler)    
      
  @deprecated("use handle method")
  override def act(): Unit = internalAct()
  
  @deprecated("use preRestart")
  protected[actors] override def exceptionHandler: PartialFunction[Exception, Unit] = {
    case e => preRestart(e, None)
  }   
 
  @deprecated("there will be no replacement in akka")
  protected[actors] override def scheduler: IScheduler = super.scheduler
     
  @deprecated("there will be no replacement in akka")
  protected[actors] def mailboxSize: Int = super.mailboxSize
  
  @deprecated("there will be no replacement in akka")
  override def getState: Actor.State.Value = super.getState

  // TODO (VJ) we need to call preStop in all places where the actor stops
  @deprecated("use pre stop instead")
  protected[actors] def exit(reason: AnyRef): Nothing = super.exit(reason)
  
  @deprecated("use pre stop instead")
  protected[actors] override def exit(): Nothing = super.exit()
  
  @deprecated("use preStart instead")
  override def start(): RichActor = synchronized {
    preStart()
    super.start()
    this
  }
  
  @deprecated("use akka instead")
  override def link(to: AbstractActor): AbstractActor = super.link(to)
  
  @deprecated("use akka instead")
  override def link(body: => Unit): Actor = super.link(body)
  
  @deprecated("use akka instead")
  override def unlink(from: AbstractActor) = super.unlink(from)
    
  /**
   * Internal implementation.
   */

  private[actors] var behaviorStack = immutable.Stack[PartialFunction[Any, Unit]]()
  
  private[actors] def internalAct() {
    reset {    
      
      behaviorStack = behaviorStack.push(new PartialFunction[Any, Unit] {
        def isDefinedAt(x: Any) =
          handle.isDefinedAt(x)
        def apply(x: Any) =
          reset { handle(x) }
      })
      
      while (true) {        
        if (receiveTimeout.isDefined)
          // TODO (VJ) check the boundary condition receiveTimeout < 1
          reactWithin(receiveTimeout.get)(behaviorStack.top) // orElse 
        else 
          react(behaviorStack.top) // orElse
      }
    }
  }
  
  private[actors] override def timeoutMessage = ReceiveTimeout
 
}

// TODO (VJ) talk to philipp about all the places where Timeout is used
// TODO (VJ) talk to philipp about package names
case object ReceiveTimeout