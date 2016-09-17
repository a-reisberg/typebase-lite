package com.shalloui

import com.shalloui.tblite.mapper.{Codec, _}
import shapeless.{HNil, Typeable, _}

import scala.language.implicitConversions

/**
  * Created by a.reisberg on 8/31/2016.
  */
package object tblite {

  case object Empty

  case class QueryDocInfo(rev: String, _conflicts: List[String])

  type Empty = Empty.type

  type Last = Empty :: HNil

  val Last: Last = Empty :: HNil

  implicit val ignoreCodec: Codec[Empty] = Codec.noHint[Empty.type]

  implicit val docInfoCodec = Codec.noHintAux[QueryDocInfo, JHashMap]

  implicit val stringCodec = Codec.aux[String, String]

  implicit def castableOps[T](t: T): CastableOps[T] = new CastableOps(t)

  final class CastableOps[T](val t: T) extends AnyVal {
    def to[U](implicit cast: Typeable[U]): Option[U] = cast.cast(t)

    def is[U](implicit cast: Typeable[U]): Boolean = cast.cast(t).isDefined
  }

}
