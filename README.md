[![Build Status](https://travis-ci.org/a-reisberg/typebase-lite.svg?branch=master)](https://travis-ci.org/a-reisberg/typebase-lite)

typebase-lite
=============
Typebase lite is a Scala wrapper for the Java version of Couchbase lite. It provides a convenient mapper between Couchbase lite's data and Scala's classes, free of boilerplate and runtime reflection. Moreover, many convenient combinators are also given to combine and reuse queries in a type-safe and functional manner. 

Currently, it supports the following features: Map views (reduce will come soon), queries and live queries. It works on both Android and the standard Jvm.

It's a work in progress. The api's might be changed without any prior warning. **You** are very much welcomed to give comments/suggestions or to contribute to the project.

# Quick start
To start using this library, first clone the repository and run
```
sbt publishLocal
```

Inside the `build.sbt` of your project, add the following line
```scala
libraryDependencies += "com.so" %% "typebase-lite-java" % "0.1-SNAPSHOT"
```
if you're using it in the standard JVM, or
```scala
libraryDependencies += "com.so" %% "typebase-lite-android" % "0.1-SNAPSHOT"
```

Many of the concepts here are related to (and influenced by) the ones in Couchbase lite, since we are, after all, a wrapper of that library. So, it might be instructive to first give a quick glance at their documentation.

# Examples
## Standard JVM
The examples mentioned below runs outside of Android, and could be found in the `tbljavademo` subproject. More specifically, the data schema is defined in
```scala
com.so.tbldemo.data
```
and the running example implemented in
```scala
com.so.tbldemo.launch.LaunchDemo
```
You can jump straight ahead to `com.so.tbldemo.launch.LaunchDemo` and run it.

## Android
The Android demo could be found here: [https://github.com/a-reisberg/tbl-android-demo](https://github.com/a-reisberg/tbl-android-demo).

## Define data
First we define the following classes which we want to persist in the database. The trait `Company` will be the super type of everything stored in our database.  
```scala
sealed trait Company

case class Department(name: String, employeeIds: List[String]) extends Company

case class Employee(name: String, age: Int, address: Address) extends Company

case class Address(city: String, zip: String)
```

## Import packages
For this example, we need the following imports:
```scala
import com.couchbase.lite.{JavaContext, Manager}
import com.so.tbldemo.data._
import com.so.typebaselite.TblQuery._
import com.so.typebaselite._
import com.so.typebaselite.mapper._
import shapeless._
```

## Initialize Database
First, we initialize Couchbase lite as usual:
```scala
val manager = new Manager(new JavaContext("data"), Manager.DEFAULT_OPTIONS)
manager.setStorageType("ForestDB")
manager.getDatabase("my-database").delete()
val db = manager.getDatabase("my-database")
```

The only thing we need to do to initialize Typebase lite is to add the following line
```scala
val tblDb = TblDb[Company](db)
```

## Insert
Let's first create some random data for `Employee`'s
```scala
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
```

And now, we can insert the employees. The `put` function returns the `_id` of the item inserted.
```scala
// Now add employees to the db
val johnId = tblDb.put(john)
val jamieId = tblDb.put(jamie)
val bradfordId = tblDb.put(bradford)
val tinaId = tblDb.put(tina)
val whitneyId = tblDb.put(whitney)
```

We finish data initialization with creating and adding the `Department`'s.
```scala
// Now create the departments
val saleDept = Department("Sale", List(johnId, jamieId))
val hr = Department("HR", List(bradfordId))
val customerSupp = Department("Customer Support", List(tinaId, whitneyId))

// Now insert departments to the Db
val saleDeptId = tblDb.put(saleDept)
val hrId = tblDb.put(hr)
val customerSuppId = tblDb.put(customerSupp)
```

## Query by keys
To retrieve, for example, the sale department, and the hr department, we just need to call `tblDb[Department](saleDeptId, hrId)`, which will return a `Seq` of `Department`'s.
```scala
println(tblDb[Department](saleDeptId, hrId))
```

## Type query
Typebase lite automatically creates an index for the types of the entries, which could be accessed by
```scala
tblDb.typeView
```

Here's how to query, for example, all departments
```scala
val deptQ = tblDb.typeView[Department]
```

`deptQ` is just a description of the query. It only runs when `deptQ.foreach(...)` is called. This means that the query could be reused later, and moreover, it could be combined with other queries using the provided combinators (more of this later).
```scala
deptQ.foreach(println)
```
or via for comprehension:
```scala
for (dept <- deptQ) println(dept)
```

## A more interesting query
Now, suppose we want to query all of the following pairs
```
(Department names, List of employees in that department)
```

Here is how it's done
```scala
val deptEmployeeQ = for {
   dept <- deptQ
   employeeNames = tblDb[Employee](dept.employeeIds: _*).map(_.name)
} yield (dept.name, employeeNames)

deptEmployeeQ.foreach(println)
```

Again, `deptEmployeeQ` is only a description. Nothing is run until the `.foreach` is called. We also see here that we seamlessly reused `deptQ` from before.

## A more complex query
Now, we want to get everyone who lives in New York, and whose age is >= 30.
```scala
val cityAndAgeQ = for {
    employee <- tblDb.typeView[Employee].filter(e => (e.age >= 30) && (e.address.city == "New York"))
} yield employee

cityAndAgeQ.foreach(println)
```

## Index/View
The query in the previous section just loops over all employees and filter out the ones that satisfy our condition. If it's something we run often, we can create an index, consisting of a pair of String (for city) and Int (for age) as follows
```scala
val cityAgeIndex = tblDb.createIndexView[String :: Int :: HNil]("city-age", "1.0", {
    case e: Employee => Set(e.address.city :: e.age :: HNil)
    case _ => Set()
})
```

Now, we can do the same query, but using the index instead:
```scala
val cityAgeQ2 = cityAgeIndex.sQuery(startKey("New York" :: 30 :: HNil), endKey("New York" :: Last))

cityAgeQ2.foreach(println)
```

Under the hood, `Index` is created by Couchbase lite's View. More general MapViews can be created via `createMapView`. Map-Reduce will come soon.

## Live queries
Now, we want to be notified whenever `cityAgeQ2` changes (i.e. someone new from New York of age >= 30 starts at our company). A Live query will help with that.

First create the live query:
```scala
val liveQ = cityAgeIndex.sLiveQuery(startKey("New York" :: 30 :: HNil), endKey("New York" :: Last)).where(_.is[Employee])
```

Next, subscribe (in this case, we just dump the query's results to StdOut)
```scala
val subscription = liveQ.subscribe(_.foreach(println))
```

And then, start the monitoring
```scala
liveQ.start()
```

Suppose someone new enters the db:
```scala
tblDb.put(Employee("New Comer", 31, Address("New York", "99999")))
```
the query will be run, and the results will be printed out.

The `subscription` returned by `liveQ.subscribe` could be used to unsubscribe when we are done:
```scala
subscription.dispose()
```

Finally, we can stop the live query by
```scla
liveQ.stop()
```