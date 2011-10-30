
import scala.actors.Reactor
import scala.util.continuations._

case class Ping(from: Reactor[Any])
case object Pong
case object Stop

/**
 * Ping pong example for Reactor.
 *
 * @author  Philipp Haller
 */
object Test {
  def main(args: Array[String]) {
    val pong = new PongActor
    val ping = new PingActor(100000, pong)
    ping.start
    pong.start
  }
}

class PingActor(count: Int, pong: Reactor[Any]) extends Reactor[Any] {
  var pingsLeft = count - 1

  def looping(): Unit @suspendable = {
    react {
      case Pong =>
        if (pingsLeft % 10000 == 0)
          println("Ping: pong")
        if (pingsLeft > 0) {
          pong ! Ping(this)
          pingsLeft -= 1
          reset { looping() }
        } else {
          println("Ping: stop")
          pong ! Stop
          exit()
        }
    }
  }

  def act() {
    try {
      pong ! Ping(this)
      reset {
        looping()
      }
    } catch {
      case e: Throwable if !e.isInstanceOf[scala.util.control.ControlThrowable] =>
        e.printStackTrace()
    }
  }
}

class PongActor extends Reactor[Any] {
  var pongCount = 0

  def looping(): Unit @suspendable = {
    react {
      case Ping(from) =>
        if (pongCount % 10000 == 0)
          println("Pong: ping "+pongCount)
        from ! Pong
        pongCount += 1
        reset { looping() }
      case Stop =>
        println("Pong: stop")
        exit()
    }
  }

  def act() {
    try {
      reset {
        looping()
      }
    } catch {
      case e: Throwable if !e.isInstanceOf[scala.util.control.ControlThrowable] =>
        e.printStackTrace()
    }
  }
}
