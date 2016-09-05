[![Build Status](https://travis-ci.org/a-reisberg/typebase-lite.svg?branch=master)](https://travis-ci.org/a-reisberg/typebase-lite)

#typebase-lite
Typebase lite is a functional ORM for Couchbase lite: free of boilerplate and runtime reflection.


##Table of contents
1. [What?](#what)
2. [Why?](#why)
    1. [Why Couchbase lite?](#why-couchbase-lite)
    2. [Why Typebase lite?](#why-typebase-lite)
3. [Show me the code](#show-me-the-code)
    1. [Quick demo](#quick-demo)
    2. [What is happening behind the scene?](#what-is-happening-behind-the-scene)
    3. [Isn’t that slow? How about indexing?](#isnt-that-slow-how-about-indexing)
    4. [More complex queries?](#more-complex-queries)
4. [Quick start](#quick-start)
5. [A long running example](#a-long-running-example)
    1. [Standard JVM](#standard-jvm)
    2. [Android](#android)
    3. [Import packages](#import-packages)
    4. [Initialize database](#initialize-database)
    5. [Define schema](#define-schema)
    6. [Insert](#insert)
    7. [Query by keys](#query-by-keys)
    8. [Query by types](#query-by-types)
    9. [More complex queries](#more-complex-queries)
    10. [Index/View](#indexview)
    11. [Live queries](#live-queries)
6. [A more in-depth overview](#a-more-in-depth-overview)
    

##What?

Typebase lite is a thin Scala wrapper for the Java and Android versions of Couchbase lite. It provides an automatic mapper between Couchbase lite's data and Scala's case classes and sealed trait hierarchies, free of boilerplate and runtime reflection. Moreover, many convenient functional combinators are also given to create and compose queries in a type-safe and functional manner a la LINQ. 

Currently, it supports the following features: Map views (Reduce will come soon), queries and live queries. It works on both Android and the standard JVM. Since it's just a thin wrapper, any unsupported feature can be done directly with Couchbase lite.  

It's a work in progress. The api might change without any prior warning. There's much to be improved and **you** are very welcome to try out, give comments/suggestions, and contribute!

> Note: We do not aim to wrap all features of Couchbase lite. Rather, our current goal is to provide a minimal and composable set of functionalities that improve the type-safety of Couchbase lite. Thus, if type-safety is not an issue for a feature X, X should be used directly via Couchbase lite's api.

##Why?

####Why Couchbase lite?
The reason why we choose Couchbase lite is its support for NoSql and, more crucially, its powerful sync feature which makes writing apps with offline functionality pleasant.

####Why Typebase lite?
Type-safety is one of the main pain points of using a database. It comes in two places: querying and mapping between the database's and the language's data types. In the case of Couchbase lite, queried results are usually of the form (Java's) `Map[String, AnyRef]`, and anything, for eg. an `Array` or another nested `Map`, might present in the `AnyRef` part. This makes querying and persisting data a rather painful and error prone process.

Typebase lite automates all of those boring/error prone parts for you!

##Show me the code
####Quick demo
```scala
val query = for {
    employee <- tblDb.typeView[Employee].filter(e => (e.age >= 30) && (e.address.city == "New York"))
} yield employee
```
will create a query of all employees living in New York, and are at least 30 years old.
 
```scala
query.foreach(println(_))
```
will execute the query and print out the results.

####What is happening behind the scene?
`tblDb` is Typebase lite's database object. The code above will loop over all employees and filter out the ones that satisfy our conditions.

####Isn't that slow? How about indexing?
Yes! In the above, we already used one, which indexes the types of the documents.

You can create a custom indices, even **composite indices** are supported! In this case we want to create a composite index consisting of 2 keys: city and age:
```scala
val cityAgeIndex = tblDb.createIndexView[String :: Int :: HNil]("city-age", "1.0", {
    case e: Employee => Set(e.address.city :: e.age :: HNil)
    case _ => Set()
})
```

Now, the same query as above, but now use the index
```scala
val query2 = cityAgeIndex.sQuery(startKey("New York" :: 30 :: HNil), endKey("New York" :: Last))
```

####More complex queries?
Queries can be composed and reused in a functional manner, and can be mixed and matched seamlessly with Scala's collections as well.

## Quick start
To start using this library, first clone the repository 
```
git clone https://github.com/a-reisberg/typebase-lite.git
```
and run
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
if you're using it on Android.

It works with both the standard storage option or ForestDb. To use the latter, just add
```scala
libraryDependencies += "com.couchbase.lite" % "couchbase-lite-java-forestdb" % "1.3.0"
```
or
```scala
libraryDependencies += "com.couchbase.lite" % "couchbase-lite-android-forestdb" % "1.3.0"
```
depending on whether you're writing for Android.

Many of the concepts here are related to (and influenced by) the ones in Couchbase lite, since we are, after all, a wrapper of that library. So, it might be instructive to first give a quick glance at their documentation.

## A long running example
#### Standard JVM
The examples mentioned below runs outside of Android and could be found under the `tbljavademo` subproject. The data schema is defined in
```scala
com.so.tbldemo.data
```
and the running example implemented in
```scala
com.so.tbldemo.launch.LaunchDemo
```
You can jump straight ahead to `com.so.tbldemo.launch.LaunchDemo` and run it.

#### Android
The Android demo could be found here: [https://github.com/a-reisberg/tbl-android-demo](https://github.com/a-reisberg/tbl-android-demo).

#### Import packages
For this example, we need the following imports:
```scala
import com.couchbase.lite._
import com.so.typebaselite.TblQuery._
import com.so.typebaselite._
import shapeless._
```

#### Initialize database
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

#### Define schema
First we define the following classes which we want to persist in the database. The trait `Company` will be the super type of everything stored in our database. This part is **completely independent** of Typebase lite.  
```scala
sealed trait Company

case class Department(name: String, employeeIds: List[String]) extends Company

case class Employee(name: String, age: Int, address: Address) extends Company

case class Address(city: String, zip: String)
```

#### Insert
Let's first create some random data for `Employee`'s
```scala
// Create some random addresses
val ny1 = Address("New York", "12345")
val ny2 = Address("New York", "13245")
// ... more addresses ...

// and some random employees
val john = Employee("John Doe", 35, ny1)
val jamie = Employee("Jamie Saunders", 25, ny2)
// ... more employees ...
```

And now, we can insert the employees. The `put` function returns the `_id` of the item inserted.
```scala
// Now add employees to the db
val johnId = tblDb.put(john)
val jamieId = tblDb.put(jamie)
// ... more ids ...
```

We finish data initialization with creating and inserting the `Department`'s.
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

#### Query by keys
To retrieve, for example, the sale department, and the hr department, we just need to call `tblDb[Department](saleDeptId, hrId)`, which will return a `Seq` of `Department`'s.
```scala
println(tblDb[Department](saleDeptId, hrId))
```

#### Query by types
Typebase lite automatically creates an index based on the type of each entry. This index could be accessed by
```scala
tblDb.typeView
```

Querying all departments, for eg.
```scala
val deptQ = tblDb.typeView[Department]
```

`deptQ` is just a description of the query, which only runs when `deptQ.foreach(...)` is called
```scala
deptQ.foreach(println)
```
Thus, the query could be reused later, and moreover, it could also be combined with other queries using the provided combinators (more of this later).
 
For-comprehension also works
```scala
for (dept <- deptQ) println(dept)
```

#### More complex queries
Now, suppose we want to query all of the following pairs
```
(Department names, List of employees in that department)
```

Again, it's a two-step process
```scala
// First create the query
val deptEmployeeQ = for {
   dept <- deptQ
   employeeNames = tblDb[Employee](dept.employeeIds: _*).map(_.name)
} yield (dept.name, employeeNames)

// Then execute the query and print out the results
deptEmployeeQ.foreach(println)
```
We see here that we seamlessly reused `deptQ` from before.

As another example, we want to get everyone who lives in New York, and whose age is >= 30
```scala
val cityAndAgeQ = for {
    employee <- tblDb.typeView[Employee].filter(e => (e.age >= 30) && (e.address.city == "New York"))
} yield employee

cityAndAgeQ.foreach(println)
```

#### Index/View
The last query in the previous section just loops over all employees and filters out the ones that satisfy our condition. If it's something we run often, we can create a compound index, consisting of a pair
```scala
(String, Int)
```
where `String` and `Int` are for city names and ages respectively.
```scala
// Create the index
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

#### Live queries
Now, we want to be notified whenever the result returned by `cityAgeQ2` changes (for eg. someone new from New York of age >= 30 starts at our company). A Live query will help with that.

First create the live query:
```scala
val liveQ = cityAgeIndex.sLiveQuery(startKey("New York" :: 30 :: HNil), endKey("New York" :: Last)).flatMap(_.to[Employee])
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
the query will be re-run automatically, and the results will be printed out.

The `subscription` returned by `liveQ.subscribe` could be used to unsubscribe when we are done:
```scala
subscription.dispose()
```

Finally, we can stop the live query by
```scla
liveQ.stop()
```

##A more in-depth overview
Soon to come!