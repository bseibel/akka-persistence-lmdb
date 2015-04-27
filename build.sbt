import sbt.Keys._

organization := "com.github.bseibel"

name := "akka-persistence-lmdb"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.11.6"

crossScalaVersions := Seq("2.10.4")

parallelExecution in Test := false

fork := true

scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-deprecation",
  "-unchecked",
  "-feature",
  "-language:implicitConversions",
  "-language:reflectiveCalls",
  "-language:postfixOps",
  "-Yinline-warnings"
)


publishMavenStyle := true

pomIncludeRepository := { _ => false }

publishTo <<= version {
  (v: String) =>
    Some("bintray" at "https://api.bintray.com/maven/bseibel/release/akka-persistence-lmdb")
}


credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

val akkaVersion = "2.3.10"

libraryDependencies += "com.typesafe.akka" %% "akka-persistence-tck-experimental" % akkaVersion % "test"
libraryDependencies += "com.typesafe.akka" %% "akka-persistence-experimental" % akkaVersion % "compile"
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
libraryDependencies += "org.scalatest" %% "scalatest" % "2.1.4" % "test"
libraryDependencies += "commons-io" % "commons-io" % "2.4" % "test"
libraryDependencies += "org.deephacks.lmdbjni" % "lmdbjni" % "0.4.0"
libraryDependencies += "org.deephacks.lmdbjni" % "lmdbjni-osx64" % "0.4.0"
libraryDependencies += "org.deephacks.lmdbjni" % "lmdbjni-linux64" % "0.4.0"
