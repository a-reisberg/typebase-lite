name := "typebase-lite"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.8"

// scalacOptions ++= Seq("-Xlog-implicits")

lazy val myOrg = "com.so"

organization := myOrg

libraryDependencies ++=
  Seq("com.chuusai" %% "shapeless" % "2.3.2",
    "com.couchbase.lite" % "couchbase-lite-java" % "1.3.0",
    "com.couchbase.lite" % "couchbase-lite-java-forestdb" % "1.3.0")

