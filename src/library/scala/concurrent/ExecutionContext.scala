package scala.concurrent

import java.util.concurrent.{ Executors, Future => JFuture }
import scala.util.{ Duration, Timeout }
import scala.concurrent.forkjoin._

trait ExecutionContext {

  def dispatchTask(task: () => Unit): Unit

  def newPromise[T](body: () => T): Promise[T]

  def newPromise[T](timeout: Timeout, body: () => T): Promise[T] =
    newPromise(body) // TODO add timeout

}

/* DONE: The challenge is to make ForkJoinPromise inherit from RecursiveAction
 * to avoid an object allocation per promise. This requires turning DefaultPromise
 * into a trait, i.e., removing its constructor parameters.
 */
private[concurrent] class ForkJoinPromise[T](context: ForkJoinExecutionContext, body: () => T, within: Long) extends DefaultPromise[T] {

  val timeout = Timeout(within)
  implicit val dispatcher = context

  // body of RecursiveAction
  override def compute(): Unit =
    body()

  override def start(): Unit =
    fork()

  // TODO FIXME: handle timeouts
  override def await(atMost: Duration): this.type =
    await

  override def await: this.type = {
    this.join()
    this
  }

  override def tryCancel(): Unit =
    tryUnfork()
}

private[concurrent] final class ForkJoinExecutionContext extends ExecutionContext {
  val pool = new ForkJoinPool

  @inline
  private def executeForkJoinTask(task: RecursiveAction) {
    if (Thread.currentThread.isInstanceOf[ForkJoinWorkerThread])
      task.fork()
    else
      pool execute task
  }

  def dispatchTask(body: () => Unit) {
    val task = new RecursiveAction { def compute() { body() } }
    executeForkJoinTask(task)
  }

  // type of body is () => T to avoid creating another closure
  def newPromise[T](body: () => T): Promise[T] =
    new ForkJoinPromise(this, body, 0L)
}

/**
 * Implements a blocking execution context
 */
/*
private[concurrent] class BlockingExecutionContext extends ExecutionContext {
  val pool = makeCachedThreadPool // TODO FIXME: need to merge thread pool factory methods from Heather's parcolls repo

  def execute(task: Runnable) {
    val p = newPromise(task.run())
    p.start()
    pool execute p
  }

  // TODO FIXME: implement
  def newPromise[T](body: => T): Promise[T] = {
    throw new Exception("not yet implemented")
  }
}
*/

object ExecutionContext {

  lazy val forNonBlocking = new ForkJoinExecutionContext

  //lazy val forBlocking = new BlockingExecutionContext

}
