package scala.concurrent

import java.util.concurrent.{Executors, Future => JFuture}

trait CancellationExecutionContext extends ExecutionContext with CancellationCapability {

  def executeCancellable(task: Runnable, cancellationToken: CancellationToken): Unit

}

trait CancellationCapability {

  def token: CancellationToken

  def cancel(): Unit

}

class CancellationToken
