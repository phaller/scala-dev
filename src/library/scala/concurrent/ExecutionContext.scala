package scala.concurrent

import java.util.concurrent.{Executors, Future => JFuture}
import scala.concurrent.forkjoin._
import scala.util.{Duration, Timeout}

trait ExecutionContext {

  def execute(task: Runnable): Unit
  def dispatchTask(task: () => Unit): Unit
  def newPromise[T](body: => T): Promise[T]
  def newPromise[T](timeout: Timeout, body: => T): Promise[T] =
    newPromise(body) // TODO add timeout

}

/* The challenge is to make ForkJoinPromise inherit from RecursiveAction to avoid
 * an object allocation per promise.
 */
private[concurrent] class ForkJoinPromise[T](context: ForkJoinExecutionContext, body: () => T, timeout: Long) extends DefaultPromise[T](timeout)(context) with Promise[T] {

  val forkJoinTask = new RecursiveAction {
    def compute() { body() }
  }

  override def start() {
    if (Thread.currentThread.isInstanceOf[ForkJoinWorkerThread])
      forkJoinTask.fork()
    else
      context.pool execute forkJoinTask
  }

  // TODO FIXME: properly handle timeouts
  override def await(atMost: Duration): this.type =
    await

  override def await: this.type = {
    forkJoinTask.join()
    this
  }

  // TODO FIXME: add def tryCancel
}

private[concurrent] class ForkJoinExecutionContext extends ExecutionContext {
  val pool = new ForkJoinPool

  private def executeForkJoinTask(task: RecursiveAction) {
    if (Thread.currentThread.isInstanceOf[ForkJoinWorkerThread])
      task.fork()
    else
      pool execute task
  }

  def execute(task: Runnable) {
    val action = new RecursiveAction { def compute() { task.run() } }
    executeForkJoinTask(action)
  }

  def dispatchTask(body: () => Unit) {
    val task = new RecursiveAction { def compute() { body() } }
    executeForkJoinTask(task)
  }

  def newPromise[T](body: => T): Promise[T] =
    new ForkJoinPromise(this, () => body, 0L) // TODO: problematic: creates closure
}

object ExecutionContext {

  
  private var _global: Option[ExecutionContext] = None

  def global: ExecutionContext = {
    if (_global.isEmpty) {
      val context = new ForkJoinExecutionContext
      _global = Some(context)
    }
    _global.get
  }

  /* a Java ExecutorService which collects executed tasks in a HashMap, so that
     they can be cancelled
   */
/*
  def defaultWithCancellation: CancellationExecutionContext = {
    new CancellationExecutionContext {
      val executor = Executors.newCachedThreadPool()
      
      lazy val token = new CancellationToken
      
      var futures = Map[CancellationToken, List[JFuture[_]]]()

      def dispatchTask(body: () => Unit) {
        executor execute (new Runnable { def run() = body() })
      }

      def newPromise[T](body: => T): Promise[T] =
        

      def execute(task: Runnable) {
        executor execute task
      }

      def executeCancellable(task: Runnable, cancellationToken: CancellationToken = token) = synchronized {
        val future = executor submit task

        futures.get(cancellationToken) match {
          case None =>
            futures += (cancellationToken -> List(future))
          case Some(fs) =>
            futures += (cancellationToken -> (future :: fs))
        }
      }

      def cancel() = synchronized {
        futures.get(token) match {
          case None =>
            // do nothing
          case Some(fs) =>
            for (future <- fs) {
              future.cancel(false)
            }
        }
      }
    }
  }
*/
}
