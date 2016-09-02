package com.so.typebaselite

import java.util.{ArrayList => JList, HashMap => JMap, HashSet => JSet}

import shapeless._

/**
  * Conjure up instances of [[ToGen]], [[FromGen]] and [[FromAny]]. This object also
  * provides implicit [[shapeless.Typeable]] instances needed for Java Set,
  * List and Map via [[JavaTypeabilityHelper]].
  *
  * Note that [[fromAny]] is more flexible than [[fromGen]] as it accepts [[AnyRef]].
  * The trade off is that [[fromGen]] has more type information. For example, the compiler
  * will complain if we want a case class out of something that isn't a [[Map[String, Any]]
  *
  * Limitation:
  * - The sealed trait has to be defined in a different scope from
  * the code that uses this function.
  * - typeHint = false should only be used on classes, instead of sealed trait hierarchy.
  * [[FromGen]]/[[FromAny]] will not behave correctly.
  *
  * {{{
  * import mapper._
  *
  * sealed trait MT
  * case object MO extends MT
  * case class MC(i: Int) extends MT
  * case class MM(m: List[MT]) extends MT
  *
  *
  * object MyTest extends App {
  *   val mtToGen = toGen[List[MT]]
  *   val mtFromGen = fromAny[List[MT]]
  *
  *   val mm = List(MM(List(MO, MC(100))), MO, MC(200))
  *
  *   val mmGen = mtToGen(mm, typeHint = true)
  *   val mmBack = mtFromGen(mmGen, typeHint = true)
  *   println(mmGen)
  *   println(mmBack)
  * }
  * }}}
  *
  * Created by a.reisberg on 8/23/2016.
  */
package object mapper extends JavaTypeabilityHelper {

  /**
    * Semantically the same as [[FromGen]] except that the input type
    * can be [[AnyRef]]. The reason why there'k no sub/super-type
    * relation with [[FromGen]] is so that the implicit [[ultimateDerivation]]
    * is only called the first time around. This is to ensure that casting
    * doesn't happen more than necessary, because the inner recursions of
    * [[FromGen]] already do all the casting. It'k only the first call in
    * [[FromGen]] that doesn't have casting already.
    *
    * Note that [[fromAny]] is more flexible than [[fromGen]] as it accepts [[AnyRef]].
    * The trade off is that [[fromGen]] has more type information. For example, the compiler
    * will complain if we want a case class out of something that isn't a [[Map[String, Any]]
    *
    * @tparam Out The type of the output
    */
  trait FromAny[Out] {
    def apply(in: AnyRef, typeHint: Boolean = defaultTypeHint): Option[Out]
  }

  // Type aliases used as output of ToGen
  type JHashMap = JMap[String, AnyRef]
  type JHashMapGen = JMap[AnyRef, AnyRef]
  type JArrayList = JList[AnyRef]
  type JHashSet = JSet[AnyRef]

  // Type aliases used as input for FromGen
  type JMapInterface = java.util.Map[String, AnyRef]
  type JMapGenInterface = java.util.Map[AnyRef, AnyRef]
  type JListInterface = java.util.List[AnyRef]
  type JSetInterface = java.util.Set[AnyRef]

  val defaultTypeHint = true
  val typeHintKey = "type"

  // Enable fromGen to accept AnyRef.
  implicit def ultimateDerivation[Out, In](implicit
                                           ev: FromGen.Aux[Out, In],
                                           inTypeable: Typeable[In]): FromAny[Out] =
  new FromAny[Out] {
    type In = AnyRef

    override def apply(in: AnyRef, typeHint: Boolean = defaultTypeHint): Option[Out] = {
      for {
        casted <- inTypeable.cast(in)
        res <- ev(casted, typeHint)
      } yield res
    }
  }

  def toGen[In](implicit ev: ToGen[In]): ToGen.Aux[In, ev.Out] = ev

  def toGenAux[In, Out](implicit ev: ToGen.Aux[In, Out]): ToGen.Aux[In, Out] = ev

  def fromAny[Out](implicit ev: FromAny[Out]): FromAny[Out] = ev

  def fromGen[Out](implicit ev: FromGen[Out]): FromGen.Aux[Out, ev.In] = ev

  def fromGenAux[Out, In](implicit ev: FromGen.Aux[Out, In]): FromGen.Aux[Out, In] = ev
}
