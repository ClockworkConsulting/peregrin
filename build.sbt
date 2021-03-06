import ReleaseTransformations._

//
// Project metadata
//

organization in ThisBuild := "dk.cwconsult.peregrin"

//
// Scala Versions
//

val scalaVersions = Seq("2.11.12", "2.12.10", "2.13.1")

scalaVersion in ThisBuild :=
  scalaVersions.find(v => "^2\\.12\\.".r.pattern.matcher(v).find()).get

crossScalaVersions in ThisBuild :=
  scalaVersions

//
// Compiler Options
//

scalacOptions in (Compile, compile) in ThisBuild := Seq(
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
libraryDependencies in ThisBuild ++= Seq(
  Dependencies.log4jApi % "test",
  Dependencies.log4jCore % "test",
  Dependencies.log4jSlf4jImpl % "test")

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
