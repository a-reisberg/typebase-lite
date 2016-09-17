package com.shalloui.tblite.mapper

import java.util.{ArrayList => JList, HashMap => JMap, HashSet => JSet}

import shapeless._
import shapeless.labelled.FieldType


/**
  * Type class converting from Java'k (possibly nested) [[JMap[String, Any]]], [[JList]] and
  * [[JSet]] to Scala's (possibly nested) sealed trait hierarchy, [[List]] and [[Set]].
  * Optional fields are supported.
  *
  * See [[FromGen]] for the other direction. See tblite object for usage example.
  *
  * Created by a.reisberg on 8/22/2016.
  */
trait ToGen[In] {
  type Out <: AnyRef

  def apply(in: In, typeHint: Boolean = defaultTypeHint): Out
}

trait LowPriorityToGen3 {
  // For genuine coproducts constructed by hand with :+:, i.e. not coming from apply LabelledGeneric to a trait.
  implicit def coprodToGenNoKey[H, VM <: JHashMap, T <: Coproduct, TM <: JHashMap](implicit
                                                                                   toGenV: Lazy[ToGen.Aux[H, VM]],
                                                                                   toGenT: Lazy[ToGen.Aux[T, TM]]): ToGen.Aux[H :+: T, JHashMap]
  = new ToGen[H :+: T] {
    type Out = JHashMap

    override def apply(in: H :+: T, typeHint: Boolean = defaultTypeHint): Out =
      in.eliminate(h => {
        val res = toGenV.value(h, typeHint)
        res
      }, t =>
        toGenT.value(t, typeHint)
      )
  }
}

// For Tuple and HList to JArrayList support
trait LowPriorityToGen2 extends LowPriorityToGen3 {
  implicit val hnilToGenArr: ToGen.Aux[HNil, JArrayList] = new ToGen[HNil] {
    type Out = JArrayList

    override def apply(in: HNil, typeHint: Boolean = defaultTypeHint): Out = new JArrayList()
  }

  implicit def hlistRecToGenArr[H, T <: HList](implicit
                                               toGenH: Lazy[ToGen[H]],
                                               toGenT: Lazy[ToGen.Aux[T, JArrayList]]): ToGen.Aux[H :: T, JArrayList] =
    new ToGen[H :: T] {
      type Out = JArrayList

      override def apply(in: H :: T, typeHint: Boolean = defaultTypeHint): JArrayList = {
        val al = new JArrayList
        al.add(toGenH.value(in.head))
        al.addAll(toGenT.value(in.tail))

        al
      }
    }

  implicit def prodToGenArr[P, R <: HList](implicit
                                           genP: Generic.Aux[P, R],
                                           toGenR: Lazy[ToGen.Aux[R, JArrayList]]): ToGen.Aux[P, JArrayList] =
    new ToGen[P] {
      type Out = JArrayList

      override def apply(in: P, typeHint: Boolean = defaultTypeHint): Out = {
        val res = toGenR.value(genP.to(in), typeHint)
        res
      }
    }
}

trait LowPriorityToGen extends LowPriorityToGen2 {
  implicit def prodToGen[P, R <: HList](implicit
                                        genP: LabelledGeneric.Aux[P, R],
                                        pTypeable: Typeable[P],
                                        toGenR: Lazy[ToGen.Aux[R, JHashMap]]): ToGen.Aux[P, JHashMap] =
    new ToGen[P] {
      type Out = JHashMap

      override def apply(in: P, typeHint: Boolean = defaultTypeHint): Out = {
        val res = toGenR.value(genP.to(in), typeHint)
        if (typeHint) res.put(typeHintKey, pTypeable.describe.dropWhile(_ == '.').takeWhile(_ != '.'))
        res
      }
    }

  implicit def sealedTraitToGen[T, R <: Coproduct](implicit
                                                   genT: LabelledGeneric.Aux[T, R],
                                                   toGenR: Lazy[ToGen.Aux[R, JHashMap]]): ToGen.Aux[T, JHashMap] =
    new ToGen[T] {
      type Out = JHashMap

      override def apply(in: T, typeHint: Boolean = defaultTypeHint): Out =
        toGenR.value.apply(genT.to(in), typeHint)
    }

  implicit def hlistRecToGen[K <: Symbol, V, T <: HList, TM <: JHashMap](implicit
                                                                         kWitness: Witness.Aux[K],
                                                                         toGenV: Lazy[ToGen[V]],
                                                                         toGenT: Lazy[ToGen.Aux[T, TM]]): ToGen.Aux[FieldType[K, V] :: T, JHashMap] =
    new ToGen[FieldType[K, V] :: T] {
      type Out = JHashMap

      override def apply(in: FieldType[K, V] :: T, typeHint: Boolean = defaultTypeHint): Out = {
        val res = toGenT.value(in.tail, typeHint)
        res.put(kWitness.value.name, toGenV.value(in.head, typeHint))

        res
      }
    }
}

