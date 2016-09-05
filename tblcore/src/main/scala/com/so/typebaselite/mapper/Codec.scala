package com.so.typebaselite.mapper

/**
  * Created by a.reisberg on 8/28/2016.
  */
trait Codec[T] {
  type Out

  def encode(t: T): Out

  def decode(i: AnyRef): Option[T]
}

object Codec {
  type Aux[T, Out0] = Codec[T] {type Out = Out0}

  implicit def apply[T](implicit encodeEv: ToGen[T], decodeEv: FromAny[T]): Aux[T, encodeEv.Out] =
    aux[T, encodeEv.Out](encodeEv, decodeEv)

  def aux[T, Out0](implicit encodeEv: ToGen.Aux[T, Out0], decodeEv: FromAny[T]): Aux[T, Out0] =
    new Codec[T] {
      type Out = Out0

      override def encode(t: T): Out = encodeEv(t)

      override def decode(i: AnyRef): Option[T] = decodeEv(i)
    }

  def noHintAux[T, Out0](implicit encodeEv: ToGen.Aux[T, Out0], decodeEv: FromAny[T]): Aux[T, Out0] =
    new Codec[T] {
      type Out = Out0

      override def encode(t: T): Out = encodeEv(t, typeHint = false)

      override def decode(i: AnyRef): Option[T] = decodeEv(i, typeHint = false)
    }

  def noHint[T](implicit encodeEv: ToGen[T], decodeEv: FromAny[T]): Aux[T, encodeEv.Out] =
    noHintAux[T, encodeEv.Out](encodeEv, decodeEv)
}