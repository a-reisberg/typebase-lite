package com.so.tbldemo.data

/**
  * Created by a.reisberg on 8/30/2016.
  */
sealed trait Company

case class Department(name: String, employeeIds: List[String]) extends Company

case class Employee(name: String, age: Int, address: Address) extends Company

case class Address(city: String, zip: String)