package com.so.typebaselite.mapper

import shapeless._
import shapeless.labelled._
import shapeless.ops.coproduct._

import scala.language.higherKinds
import scala.util.Try

/**
  * Type class for converting Java's (possibly nested) [[JMapInterface]]], [[JListInterface]] and
  * [[JSetInterface]] to Scala's (possibly nested) sealed trait hierarchy, [[List]] and [[Set]]. Optional
  * fields are supported.
  *
  * See [[ToGen]] for the other direction. See package object for usage example.
  *
  * Created by a.reisberg on 8/22/2016.
  */
trait FromGen[Out] {
  type In

  def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[Out]
}

trait LowPriorityFromGen3 {
  // For genuine coproducts constructed by hand with :+:, i.e. not coming from apply LabelledGeneric to a trait.
  implicit def coprodToGenNoKey[H, T <: Coproduct](implicit
                                                   fromGenV: Lazy[FromGen.Aux[H, JMapInterface]],
                                                   basisT: Basis[H :+: T, T],
                                                   fromGenT: Lazy[FromGen.Aux[T, JMapInterface]]): FromGen.Aux[H :+: T, JMapInterface] =
  new FromGen[H :+: T] {
    type In = JMapInterface

    type HT = H :+: T

    override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[HT] = {
      lazy val headOption: Option[HT] =
        for (v <- fromGenV.value(in, typeHint))
          yield Coproduct[HT](v)

      lazy val tailOption: Option[HT] =
        for (t <- fromGenT.value(in, typeHint)) yield t.embed[H :+: T]

      if (headOption.isDefined) headOption
      else tailOption
    }
  }
}

// For Tuple and HList from JListInterface
trait LowPriorityFromGen2 extends LowPriorityFromGen3 {
  implicit val hnilFromGenArr: FromGen.Aux[HNil, JListInterface] = new FromGen[HNil] {
    type In = JListInterface

    override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[HNil] = Some(HNil)
  }

  implicit def hlistRecFromGenArr[H, JTH, T <: HList](implicit
                                                      fromGenH: Lazy[FromGen.Aux[H, JTH]],
                                                      jthTypeable: Typeable[JTH],
                                                      fromGenT: Lazy[FromGen.Aux[T, JListInterface]]): FromGen.Aux[H :: T, JListInterface] =
    new FromGen[H :: T] {
      type In = JListInterface

      override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[H :: T] =
        for {
          e <- Try(in.remove(0)).toOption
          castedE <- jthTypeable.cast(e)
          r <- fromGenH.value(castedE, typeHint)
          tail <- fromGenT.value(in, typeHint)
        } yield r :: tail
    }

  implicit def prodFromGenArr[P, R <: HList](implicit
                                             genP: Generic.Aux[P, R],
                                             fromGenR: Lazy[FromGen.Aux[R, JListInterface]]): FromGen.Aux[P, JListInterface] =
    new FromGen[P] {
      type In = JListInterface

      override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[P] =
        fromGenR.value(in, typeHint) map genP.from
    }
}

trait LowPriorityFromGen extends LowPriorityFromGen2 {
  implicit def prodFromGen[P, R <: HList](implicit
                                          genP: LabelledGeneric.Aux[P, R],
                                          pTypeable: Typeable[P],
                                          fromGenR: Lazy[FromGen.Aux[R, JMapInterface]]): FromGen.Aux[P, JMapInterface] =
    new FromGen[P] {
      type In = JMapInterface

      override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[P] = {
        lazy val res = fromGenR.value(in, typeHint) map genP.from

        if (typeHint) {
          in.get(typeHintKey) match {
            case str: String =>
              if (str == pTypeable.describe.dropWhile(_ == '.').takeWhile(_ != '.')) res
              else None
            case _ => None
          }
        }
        else res
      }
    }

  implicit def sealedTraitFromGen[T, R <: Coproduct](implicit
                                                     genT: LabelledGeneric.Aux[T, R],
                                                     fromGenR: Lazy[FromGen.Aux[R, JMapInterface]]): FromGen.Aux[T, JMapInterface] =
    new FromGen[T] {
      type In = JMapInterface

      override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[T] =
        fromGenR.value(in, typeHint) map genT.from
    }

  implicit def hlistRecFromGen[K <: Symbol, V, JTV, T <: HList](implicit
                                                                kWitness: Witness.Aux[K],
                                                                fromGenV: Lazy[FromGen.Aux[V, JTV]],
                                                                jtvTypeable: Typeable[JTV],
                                                                fromGenT: Lazy[FromGen.Aux[T, JMapInterface]]): FromGen.Aux[FieldType[K, V] :: T, JMapInterface] =
    new FromGen[FieldType[K, V] :: T] {
      type In = JMapInterface

      override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[FieldType[K, V] :: T] =
        for {
          e <- Option(in.get(kWitness.value.name))
          castedE <- jtvTypeable.cast(e)
          r <- fromGenV.value(castedE, typeHint)
          tail <- fromGenT.value(in, typeHint)
        } yield field[K](r) :: tail
    }
}

object FromGen extends LowPriorityFromGen {
  type Aux[Out, In0] = FromGen[Out] {type In = In0}

  def fromGen2Converter[Out, In](fromG: Aux[Out, In], typeHint: Boolean = defaultTypeHint): In => Option[Out] =
    x => fromG.apply(x, typeHint)

  def makeBaseFromGen[Out, In0](implicit autoUnboxing: In0 => Out): FromGen.Aux[Out, In0] =
    new FromGen[Out] {
      type In = In0

      override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[Out] =
        Some(autoUnboxing(in))
    }

