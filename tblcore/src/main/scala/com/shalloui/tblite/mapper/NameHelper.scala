package com.shalloui.tblite.mapper

import shapeless._

/**
  * Typeclass to extract the name of the class.
  * More specifically, when [[A]] is a sealed trait,
  * the apply method will returns a set of names of
  * the subclasses. When [[A]] is a concrete class,
  * the apply method will return the a singleton set
  * containing the name of the class [[A]].
  *
  * Created by a.reisberg on 8/30/2016.
  */
trait NameHelper[A] {
  def apply(): Set[String]
}

trait NameHelperLowPriority {
  implicit def prodNameHelper[A, C <: Product](implicit
                                               gen: LabelledGeneric.Aux[A, C],
                                               aTypeable: Typeable[A]): NameHelper[A] =
    new NameHelper[A] {
      override def apply(): Set[String] =
        Set(aTypeable.describe.dropWhile(_ == '.').takeWhile(_ != '.'))
    }
}

object NameHelper extends NameHelperLowPriority {
  implicit val cnilNameHelper: NameHelper[CNil] =
    new NameHelper[CNil] {
      override def apply(): Set[String] = Set()
    }

  implicit def sealedTraitNameHelper[A, C <: Coproduct, K <: HList](implicit
                                                                    gen: LabelledGeneric.Aux[A, C],
                                                                    keys: ops.union.Keys.Aux[C, K],
                                                                    toSet: ops.hlist.ToTraversable.Aux[K, Set, Symbol]): NameHelper[A] =
    new NameHelper[A] {
      override def apply(): Set[String] = {
        toSet(keys()).map(_.name)
      }
    }

  implicit def coprodNameHelper[H, T <: Coproduct](implicit
                                                   hNameHelper: Lazy[NameHelper[H]],
                                                   tNameHelpper: Lazy[NameHelper[T]]): NameHelper[H :+: T] =
    new NameHelper[H :+: T] {
      override def apply(): Set[String] =
        hNameHelper.value.apply() ++ tNameHelpper.value.apply()
    }
}
