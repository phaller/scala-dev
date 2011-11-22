/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2005-2011, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */


package scala.actors

import scala.actors.scheduler.{DelegatingScheduler, ExecutorScheduler,
                               ForkJoinScheduler, ThreadPoolConfig}
import scala.util.continuations._
import java.util.concurrent.{ThreadPoolExecutor, TimeUnit, LinkedBlockingQueue}

private[actors] object Reactor {

  val scheduler = new DelegatingScheduler {
    def makeNewScheduler: IScheduler = {
      val sched = if (!ThreadPoolConfig.useForkJoin) {
        // default is non-daemon
        val workQueue = new LinkedBlockingQueue[Runnable]
        ExecutorScheduler(
          new ThreadPoolExecutor(ThreadPoolConfig.corePoolSize,
                                 ThreadPoolConfig.maxPoolSize,
                                 60000L,
                                 TimeUnit.MILLISECONDS,
                                 workQueue,
                                 new ThreadPoolExecutor.CallerRunsPolicy))
      } else {
        // default is non-daemon, non-fair
        val s = new ForkJoinScheduler(ThreadPoolConfig.corePoolSize, ThreadPoolConfig.maxPoolSize, false, false)
        s.start()
        s
      }
      Debug.info(this+": starting new "+sched+" ["+sched.getClass+"]")
      sched
    }
  }

  val waitingForNone = new PartialFunction[Any, Unit] {
    def isDefinedAt(x: Any) = false
    def apply(x: Any) {}
  }

}

/**
 * Super trait of all actor traits.
 *
 * @author Philipp Haller
 *
 * @define actor reactor
 */
@deprecated("Extend RichActor instead. Actor will be removed from scala library and replaced with AKKA actor implementation.", "2.10")
trait Reactor[Msg >: Null] extends OutputChannel[Msg] with Combinators with InternalReactor[Msg] {

  // guarded by this
  private[actors] var _state: Actor.State.Value = Actor.State.New

  /**
   * The $actor's behavior is specified by implementing this method.
   */
  def act(): Unit

  private[actors] override def internalAct(): Unit =
    act()

  /**
   * This partial function is applied to exceptions that propagate out of
   * this $actor's body.
   */
  protected[actors] def exceptionHandler: PartialFunction[Exception, Unit] =
    Map()

  private[actors] override def internalExceptionHandler: PartialFunction[Exception, Unit] =
    exceptionHandler

  protected[actors] def mailboxSize: Int =
    mailbox.size

  /**
   * Receives a message from this $actor's mailbox.
   *
   * @param  handler  a partial function with message patterns and actions
   */
  protected def react(handler: PartialFunction[Msg, Unit]): /*Nothing*/Unit @suspendable = {
    shift { (k: Unit => Any) => {
      synchronized { drainSendBuffer(mailbox) }
      searchMailbox(mailbox, handler andThen k, false)
      throw Actor.suspendException
    } }
  }

  def send(msg: Msg, replyTo: OutputChannel[Any]) =
    sendInternal(msg, replyTo)

  def forward(msg: Msg) {
    sendInternal(msg, null)
  }

  def receiver: Actor = this.asInstanceOf[Actor]

  // guarded by this
  private[actors] override def dostart() {
    _state = Actor.State.Runnable
    super.dostart()
  }

  /**
   * Starts this $actor. This method is idempotent.
   */
  def start(): Reactor[Msg] = synchronized {
    if (_state == Actor.State.New)
      dostart()
    this
  }

  /**
   * Restarts this $actor.
   *
   * @throws java.lang.IllegalStateException  if the $actor is not in state `Actor.State.Terminated`
   */
  def restart(): Unit = synchronized {
    if (_state == Actor.State.Terminated)
      dostart()
    else
      throw new IllegalStateException("restart only in state "+Actor.State.Terminated)
  }

  /** Returns the execution state of this $actor.
   *  
   *  @return the execution state
   */
  def getState: Actor.State.Value = synchronized {
    if (waitingFor ne Reactor.waitingForNone)
      Actor.State.Suspended
    else
      _state
  }

  implicit def mkBody[A](body: => A) = new Actor.Body[A] {
    def andThen[B](other: => B): Unit = Reactor.this.seq(body, other)
  }

  private[actors] def seq[a, b](first: => a, next: => b): Unit = {
    val killNext = this.kill
    this.kill = () => {
      this.kill = killNext

      // to avoid stack overflow:
      // instead of directly executing `next`,
      // schedule as continuation
      scheduleActor({ case _ => next }, null)
      throw Actor.suspendException
    }
    first
    throw new KillActorControl
  }

  protected[actors] def exit(): Nothing =
    internalExit()

  private[actors] override def terminated() {
    synchronized {
      _state = Actor.State.Terminated
      // reset waitingFor, otherwise getState returns Suspended
      waitingFor = Reactor.waitingForNone
    }
    super.terminated()
  }

}
