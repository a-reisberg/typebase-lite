package com.so.typebaselite.mapper

import java.util.{List => JList, Map => JMap, Set => JSet}

import shapeless.Typeable
import shapeless.syntax.typeable._

import scala.util.Try

/**
  * Created by a.reisberg on 8/12/2016.
  */
trait JavaTypeabilityHelper {
  // Override Shapeless's default Typeable[AnyRef], which fails on boxed primitive types
  implicit val anyRefTypeable: Typeable[AnyRef] = new Typeable[AnyRef] {
    def cast(t: Any): Option[AnyRef] =
      Try(t.asInstanceOf[AnyRef]).toOption

    def describe: String = "AnyRef"
  }

  implicit def genJMapTypeable[K, V](implicit
                                     kTypeable: Typeable[K],
                                     vTypeable: Typeable[V]): Typeable[JMap[K, V]] =
    new Typeable[JMap[K, V]] {
      def cast(t: Any): Option[JMap[K, V]] =
        if (t == null) None
        else if (t.isInstanceOf[JMap[_, _]]) {
          val m = t.asInstanceOf[JMap[Any, Any]]
          var valid: Boolean = true
          val it = m.entrySet().iterator()

          while (valid && it.hasNext) {
            val cur = it.next()
            valid = cur.getKey.cast[K].isDefined && cur.getValue.cast[V].isDefined
          }

          if (valid) Some(t.asInstanceOf[JMap[K, V]])
          else None
        } else None

      def describe = s"java.util.Map[${kTypeable.describe}, ${vTypeable.describe}]"
    }

  implicit def genJListTypeable[K](implicit kTypeable: Typeable[K]): Typeable[JList[K]] =
    new Typeable[JList[K]] {
      def cast(t: Any): Option[JList[K]] = {
        if (t == null) None
        else if (t.isInstanceOf[JList[_]]) {
          val m = t.asInstanceOf[JList[Any]]
          var valid: Boolean = true
          val it = m.iterator()

          while (valid && it.hasNext)
            valid = it.next().cast[K].isDefined

          if (valid) Some(t.asInstanceOf[JList[K]])
          else None
        } else None
      }

      def describe = s"java.util.List[${kTypeable.describe}]"
    }

  implicit def genJSetTypeable[K](implicit kTypeable: Typeable[K]): Typeable[JSet[K]] =
    new Typeable[JSet[K]] {
      def cast(t: Any): Option[JSet[K]] =
        if (t == null) None
        else if (t.isInstanceOf[JSet[_]]) {
          val m = t.asInstanceOf[JSet[Any]]
          var valid: Boolean = true
          val it = m.iterator()

          while (valid && it.hasNext)
            valid = it.next().cast[K].isDefined

          if (valid) Some(t.asInstanceOf[JSet[K]])
          else None
        } else None

      def describe = s"java.util.Set[${kTypeable.describe}]"
    }
}