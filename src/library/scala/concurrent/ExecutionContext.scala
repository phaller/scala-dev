package scala.concurrent

import java.util.concurrent.{Executors, Future => JFuture}
import scala.concurrent.forkjoin._

trait ExecutionContext {

  def execute(task: Runnable): Unit
  def newPromise[T](body: => T): Promise[T]

}

private[concurrent] class ForkJoinPromise extends DefaultPromise {

  def start(): Unit = {
    
  }

  // TODO FIXME: properly handle timeouts
  override def await(atMost: Duration): this.type =
    await

  // TODO FIXME: implement
  override def await: this.type = {
    this
  }

  // TODO FIXME: add def tryCancel
}

private[concurrent] class ForkJoinExecutionContext extends ExecutionContext {
  val pool = new ForkJoinPool

  def execute(task: Runnable) {
    val p = newPromise(task.run())

    if (Thread.currentThread.isInstanceOf[ForkJoinWorkerThread])
      p.start()
    else
      pool execute p
  }

  // TODO FIXME: implement
  def newPromise[T](body: => T): Promise[T] = {
    throw new Exception("not yet implemented")
  }
}

/**
 * Implements a blocking execution context
 */
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

object ExecutionContext {

  private var _global: Option[ExecutionContext] = None

  def global: ExecutionContext = {
    if (_global.isEmpty) {
      val context = new ExecutionContext {
        def execute(task: Runnable) {
          
        }
      }
    }
  }

  /* a Java ExecutorService which collects executed tasks in a HashMap, so that
     they can be cancelled
   */
  def defaultWithCancellation: CancellationExecutionContext = {
    new CancellationExecutionContext {
      val executor = Executors.newCachedThreadPool()
      
      lazy val token = new CancellationToken
      
      var futures = Map[CancellationToken, List[JFuture[_]]]()

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

}
