package com.so.typebaselite.mapper

/**
  * Created by a.reisberg on 9/7/2016.
  */
trait Default[T] {
  def get(): T
}

object Default {
  implicit def defaultOption[E]: Default[Option[E]] =
    new Default[Option[E]] {
      override def get(): Option[E] = None
    }

  implicit def defaultSet[E]: Default[Set[E]] =
    new Default[Set[E]] {
      override def get(): Set[E] = Set.empty[E]
    }

  implicit def defaultList[E]: Default[List[E]] =
    new Default[List[E]] {
      override def get(): List[E] = List.empty[E]
    }

  implicit def defaultMap[K, V]: Default[Map[K, V]] =
    new Default[Map[K, V]] {
      override def get(): Map[K, V] = Map.empty[K, V]
    }
}