package scala.actors

trait ActorRef {

  private[actors] var actor: InternalActor = _

  /**
   * Sends a message asynchronously, returning a future which may eventually hold the reply.
   */
  def ?(message: Any): Future[Any] = null
  
  def !(msg: Any): Unit = actor ! msg 
  
  def start(): ActorRef = {
    actor.start();
    this
  }

  def stop(): Unit = {
    // TODO (VJ) do in the same manner as link. This should be implemented on InternalActor level or above.
  }
  
  
  
  /**
   * Forwards the message and passes the original sender actor as the sender.
   * <p/>
   * Works with '!' and '?'/'ask'.
   */
  def forward(message: Any) = actor.forward(message)

}