  def build[F[_], E, EG](builder: collection.mutable.Builder[E, F[E]])
                        (it: java.util.Iterator[AnyRef], fromGenE: EG => Option[E], typeableEG: Typeable[EG]): Option[F[E]] = {
    var allValid = true
    while (it.hasNext && allValid)
      typeableEG.cast(it.next()) flatMap fromGenE match {
        case Some(curr) => builder += curr
        case None => allValid = false
      }

    if (allValid) Some(builder.result())
    else None
  }

  implicit val doubleFromGen: Aux[Double, java.lang.Double] = makeBaseFromGen[Double, java.lang.Double]

  implicit val intFromGen: Aux[Int, java.lang.Integer] = makeBaseFromGen[Int, java.lang.Integer]

  implicit val stringFromGen: Aux[String, String] = makeBaseFromGen[String, String]

  implicit val booleanFromGen: Aux[Boolean, java.lang.Boolean] = makeBaseFromGen[Boolean, java.lang.Boolean]

  implicit val hnilFromGen: Aux[HNil, JMapInterface] = new FromGen[HNil] {
    type In = JMapInterface

    override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[HNil] = Some(HNil)
  }

  implicit val cnilFromGen: Aux[CNil, JMapInterface] = new FromGen[CNil] {
    type In = JMapInterface

    override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[CNil] =
      None
  }

  implicit def listFromGen[E, EG](implicit
                                  fromGenE: Lazy[Aux[E, EG]],
                                  egTypeable: Typeable[EG]): Aux[List[E], JListInterface] =
    new FromGen[List[E]] {
      type In = JListInterface

      override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[List[E]] = {
        val builder = List.newBuilder[E]
        build(builder)(in.iterator, fromGen2Converter(fromGenE.value, typeHint), egTypeable)
      }
    }

  implicit def setFromGen[E, EG](implicit
                                 fromGenE: Lazy[Aux[E, EG]],
                                 egTypeable: Typeable[EG]): Aux[Set[E], JSetInterface] =
    new FromGen[Set[E]] {
      type In = JSetInterface

      override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[Set[E]] = {
        val builder = Set.newBuilder[E]
        build(builder)(in.iterator, fromGen2Converter(fromGenE.value, typeHint), egTypeable)
      }
    }

  implicit def mapFromGen[K, KG, V, VG](implicit
                                        fromGenK: Lazy[Aux[K, KG]],
                                        kgTypeable: Typeable[KG],
                                        fromGenV: Lazy[Aux[V, VG]],
                                        vgTypeable: Typeable[VG]): Aux[Map[K, V], JMapGenInterface] =
    new FromGen[Map[K, V]] {
      type In = JMapGenInterface

      override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[Map[K, V]] = {
        val builder = Map.newBuilder[K, V]
        val fromGenKFunc = fromGen2Converter(fromGenK.value, typeHint)
        val fromGenVFunc = fromGen2Converter(fromGenV.value, typeHint)

        val it = in.entrySet().iterator()

        var allValid = true
        while (it.hasNext && allValid) {
          val curr = it.next()

          val pairOpt = for {
            kg <- kgTypeable.cast(curr.getKey)
            k <- fromGenKFunc(kg)
            vg <- vgTypeable.cast(curr.getValue)
            v <- fromGenVFunc(vg)
          } yield (k, v)

          pairOpt match {
            case Some(pair) => builder += pair
            case None => allValid = false
          }
        }

        if (allValid) Some(builder.result())
        else None
      }
    }


  implicit def hlistRecWOptionFromGen[K <: Symbol, V, JTV, T <: HList](implicit
                                                                       kWitness: Witness.Aux[K],
                                                                       fromGenV: Lazy[Aux[V, JTV]],
                                                                       jtvTypeable: Typeable[JTV],
                                                                       fromGenT: Lazy[Aux[T, JMapInterface]]): Aux[FieldType[K, Option[V]] :: T, JMapInterface] =
    new FromGen[FieldType[K, Option[V]] :: T] {
      type In = JMapInterface

      override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[FieldType[K, Option[V]] :: T] = {
        val tailOpt = fromGenT.value(in, typeHint)

        val headOpt = for {
          e <- Option(in.get(kWitness.value.name))
          castedE <- jtvTypeable.cast(e)
          r <- fromGenV.value(castedE, typeHint)
        } yield field[K](Some(r))

        headOpt match {
          case None => tailOpt map (field[K](None) :: _)
          case Some(head) => tailOpt map (head :: _)
        }
      }
    }

  implicit def coprodToGen[K <: Symbol, V, T <: Coproduct](implicit
                                                           fromGenV: Lazy[Aux[V, JMapInterface]],
                                                           basisT: Basis[FieldType[K, V] :+: T, T],
                                                           fromGenT: Lazy[Aux[T, JMapInterface]]): Aux[FieldType[K, V] :+: T, JMapInterface] =
    new FromGen[FieldType[K, V] :+: T] {
      type In = JMapInterface

      type KVT = FieldType[K, V] :+: T

      override def apply(in: In, typeHint: Boolean = defaultTypeHint): Option[KVT] = {
        lazy val headOption: Option[KVT] =
          for (v <- fromGenV.value(in, typeHint))
            yield Coproduct[KVT](field[K](v))

        lazy val tailOption: Option[KVT] =
          for (t <- fromGenT.value(in, typeHint)) yield t.embed[FieldType[K, V] :+: T]


        if (headOption.isDefined) headOption
        else tailOption
      }
    }
}