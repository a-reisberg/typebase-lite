name := "typebase-lite"

scalaVersion := "2.11.8"

// scalacOptions ++= Seq("-Xlog-implicits")

lazy val myOrg = "com.so"
lazy val shapelessVersion = "2.3.2"
lazy val cblJavaVersion = "1.3.0"
lazy val cblAndroidVersion = "1.3.0"
lazy val cblJavaForestDBVersion = "1.3.0"

lazy val currentVersion = "0.1-SNAPSHOT"

lazy val commonSettings = Seq(
  scalaVersion := "2.11.8",
  organization := myOrg,
  version := currentVersion,
  scalacOptions ++= Seq("-feature", "-language:postfixOps", "-language:implicitConversions", "-language:higherKinds")
)

lazy val tblCore = (project in file("tblcore"))
  .settings(commonSettings)
  .settings(
    name := "typebase-lite-core",
    libraryDependencies ++= Seq(
      "com.chuusai" %% "shapeless" % shapelessVersion,
      "com.couchbase.lite" % "couchbase-lite-java" % cblJavaVersion,
      "com.couchbase.lite" % "couchbase-lite-java-forestdb" % cblJavaForestDBVersion
    )
  )

lazy val tblJava = (project in file("tbljava"))
  .settings(commonSettings)
  .settings(
    name := "typebase-lite-java",
    libraryDependencies ++= Seq(
      "com.couchbase.lite" % "couchbase-lite-java" % cblJavaVersion
    )
  ) dependsOn tblCore  aggregate tblCore

lazy val tblAndroid = (project in file("tblandroid"))
  .settings(commonSettings)
  .settings(
    name := "typebase-lite-android",
    libraryDependencies ++= Seq(
      "com.couchbase.lite" % "couchbase-lite-android" % cblAndroidVersion
    )
  ) dependsOn tblCore  aggregate tblCore

