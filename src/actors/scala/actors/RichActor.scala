package scala.actors

import scala.util.continuations._

trait RichActor extends AbstractActor with InternalReactor[Any] {

  private[actors] def isActor = false

  // creation check (see ActorRef)
  private val context = ActorSystem.contextStack.get
  if (context.isEmpty && !isActor)
    throw new Exception("must use actorOf to create actor")
  else {
    if (!context.head && !isActor)
      throw new Exception("must use actorOf to create actor")
    else
      ActorSystem.contextStack.set(context.push(false))
  }

  type Receive = PartialFunction[Any, Unit @suspendable]

  // receive, process, serve, accept, onMessage, respond, response, handle, handler
  def handle: Receive

  /**
   * Receives a message from this $actor's mailbox.
   *
   * @param  handler  a partial function with message patterns and actions
   */
  protected def react(handler: PartialFunction[Any, Unit]): /*Nothing*/Unit @suspendable = {
    shift { (k: Unit => Any) => {
      synchronized { drainSendBuffer(mailbox) }
      searchMailbox(mailbox, handler andThen k, false)
      throw Actor.suspendException
    } }
  }

  /**
   * Starts this $actor. This method is idempotent.
   *
   * TODO: make private, invoke from inside actorOf
   */
  /*private[actors]*/ def start(): RichActor = synchronized {
    dostart()
    this
  }

  private[actors] override def internalAct() {
    reset {
      while (true) {
        react(new PartialFunction[Any, Unit] {
          def isDefinedAt(x: Any) =
            handle.isDefinedAt(x)
          def apply(x: Any) =
            reset { handle(x) }
        })
      }
    }
  }

  // TODO (VJ): build into ActorRef
  protected[actors] def stop(): Nothing =
    internalExit()
  
    /*
   * Following methods are copied from Actor and deprecated in order to make the transition to AKKA actors smoother.
   */
  import scala.actors.Actor._
  
  // guarded by this
  private[actors] var exitReason: AnyRef = 'normal
  
  /**
   * <p>
   *   Terminates execution of <code>self</code> with the following
   *   effect on linked actors:
   * </p>
   * <p>
   *   For each linked actor <code>a</code> with
   *   <code>trapExit</code> set to <code>true</code>, send message
   *   <code>Exit(self, reason)</code> to <code>a</code>.
   * </p>
   * <p>
   *   For each linked actor <code>a</code> with
   *   <code>trapExit</code> set to <code>false</code> (default),
   *   call <code>a.exit(reason)</code> if
   *   <code>reason != 'normal</code>.
   * </p>
   */
  @deprecated("Exists only for purposes of smooth transition to AKKA actors.", "2.10")
  protected[actors] def exit(reason: AnyRef): Nothing = {
    synchronized {
      exitReason = reason
    }
    exit()
  }

  /**
   * Terminates with exit reason <code>'normal</code>.
   */
  @deprecated("Exists only for purposes of smooth transition to AKKA actors.", "2.10")
  protected[actors] def exit(): Nothing = {
    val todo = synchronized {
      if (!links.isEmpty)
        exitLinked()
      else
        () => {}
    }
    todo()
    internalExit()
  }
  
  @deprecated("Exists only for purposes of smooth transition to AKKA actors.", "2.10") 
  protected[actors] def mailboxSize: Int =
    mailbox.size
  
      // guarded by this
  private[actors] var links: List[AbstractActor] = Nil
    
  /**
   * Links <code>self</code> to actor <code>to</code>.
   *
   * @param to the actor to link to
   * @return   the parameter actor
   */
  @deprecated("Exists only for purposes of smooth transition to AKKA actors.", "2.10")
  def link(to: AbstractActor): AbstractActor = {
    assert(Actor.self(scheduler) == this, "link called on actor different from self")
    this linkTo to
    to linkTo this
    to
  }  
  
  /**
   * Links <code>self</code> to the actor defined by <code>body</code>.
   *
   * @param body the body of the actor to link to
   * @return     the parameter actor
   */
  @deprecated("Exists only for purposes of smooth transition to AKKA actors.", "2.10")
  def link(body: => Unit): Actor = {
    assert(Actor.self(scheduler) == this, "link called on actor different from self")
    val a = new Actor {
      def act() = body
      override final val scheduler: IScheduler = RichActor.this.scheduler
    }
    link(a)
    a.start()
    a
  }
  
  private[actors] def linkTo(to: AbstractActor) = synchronized {
    links = to :: links
  }

  /**
   * Unlinks <code>self</code> from actor <code>from</code>.
   */
  @deprecated("Exists only for purposes of smooth transition to AKKA actors.", "2.10")
  def unlink(from: AbstractActor) {
    assert(Actor.self(scheduler) == this, "unlink called on actor different from self")
    this unlinkFrom from
    from unlinkFrom this
  }

  private[actors] def unlinkFrom(from: AbstractActor) = synchronized {
    links = links.filterNot(from.==)
  }
  
  private[actors] def beforeExitLinked() = {}
  
  // Assume !links.isEmpty
  // guarded by this
  private[actors] def exitLinked(): () => Unit = { 
    beforeExitLinked()    
    // remove this from links
    val mylinks = links.filterNot(this.==)
    // unlink actors
    mylinks.foreach(unlinkFrom(_))
    // return closure that locks linked actors
    () => {
      mylinks.foreach((linked: AbstractActor) => {
        linked.synchronized {
          if (!linked.exiting) {
            linked.unlinkFrom(this)
            linked.exit(this, exitReason)
          }
        }
      })
    }
  }
  
  // Assume !links.isEmpty
  // guarded by this
  private[actors] def exitLinked(reason: AnyRef): () => Unit = {
    exitReason = reason
    exitLinked()
  }
   
}
