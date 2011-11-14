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

  // TODO: build into ActorRef
  protected[actors] def stop(): Nothing =
    internalExit()

}
