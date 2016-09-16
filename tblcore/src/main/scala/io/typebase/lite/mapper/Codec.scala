package io.typebase.lite.mapper

/**
  * Typeclass for converting an object of type [[T]]
  * to and from Java maps, array, lists etc. Supports
  * sealed trait hierachies as well as optional values
  * (via Scala's Option type)
  *
  * @tparam T Type of objects we want to convert.
  *
  * Created by a.reisberg on 8/28/2016.
  */
trait Codec[T] {
  /**
    * Type of output given by encode
    */
  type Out

  /**
    * Encode an object t of type [[T]] to type [[Out]]
    * so it can be passed to Couchbase lite
    *
    * @param t object of type T to be converted
    * @return object of type [[Out]]
    */
  def encode(t: T): Out

  /**
    * Decode an object returned by Couchbase lite to
    * the desired type
    *
    * @param i Object coming from Couchbase lite
    * @return a possible value of type [[T]]
    */
  def decode(i: AnyRef): Option[T]
}

object Codec {
  type Aux[T, Out0] = Codec[T] {type Out = Out0}

  /**
    * Derive a Codec for type [[T]]
    *
    * @param encodeEv implicit parameter for the encoding part
    * @param decodeEv implicit parameter for the decoding part
    * @tparam T (Scala) type we want to convert
    * @return Codec
    */
  implicit def apply[T](implicit encodeEv: ToGen[T], decodeEv: FromAny[T]): Aux[T, encodeEv.Out] =
    aux[T, encodeEv.Out](encodeEv, decodeEv)

  /**
    * Similar to [[apply]] except that we can specify the output type
    *
    * @return Codec, where output type for encoding is specified by [[Out0]]
    */
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