import com.typesafe.sbt.pgp.PgpKeys._

scalaVersion := "2.11.8"

// scalacOptions ++= Seq("-Xlog-implicits")

lazy val myOrg = "com.shalloui"
lazy val shapelessVersion = "2.3.2"
lazy val cblJavaVersion = "1.3.0"
lazy val cblAndroidVersion = "1.3.0"
lazy val cblJavaForestDBVersion = "1.3.0"

lazy val currentVersion = "0.1"

lazy val commonSettings = Seq(
  scalaVersion := "2.11.8",
  organization := myOrg,
  version := currentVersion,
  javacOptions ++= Seq("-source", "1.7", "-target", "1.7"),
  scalacOptions ++= Seq("-feature", "-language:postfixOps", "-language:implicitConversions", "-language:higherKinds", "-target:jvm-1.7")
)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(publishSigned := ())
  .aggregate(tblCore, tblJava, tblAndroid)

lazy val tblCore = (project in file("tblcore"))
  .settings(commonSettings)
  .settings(
    name := "typebase-lite-core",
    libraryDependencies ++= Seq(
      "com.chuusai" %% "shapeless" % shapelessVersion,
      "com.couchbase.lite" % "couchbase-lite-java" % cblJavaVersion % "compile-internal"
    )
  )

lazy val tblJava = (project in file("tbljava"))
  .settings(commonSettings)
  .settings(
    name := "typebase-lite-java",
    libraryDependencies ++= Seq(
      "com.couchbase.lite" % "couchbase-lite-java" % cblJavaVersion
    )
  ) dependsOn tblCore aggregate tblCore

lazy val tblAndroid = (project in file("tblandroid"))
  .settings(commonSettings)
  .settings(
    name := "typebase-lite-android",
    libraryDependencies ++= Seq(
      "com.couchbase.lite" % "couchbase-lite-android" % cblAndroidVersion
    )
  ) dependsOn tblCore aggregate tblCore

lazy val tblJavaDemo = (project in file("tbljavademo"))
  .settings(commonSettings)
  .settings(
    name := "typebase-lite-java-demo",
    libraryDependencies ++= Seq(
      "com.couchbase.lite" % "couchbase-lite-java-forestdb" % cblJavaForestDBVersion
    )
  ) dependsOn tblJava

