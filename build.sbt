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
val scala_3 = "3.1.1"

ThisBuild / scalaVersion := scala_2_12
ThisBuild / crossScalaVersions := Seq(scala_2_12, scala_2_13, scala_3)

//
// Compiler Options
//

ThisBuild / scalacOptions ++= {
  val v = scalaVersion.value
  CrossVersion.partialVersion(v) match {
    case Some((2, 12)) =>
      Seq(
        "-encoding", "utf8",
        "-feature",
        "-deprecation",
        "-unchecked",
        "-Yno-adapted-args",
        "-Ywarn-value-discard",
        "-Xfatal-warnings",
        "-Xsource:3",
        "-Xlint",
        "-Xmax-classfile-name", "200",
        "-Ypartial-unification",
      )
    case Some((2, 13)) =>
      Seq(
        "-encoding", "utf8",
        "-feature",
        "-deprecation",
        "-unchecked",
        "-Ywarn-value-discard",
        "-Xfatal-warnings",
        "-Xsource:3",
        "-Xlint",
        "-Xlint:adapted-args",
      )
    case Some((3, _)) =>
      Seq(
        "-encoding", "utf8",
        "-feature",
        "-deprecation",
        "-unchecked",
        "-Xfatal-warnings",
      )
    case _ =>
      throw new IllegalArgumentException(s"Unrecognized Scala version: $v")
  }
}

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
