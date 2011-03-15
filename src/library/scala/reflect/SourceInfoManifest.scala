package scala.reflect

trait SourceInfoManifest[T] extends Manifest[T] { self =>
  def line: Int

  def bindings: List[(String, Int)]

  def assignedVariable: Option[String] =
    if (bindings(0)._1 == null) None
    else Some(bindings(0)._1)
  
  def update(lineInfo: Int) = new SourceInfoManifest[T] {
    def erasure = self.erasure
    override def line = lineInfo // todo: update bindings instead
    def bindings = self.bindings
  }
}

object SourceInfoManifest {
  /** Manifest for the class type `clazz[args]', where `clazz' is
    * a top-level or static class.
    * @note This no-prefix, no-arguments case is separate because we
    *       it's called from ScalaRunTime.boxArray itself. If we
    *       pass varargs as arrays into this, we get an infinitely recursive call
    *       to boxArray. (Besides, having a separate case is more efficient)
    */
  def classType[T](sourceInfo: List[(String, Int)], clazz: Predef.Class[_]): SourceInfoManifest[T] =
    new ClassTypeManifest[T](sourceInfo, None, clazz, Nil)

  /** Manifest for the class type `clazz', where `clazz' is
    * a top-level or static class and args are its type arguments. */
  def classType[T](sourceInfo: List[(String, Int)], clazz: Predef.Class[T], arg1: Manifest[_], args: Manifest[_]*): SourceInfoManifest[T] =
    new ClassTypeManifest[T](sourceInfo, None, clazz, arg1 :: args.toList)

  /** Manifest for the class type `clazz[args]', where `clazz' is
    * a class with non-package prefix type `prefix` and type arguments `args`.
    */
  def classType[T](sourceInfo: List[(String, Int)], prefix: Manifest[_], clazz: Predef.Class[_], args: Manifest[_]*): SourceInfoManifest[T] =
    new ClassTypeManifest[T](sourceInfo, Some(prefix), clazz, args.toList)

  /** Manifest for the class type `clazz[args]', where `clazz' is
    * a top-level or static class. */
  private class ClassTypeManifest[T](override val bindings: List[(String, Int)],
                                     prefix: Option[Manifest[_]], 
                                     val erasure: Predef.Class[_], 
                                     override val typeArguments: List[Manifest[_]]) extends SourceInfoManifest[T] {
    override def line = bindings(0)._2

    override def toString = 
      (if (prefix.isEmpty) "" else prefix.get.toString+"#") +
      (if (erasure.isArray) "Array" else erasure.getName) +
      argString
  }
  
}