object ToGen extends LowPriorityToGen {
  type Aux[In, Out0] = ToGen[In] {type Out = Out0}

  def makeBaseToGen[In, Out0 <: AnyRef](implicit autoBoxing: In => Out0): Aux[In, Out0] = new ToGen[In] {
    type Out = Out0

    override def apply(in: In, typeHint: Boolean = defaultTypeHint): Out0 =
      autoBoxing(in)
  }

  implicit val doubleToGen: Aux[Double, java.lang.Double] = makeBaseToGen[Double, java.lang.Double]

  implicit val intToGen: Aux[Int, java.lang.Integer] = makeBaseToGen[Int, java.lang.Integer]

  implicit val stringToGen: Aux[String, String] = makeBaseToGen[String, String]

  implicit val booleanToGen: Aux[Boolean, java.lang.Boolean] = makeBaseToGen[Boolean, java.lang.Boolean]

  implicit val hnilToGen: Aux[HNil, JHashMap] = new ToGen[HNil] {
    type Out = JHashMap

    override def apply(in: HNil, typeHint: Boolean = defaultTypeHint): Out = new JHashMap()
  }

  implicit val cnilToGen: Aux[CNil, JHashMap] = new ToGen[CNil] {
    type Out = JHashMap

    override def apply(in: CNil, typeHint: Boolean = defaultTypeHint): Out = new JHashMap()
  }

  implicit def listToGen[In](implicit toGenIn: Lazy[ToGen[In]]): Aux[List[In], JArrayList] =
    new ToGen[List[In]] {
      type Out = JArrayList

      override def apply(s: List[In], typeHint: Boolean = defaultTypeHint): Out = {
        val resList = new Out
        for (e <- s)
          resList.add(toGenIn.value.apply(e, typeHint))
        resList
      }
    }

  implicit def setToGen[In](implicit toGenIn: Lazy[ToGen[In]]): Aux[Set[In], JHashSet] =
    new ToGen[Set[In]] {
      type Out = JHashSet

      override def apply(s: Set[In], typeHint: Boolean = defaultTypeHint): Out = {
        val resSet = new Out
        for (e <- s)
          resSet.add(toGenIn.value.apply(e, typeHint))
        resSet
      }
    }

  implicit def mapToGen[InK, InV](implicit
                                  toGenInK: Lazy[ToGen[InK]],
                                  toGenInV: Lazy[ToGen[InV]]): Aux[Map[InK, InV], JHashMapGen] =
    new ToGen[Map[InK, InV]] {
      type Out = JHashMapGen

      override def apply(in: Map[InK, InV], typeHint: Boolean): Out = {
        val res = new Out

        for ((k, v) <- in)
          res.put(toGenInK.value(k, typeHint), toGenInV.value(v, typeHint))

        res
      }
    }

  implicit def hlistRecWOptionToGen[K <: Symbol, V, T <: HList, TM <: JHashMap](implicit
                                                                                kWitness: Witness.Aux[K],
                                                                                toGenV: Lazy[ToGen[V]],
                                                                                toGenT: Lazy[Aux[T, TM]]): Aux[FieldType[K, Option[V]] :: T, JHashMap] =
    new ToGen[FieldType[K, Option[V]] :: T] {
      type Out = JHashMap

      override def apply(in: FieldType[K, Option[V]] :: T, typeHint: Boolean = defaultTypeHint): Out = {
        val res = toGenT.value(in.tail, typeHint)
        for (a <- in.head.map(i => toGenV.value(i, typeHint)))
          res.put(kWitness.value.name, a)

        res
      }
    }

  implicit def coprodToGen[K <: Symbol, V, VM <: JHashMap, T <: Coproduct, TM <: JHashMap](implicit
                                                                                           toGenV: Lazy[ToGen.Aux[V, VM]],
                                                                                           toGenT: Lazy[ToGen.Aux[T, TM]]): ToGen.Aux[FieldType[K, V] :+: T, JHashMap]
  = new ToGen[FieldType[K, V] :+: T] {
    type Out = JHashMap

    override def apply(in: FieldType[K, V] :+: T, typeHint: Boolean = defaultTypeHint): Out =
      in.eliminate(h => {
        val res = toGenV.value(h, typeHint)
        res
      }, t =>
        toGenT.value(t, typeHint)
      )
  }
}
