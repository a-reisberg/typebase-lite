package com.so.tbldemo.launch

import com.couchbase.lite.{JavaContext, Manager}
import com.so.tbldemo.data._
import com.so.typebaselite.TblQuery._
import com.so.typebaselite._
import shapeless._

import scala.io.StdIn

/**
  * Created by a.reisberg on 8/31/2016.
  */
object LaunchDemo extends App {
  // Initialize couchbase lite, as usual.
  val manager = new Manager(new JavaContext("data"), Manager.DEFAULT_OPTIONS)
  manager.setStorageType("ForestDB")
  manager.getDatabase("my-database").delete()
  val db = manager.getDatabase("my-database")

  // This is all you need to set up the typebase lite.
  // Each document inserted into the db will have a type field.
  // No reflection is done at runtime. Everything is processed at compile time.
  val tblDb = TblDb[Company](db)

  // Create some random addresses
  val ny1 = Address("New York", "12345")
  val ny2 = Address("New York", "13245")
  val chicago = Address("Chicago", "43254")
  val sf = Address("San Francisco", "13324")

  // and some random employees
  val john = Employee("John Doe", 35, ny1)
  val jamie = Employee("Jamie Saunders", 25, ny2)
  val bradford = Employee("Bradford Newton", 30, chicago)
  val tina = Employee("Tina Rivera", 23, sf)
  val whitney = Employee("Whitney Perez", 40, sf)

  // Now add employees to the db
  val johnId = tblDb.put(john)
  val jamieId = tblDb.put(jamie)
  val bradfordId = tblDb.put(bradford)
  val tinaId = tblDb.put(tina)
  val whitneyId = tblDb.put(whitney)

  // Now create the departments
  val saleDept = Department("Sale", List(johnId, jamieId))
  val hr = Department("HR", List(bradfordId))
  val customerSupp = Department("Customer Support", List(tinaId, whitneyId))

  // Now insert departments to the Db
  val saleDeptId = tblDb.put(saleDept)
  val hrId = tblDb.put(hr)
  val customerSuppId = tblDb.put(customerSupp)

  // Get some departments out of Db
  printSection("Get sale dept and hr dept")
  println(tblDb[Department](saleDeptId, hrId))

  // Query all departments.
  // typeView is a view created automatically by typebase lite.
  // deptQ just build the query, and doesn't run it yet. So one can reuse it in the future.
  val deptQ = tblDb.typeView[Department]

  // Now, run the query.
  printSection("Print out all dept")
  deptQ.foreach(println(_))

  // For comprehension also works
  printSection("Or by for comprehension")
  for (dept <- deptQ) println(dept)

  // A bit more complex query: query all department names along with list of employees name
  // deptQ was defined earlier, i.e. one can reuse it and compose with other queries using
  // various combinators.
  printSection("Department name, along with List of employee names")
  val deptEmployeeQ = for {
    dept <- deptQ
    employeeNames = tblDb[Employee](dept.employeeIds: _*).map(_.name)
  } yield (dept.name, employeeNames)

  deptEmployeeQ.foreach(println)

  // Now, we want to create a more complex query: everyone who lives whose age is > 30 and lives in NYC
  printSection("Everyone whose age is > 30 and is living in New York")
  val cityAndAgeQ = for {
    employee <- tblDb.typeView[Employee].filter(e => (e.age > 30) && (e.address.city == "New York"))
  } yield employee

  cityAndAgeQ.foreach(println)

  // But that was inefficient, because it has to loop through everyone.
  // Would it be nicer to have an index if we do this query often? Enter View!

  // First, create TblView. The key of the index is String (for city) and Int (for age).
  // More general MapViews and also be created via createMapView. Map-Reduce will come soon.
  printSection("Same query, but with index")
  val cityAgeIndex = tblDb.createIndexView[String :: Int :: HNil]("city-age", "1.0", {
    case e: Employee => Set(e.address.city :: e.age :: HNil)
    case _ => Set()
  })

  // Now, we create a query using the index. This Query can also be mixed with others, using various combinators.
  val cityAgeQ2 = cityAgeIndex.sQuery(startKey("New York" :: 30 :: HNil), endKey("New York" :: Last))

  cityAgeQ2.foreach(println)

  // Live queries are also supported. Now we want to be notified
  // whenever someone from New York, whose age is > 30 starts at our company.
  printSection("Live query: anyone new of age > 30 from NY?")
  val liveQ = cityAgeIndex.sLiveQuery(startKey("New York" :: 30 :: HNil), endKey("New York" :: Last)).flatMap(_.to[Employee])
  val subscription = liveQ.subscribe(_.foreach(println))
  liveQ.start()

  tblDb.put(Employee("New Comer", 31, Address("New York", "99999")))

  StdIn.readLine("\n***** Press Enter to unsubscribe! *****") // Wait a bit

  // Unsubscribe
  subscription.dispose()

  printSection("Someone new just joined us, but noone gets notified because we already unsubscribed")
  tblDb.put(Employee("New Comer2", 31, Address("New York", "99999"))) // should print out nothing

  StdIn.readLine("\n***** Press Enter to stop the live query! *****")
  liveQ.stop()

  def printSection(s: String): Unit =
    println(s"\n----- $s -----")
}
