package scala.actors

import scala.util.continuations._

trait RichActor extends InternalReactor[Any] {

  private[actors] def isActor = false

  // creation check (see ActorRef)
  private val context = ActorSystem.contextStack.get
  if (context.isEmpty && !isActor)
    throw new Exception("must use actorOf to create actor")
  else {
    if (!context.head && !isActor)
      throw new Exception("must use actorOf to create actor")
    else
      ActorSystem.contextStack.set(context.push(false))
  }

  type Receive = PartialFunction[Any, Unit @suspendable]

  // receive, process, serve, accept, onMessage, respond, response, handle, handler
  def handle: Receive

  /**
   * Receives a message from this $actor's mailbox.
   *
   * @param  handler  a partial function with message patterns and actions
   */
  protected def react(handler: PartialFunction[Any, Unit]): /*Nothing*/Unit @suspendable = {
    shift { (k: Unit => Any) => {
      synchronized { drainSendBuffer(mailbox) }
      searchMailbox(mailbox, handler andThen k, false)
      throw Actor.suspendException
    } }
  }

  /**
   * Starts this $actor. This method is idempotent.
   *
   * TODO: make private, invoke from inside actorOf
   */
  /*private[actors]*/ def start(): RichActor = synchronized {
    dostart()
    this
  }

  private[actors] override def internalAct() {
    reset {
      while (true) {
        react(new PartialFunction[Any, Unit] {
          def isDefinedAt(x: Any) =
            handle.isDefinedAt(x)
          def apply(x: Any) =
            reset { handle(x) }
        })
      }
    }
  }

  // TODO (VJ): build into ActorRef
  protected[actors] def stop(): Nothing =
    internalExit()
  
    /*
   * Following methods are copied from Actor and deprecated in order to make the transition to AKKA actors smoother.
   */
  import scala.actors.Actor._
  
  // guarded by this
  private[actors] var exitReason: AnyRef = 'normal
  
  /**
   * <p>
   *   Terminates execution of <code>self</code> with the following
   *   effect on linked actors:
   * </p>
   * <p>
   *   For each linked actor <code>a</code> with
   *   <code>trapExit</code> set to <code>true</code>, send message
   *   <code>Exit(self, reason)</code> to <code>a</code>.
   * </p>
   * <p>
   *   For each linked actor <code>a</code> with
   *   <code>trapExit</code> set to <code>false</code> (default),
   *   call <code>a.exit(reason)</code> if
   *   <code>reason != 'normal</code>.
   * </p>
   */
  @deprecated("Exists only for purposes of smooth transition to AKKA actors.", "2.10")
  protected[actors] def exit(reason: AnyRef): Nothing = {
    synchronized {
      exitReason = reason
    }
    exit()
  }

  /**
   * Terminates with exit reason <code>'normal</code>.
   */
  @deprecated("Exists only for purposes of smooth transition to AKKA actors.", "2.10")
  protected[actors] def exit(): Nothing = {
//    val todo = synchronized {
//      if (!links.isEmpty)
//        exitLinked()
//      else
//        () => {}
//    }
//    todo()
    internalExit()
  }
  
  @deprecated("Exists only for purposes of smooth transition to AKKA actors.", "2.10") 
  protected[actors] def mailboxSize: Int =
    mailbox.size
  
}
