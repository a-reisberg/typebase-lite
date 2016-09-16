package com.so.tbldemo.launch

import com.so.typebaselite.mapper.Extract
import shapeless._

/**
  * Created by a.reisberg on 9/12/2016.
  */

sealed trait MT

case object MO1 extends MT

case object MO2 extends MT

case class Inner[T](inner: T)

case class Bigger[T](str: String, inside: List[Inner[T]])

case class Smaller[T](str: String, inside: List[Inner[T]])

case class Person(name: String, age: Int)

object ExtractDemo extends App {
  val bigger2smaller = implicitly[Extract[Bigger[MT], Smaller[MO2.type]]]

  println(bigger2smaller(Bigger("Nice", List(Inner(MO2)))))

  val pLens = lens[Person] >> 'name
}
