package scala.actors

final class ActorRef {

  private[actors] var actor: RichActor = _

  def !(msg: Any): Unit =
    actor ! msg

  // TODO: deprecate?
  def start(): Unit =
    actor.start()

  def stop(): Unit = {
    // TODO
  }

}
