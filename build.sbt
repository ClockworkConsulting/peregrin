import ReleaseTransformations._

//
// Project metadata
//

ThisBuild / organization := "dk.cwconsult.peregrin"

//
// Automatically reload the build when source changes
//

Global / onChangedBuildSource := ReloadOnSourceChanges

//
// Scala Versions
//

val scala_2_12 = "2.12.15"
val scala_2_13 = "2.13.8"

ThisBuild / scalaVersion := scala_2_12
ThisBuild / crossScalaVersions := Seq(scala_2_12, scala_2_13)

//
// Compiler Options
//

ThisBuild / Compile / compile / scalacOptions := Seq(
  "-encoding", "utf8",
  "-feature",
  "-deprecation",
  "-unchecked",
  "-Yno-adapted-args",
  "-Ywarn-value-discard",
  "-Xfatal-warnings",
  "-Xfuture",
  "-Xlint",
  "-Xmax-classfile-name", "200",
  "-Ypartial-unification")

// Add logging for all project "test" scopes
ThisBuild / libraryDependencies ++= Seq(
  Dependencies.log4j.api,
  Dependencies.log4j.impl,
).flatten.map(_ % "test")

// ==============================================================
// sbt-release
// ==============================================================

releaseCrossBuild := true

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)

// ==============================================================
// Peregrin Migrations
// ==============================================================

lazy val peregrinCore = Project("peregrin-core", file("modules/core"))
  .settings(
    libraryDependencies  += Dependencies.postgresqlDriver % "test",
    libraryDependencies  += Dependencies.scalaTest % "test",
    libraryDependencies  += Dependencies.scalikeJdbcCore % "test",
    libraryDependencies  += Dependencies.tempgresClient % "test"
  )

lazy val root = Project("peregrin-root", file("."))
  .settings(
    publishArtifact := false
  )
  .aggregate(
    peregrinCore
  )
