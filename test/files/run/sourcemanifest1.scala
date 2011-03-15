
/*
class SourceInfoManifest[T](m: Manifest[T]) extends Manifest[T] {

  def line: Int
  
}
*/

import reflect.SourceInfoManifest

object Test {

/* The following doesn't work, since the newly created manifest would always
 * have the same source position, namely, the position of the invocation
 * of the withNewManifest method
  implicit def sourceInfoManifest[T]: SourceInfoManifest[T] = {
    def withNewManifest()(implicit m: Manifest[T]) {
      // here we know that m has been generated at the current source location
    }
    withNewManifest()
  }
*/

  def printInfo[T](m: SourceInfoManifest[T]) {
    println("line: "+m.line)
    println("binding: "+m.bindings(0)._1)
  }
  
  def inspect[T](x: T)(implicit m: SourceInfoManifest[T]): Int = {
    def withManifest()(implicit mm: SourceInfoManifest[T]) {
      printInfo(mm)
    }
    printInfo(m)
    withManifest()
    0
  }

  /* - by-name passing of manifests?
   */
  def main(args: Array[String]) {
    val l = List(1, 2, 3)
    /* what source info do we need at point of invocation?
       - source location
       - context info: valdefs
         for each context: name, location (invocation: method name, valdef: var name)
         
    */
    val x = inspect(l)
    val y = {
      val z = 4*7
      inspect(l)
    }
    //inspect(l)
  }

}
