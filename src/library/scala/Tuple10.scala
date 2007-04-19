
/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: genprod.scala 10751 2007-04-19 15:45:58Z michelou $

// generated by genprod on Thu Apr 19 18:52:00 CEST 2007

package scala

/** Tuple10 is the canonical representation of a @see Product10 */
case class Tuple10[+T1, +T2, +T3, +T4, +T5, +T6, +T7, +T8, +T9, +T10](_1:T1, _2:T2, _3:T3, _4:T4, _5:T5, _6:T6, _7:T7, _8:T8, _9:T9, _10:T10) 
  extends Product10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10]  {

   override def toString() = {
     val sb = new compat.StringBuilder
     sb.append('(').append(_1).append(',').append(_2).append(',').append(_3).append(',').append(_4).append(',').append(_5).append(',').append(_6).append(',').append(_7).append(',').append(_8).append(',').append(_9).append(',').append(_10).append(')')
     sb.toString
   }
}
