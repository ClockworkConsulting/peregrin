PgpKeys.useGpg in Global := true

PgpKeys.gpgCommand in Global := "gpg2"

PgpKeys.useGpgAgent in Global := true

publishTo in ThisBuild := sonatypePublishTo.value

publishMavenStyle in ThisBuild := true

sonatypeProfileName := "dk.cwconsult"

licenses in ThisBuild := Seq("BSD2" -> url("http://opensource.org/licenses/BSD-2-Clause"))

homepage in ThisBuild := Some(
  url("https://github.com/ClockworkConsulting/peregrin")
)

scmInfo in ThisBuild := Some(
  ScmInfo(
    url("https://github.com/ClockworkConsulting/peregrin"),
    "git@github.com:ClockworkConsulting/peregrin.git"
  )
)

developers in ThisBuild := List(
  Developer(
    id = "BardurArantsson",
    name = "Bardur Arantsson",
    email = "ba@cwconsult.dk",
    url = url("https://www.cwconsult.dk")
  )
)
