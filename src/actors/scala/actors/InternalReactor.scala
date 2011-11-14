package scala.actors

import scala.util.continuations._

// implement startSearch, searchMailbox, makeReaction, scheduleActor, dostart, start
// basically Reactor without any public interface
private[actors] trait InternalReactor[Msg >: Null] {

  /* The $actor's mailbox. */
  private[actors] val mailbox = new MQueue[Msg]("Reactor")

  // guarded by this
  private[actors] val sendBuffer = new MQueue[Msg]("SendBuffer")

  /* Whenever this $actor executes on some thread, `waitingFor` is
   * guaranteed to be equal to `Reactor.waitingForNone`.
   *
   * In other words, whenever `waitingFor` is not equal to
   * `Reactor.waitingForNone`, this $actor is guaranteed not to execute
   * on some thread.
   *
   * If the $actor waits in a `react`, `waitingFor` holds the
   * message handler that `react` was called with.
   *
   * guarded by this
   */
  private[actors] var waitingFor: PartialFunction[Msg, Any] =
    Reactor.waitingForNone

  /**
   * This partial function is applied to exceptions that propagate out of
   * this $actor's body.
   */
  private[actors] def internalExceptionHandler: PartialFunction[Exception, Unit] =
    Map()

  protected[actors] def scheduler: IScheduler =
    Reactor.scheduler

  private[actors] def startSearch(msg: Msg, replyTo: OutputChannel[Any], handler: PartialFunction[Msg, Any]) =
    () => scheduler execute makeReaction(() => {
      val startMbox = new MQueue[Msg]("Start")
      synchronized { startMbox.append(msg, replyTo) }
      searchMailbox(startMbox, handler, true)
    })

  private[actors] final def makeReaction(fun: () => Unit): Runnable =
    makeReaction(fun, null, null)

  /* This method is supposed to be overridden. */
  private[actors] def makeReaction(fun: () => Unit, handler: PartialFunction[Msg, Any], msg: Msg): Runnable =
    new ReactorTask(this, fun, handler, msg)

  private[actors] def resumeReceiver(item: (Msg, OutputChannel[Any]), handler: PartialFunction[Msg, Any], onSameThread: Boolean) {
    if (onSameThread)
      makeReaction(null, handler, item._1).run()
    else
      scheduleActor(handler, item._1)

    /* Here, we throw a SuspendActorControl to avoid
       terminating this actor when the current ReactorTask
       is finished.

       The SuspendActorControl skips the termination code
       in ReactorTask.
     */
    throw Actor.suspendException
  }

  // guarded by this
  private[actors] def drainSendBuffer(mbox: MQueue[Msg]) {
    sendBuffer.foreachDequeue(mbox)
  }

  private[actors] def searchMailbox(startMbox: MQueue[Msg],
                                    handler: PartialFunction[Msg, Any],
                                    resumeOnSameThread: Boolean) {
    var tmpMbox = startMbox
    var done = false
    while (!done) {
      val qel = tmpMbox.extractFirst(handler)
      if (tmpMbox ne mailbox)
        tmpMbox.foreachAppend(mailbox)
      if (null eq qel) {
        synchronized {
          // in mean time new stuff might have arrived
          if (!sendBuffer.isEmpty) {
            tmpMbox = new MQueue[Msg]("Temp")
            drainSendBuffer(tmpMbox)
            // keep going
          } else {
            waitingFor = handler
            /* Here, we throw a SuspendActorControl to avoid
               terminating this actor when the current ReactorTask
               is finished.

               The SuspendActorControl skips the termination code
               in ReactorTask.
             */
            throw Actor.suspendException
          }
        }
      } else {
        resumeReceiver((qel.msg, qel.session), handler, resumeOnSameThread)
        done = true
      }
    }
  }

  /* This method is guaranteed to be executed from inside
   * an $actor's act method.
   *
   * assume handler != null
   *
   * never throws SuspendActorControl
   */
  private[actors] def scheduleActor(handler: PartialFunction[Msg, Any], msg: Msg) {
    scheduler executeFromActor makeReaction(null, handler, msg)
  }

  private[actors] def sendInternal(msg: Msg, replyTo: OutputChannel[Any]) {
    val todo = synchronized {
      if (waitingFor ne Reactor.waitingForNone) {
        val savedWaitingFor = waitingFor
        waitingFor = Reactor.waitingForNone
        startSearch(msg, replyTo, savedWaitingFor)
      } else {
        sendBuffer.append(msg, replyTo)
        () => { /* do nothing */ }
      }
    }
    todo()
  }

  // TODO: deprecate
  def !(msg: Msg) {
    sendInternal(msg, null)
  }

  private[actors] def internalAct() {}

  // guarded by this
  private[actors] def dostart() {
    scheduler newActor this
    scheduler execute makeReaction(() => internalAct(), null, null)
  }

  /* This closure is used to implement control-flow operations
   * built on top of `seq`. Note that the only invocation of
   * `kill` is supposed to be inside `ReactorTask.run`.
   */
  @volatile
  private[actors] var kill: () => Unit =
    () => { internalExit() }

  private[actors] def internalExit(): Nothing = {
    terminated()
    throw Actor.suspendException
  }

  private[actors] def terminated() {
    scheduler.terminated(this)
  }

}
