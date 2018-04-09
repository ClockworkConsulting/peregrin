//
// Project metadata
//

organization in ThisBuild := "dk.cwconsult.peregrin"

//
// Scala Versions
//

scalaVersion in ThisBuild := "2.12.4"

crossScalaVersions in ThisBuild := Seq("2.11.12", "2.12.4")

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
