import scala.actors.RichActor
import scala.util.continuations._

object Test {

  def main(args: Array[String]) {

    val rich = new RichActor {
      def handle = {
        case "hello" =>
          println("received hello")
          react {
            case "next" =>
          }
          println("done")
          exit()
      }
    }

    rich.start()

    rich ! "hello"
    rich ! "next"
  }

}
