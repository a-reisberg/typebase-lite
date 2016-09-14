package com.so.typebaselite.mapper

import shapeless._
import shapeless.labelled._

/**
  * Created by a.reisberg on 9/10/2016.
  */
trait Extract[From, To] {
  self =>
  def apply(t: From): Option[To]

  def map[To2](f: To => To2): Extract[From, To2] =
    new Extract[From, To2] {
      override def apply(t: From): Option[To2] =
        self.apply(t).map(f)
    }

  def contraMap[From2](f: From2 => From): Extract[From2, To] =
    new Extract[From2, To] {
      override def apply(t: From2): Option[To] =
        self.apply(f(t))
    }
}

trait LowPriorityExtract2 {
  implicit def extractFieldFromHLRecFrom[H1, T1 <: HList, K2 <: Symbol, V2](implicit
                                                                            t1tok2v2: Lazy[Extract[T1, FieldType[K2, V2]]]):
  Extract[H1 :: T1, FieldType[K2, V2]] = new Extract[H1 :: T1, FieldType[K2, V2]] {
    override def apply(t: H1 :: T1): Option[FieldType[K2, V2]] =
      t1tok2v2.value(t.tail)
  }
}

trait LowPriorityExtract extends LowPriorityExtract2 {
  implicit def extractFromHNil[T]: Extract[HNil, T] = new Extract[HNil, T] {
    override def apply(t: HNil): Option[T] = None
  }

  implicit def extractViaDowncast[T, T2 <: T](implicit t2Typeable: Typeable[T2]): Extract[T, T2] =
    new Extract[T, T2] {
      override def apply(t: T): Option[T2] =
        t2Typeable.cast(t)
    }

  implicit def extractMatchedFieldFromHL[K <: Symbol, V1, T1 <: HList, V2](implicit v1tov2: Lazy[Extract[V1, V2]]):
  Extract[FieldType[K, V1] :: T1, FieldType[K, V2]] = new Extract[FieldType[K, V1] :: T1, FieldType[K, V2]] {
    override def apply(t: FieldType[K, V1] :: T1): Option[FieldType[K, V2]] =
      v1tov2.value(t.head).map(field[K](_))
  }

  implicit def extractHLfromHLRecTo[HL1 <: HList, H2, T2 <: HList](implicit
                                                                   hl1toh2: Lazy[Extract[HL1, H2]],
                                                                   hl1tot2: Lazy[Extract[HL1, T2]]): Extract[HL1, H2 :: T2] =
    new Extract[HL1, H2 :: T2] {
      override def apply(t: HL1): Option[H2 :: T2] =
        for {
          h2 <- hl1toh2.value(t)
          t2 <- hl1tot2.value(t)
        } yield h2 :: t2
    }
}

object Extract extends LowPriorityExtract {
  implicit def extractFromOption[T]: Extract[Option[T], T] =
    new Extract[Option[T], T] {
      override def apply(t: Option[T]): Option[T] = t
    }

  implicit def extractOption[T]: Extract[T, Option[T]] =
    new Extract[T, Option[T]] {
      override def apply(t: T): Option[Option[T]] = Some(Some(t))
    }

  implicit def extractHNil[T]: Extract[T, HNil] = new Extract[T, HNil] {
    override def apply(t: T): Option[HNil] = Some(HNil)
  }

  implicit def extractViaUpcast[T, T2 >: T]: Extract[T, T2] = new Extract[T, T2] {
    override def apply(t: T): Option[T2] =
      Some(t)
  }

  implicit def extractPfromP[P1 <: Product, P2 <: Product, HL1 <: HList, HL2 <: HList](implicit
                                                                                       p1gen: LabelledGeneric.Aux[P1, HL1],
                                                                                       p2gen: LabelledGeneric.Aux[P2, HL2],
                                                                                       hl1tohl2: Lazy[Extract[HL1, HL2]]): Extract[P1, P2] =
    new Extract[P1, P2] {
      override def apply(t: P1): Option[P2] =
        hl1tohl2.value(p1gen.to(t)).map(p2gen.from)
    }

  implicit def extractHLfromHLRecToWDefault[HL1 <: HList, H2, T2 <: HList](implicit
                                                                           hl1toh2: Lazy[Extract[HL1, H2]],
                                                                           hl1tot2: Lazy[Extract[HL1, T2]],
                                                                           h2Default: Default[H2]): Extract[HL1, H2 :: T2] =
    new Extract[HL1, H2 :: T2] {
      override def apply(t: HL1): Option[H2 :: T2] =
        for {
          h2 <- Some(hl1toh2.value(t).getOrElse(h2Default.get()))
          t2 <- hl1tot2.value(t)
        } yield h2 :: t2
    }
}