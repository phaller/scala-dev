package scala.actors

trait RichActor extends InternalActor {
  type Receive = PartialFunction[Any, Unit]
  
  /**
   * Migration notes:
   *   this method replaces receiveWithin, receive and react methods from Scala Actors. 
   *     - if we invoke the self  
   */
  def handle: Receive
  	 
  
}