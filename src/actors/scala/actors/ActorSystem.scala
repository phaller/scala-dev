package scala.actors

import scala.collection.immutable.Stack

object ActorSystem {

  private[actors] val contextStack = new ThreadLocal[Stack[Boolean]] {
    override def initialValue() = Stack[Boolean]()
  }

  private def withCleanContext(block: => ActorRef): ActorRef = {
    // push clean marker
    val old = contextStack.get
    contextStack.set(old.push(true))
    try {
      val instance = block

      if (instance eq null)
        throw new Exception("Actor instance passed to actorOf can't be 'null'")

      instance
    } finally {
      val stackAfter = contextStack.get
      if (stackAfter.nonEmpty)
        contextStack.set(if (!stackAfter.head) stackAfter.pop.pop else stackAfter.pop)
    }
  }

  // actorOf(new MyActor())
  def actorOf(factory: ⇒ RichActor): ActorRef = withCleanContext {
    val r = new ActorRef
    r.actor = factory
    r
  }

  def actorOf[T <: RichActor](implicit m: Manifest[T]): ActorRef = withCleanContext {
    val clazz = m.erasure.asInstanceOf[Class[_ <: RichActor]]
    val r = new ActorRef
    r.actor = clazz.newInstance()
    r
  }

}
