package scala.concurrent

import java.util.concurrent.{Executors, Future => JFuture}

trait ExecutionContext {

  def execute(task: Runnable): Unit

}

object ExecutionContext {

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
