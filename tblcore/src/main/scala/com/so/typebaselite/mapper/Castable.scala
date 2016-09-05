package com.so.typebaselite.mapper

import java.util.{List => JList, Map => JMap, Set => JSet}

import shapeless._

import scala.util.Try

/**
  * Castable[T] is essentially Typeable[T],
  * with added support for some Java types (for eg. [[JHashMap]] etc.)
  * needed in [[FromGen]].
  *
  * @tparam T Type that can be casted
  *
  * Created by a.reisberg on 9/4/2016.
  */
trait Castable[T] extends Typeable[T]

trait LowPriorityCastable {
  implicit def typeableImpliesCastable[T](implicit tTypeable: Typeable[T]): Castable[T] =
    new Castable[T] {
      override def describe: String = tTypeable.describe

      override def cast(t: Any): Option[T] = tTypeable.cast(t)
    }
}

object Castable extends LowPriorityCastable {
  implicit val anyRefCastable: Castable[AnyRef] = new Castable[AnyRef] {
    def cast(t: Any): Option[AnyRef] =
      Try(t.asInstanceOf[AnyRef]).toOption

    def describe: String = "AnyRef"
  }

  implicit def genJMapCastable[K, V](implicit
                                     kCastable: Castable[K],
                                     vCastable: Castable[V]): Castable[JMap[K, V]] =
    new Castable[JMap[K, V]] {
      def cast(t: Any): Option[JMap[K, V]] =
        if (t == null) None
        else if (t.isInstanceOf[JMap[_, _]]) {
          val m = t.asInstanceOf[JMap[Any, Any]]
          var valid: Boolean = true
          val it = m.entrySet().iterator()

          while (valid && it.hasNext) {
            val cur = it.next()
            valid = kCastable.cast(cur.getKey).isDefined && vCastable.cast(cur.getValue).isDefined
          }

          if (valid) Some(t.asInstanceOf[JMap[K, V]])
          else None
        } else None

      def describe = s"java.util.Map[${kCastable.describe}, ${vCastable.describe}]"
    }

  implicit def genJListCastable[K](implicit kCastable: Castable[K]): Castable[JList[K]] =
    new Castable[JList[K]] {
      def cast(t: Any): Option[JList[K]] = {
        if (t == null) None
        else if (t.isInstanceOf[JList[_]]) {
          val m = t.asInstanceOf[JList[Any]]
          var valid: Boolean = true
          val it = m.iterator()

          while (valid && it.hasNext)
            valid = kCastable.cast(it.next()).isDefined

          if (valid) Some(t.asInstanceOf[JList[K]])
          else None
        } else None
      }

      def describe = s"java.util.List[${kCastable.describe}]"
    }

  implicit def genJSetCastable[K](implicit kCastable: Castable[K]): Castable[JSet[K]] =
    new Castable[JSet[K]] {
      def cast(t: Any): Option[JSet[K]] =
        if (t == null) None
        else if (t.isInstanceOf[JSet[_]]) {
          val m = t.asInstanceOf[JSet[Any]]
          var valid: Boolean = true
          val it = m.iterator()

          while (valid && it.hasNext)
            valid = kCastable.cast(it.next()).isDefined

          if (valid) Some(t.asInstanceOf[JSet[K]])
          else None
        } else None

      def describe = s"java.util.Set[${kCastable.describe}]"
    }
}
