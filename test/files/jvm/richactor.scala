import scala.actors.{ActorSystem, RichActor}
import ActorSystem.actorOf
import scala.util.continuations._

object Test {

  def main(args: Array[String]) {

    val rich = actorOf(new RichActor {
      try {
        val another = new RichActor { def handle = { case any => } }
      } catch {
        case e: Exception =>
          println(e.getMessage)
      }
      val another2 = actorOf(new RichActor { def handle = { case any => } })
      def handle = {
        case "hello" =>
          println("received hello")
          react {
            case "next" =>
          }
          println("done")
          exit()
      }
    })

    rich.start()

    rich ! "hello"
    rich ! "next"
  }

}
