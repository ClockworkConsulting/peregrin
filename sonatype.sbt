Global / PgpKeys.useGpg := true

Global / PgpKeys.gpgCommand := "gpg2"

Global / PgpKeys.useGpgAgent := true

ThisBuild / publishTo := sonatypePublishTo.value

ThisBuild / publishMavenStyle := true

sonatypeProfileName := "dk.cwconsult"

ThisBuild / licenses := Seq("BSD2" -> url("http://opensource.org/licenses/BSD-2-Clause"))

ThisBuild / homepage := Some(
  url("https://github.com/ClockworkConsulting/peregrin")
)

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/ClockworkConsulting/peregrin"),
    "git@github.com:ClockworkConsulting/peregrin.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id = "BardurArantsson",
    name = "Bardur Arantsson",
    email = "ba@cwconsult.dk",
    url = url("https://www.cwconsult.dk")
  )
)
