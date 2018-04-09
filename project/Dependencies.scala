import sbt._

object Dependencies {

  val scalaTest =
    "org.scalatest" %% "scalatest" % Version.scalaTest

  val log4jApi =
    "org.apache.logging.log4j" % "log4j-api" % Version.log4j

  val log4jCore =
    "org.apache.logging.log4j" % "log4j-core" % Version.log4j

  val log4jSlf4jImpl =
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % Version.log4j

  val postgresqlDriver =
    "org.postgresql" % "postgresql" % Version.postgresqlDriver

  val scalikeJdbcCore =
    "org.scalikejdbc" %% "scalikejdbc-core" % Version.scalikeJDBC

  val tempgresClient =
    "dk.cwconsult" % "tempgres-client" % Version.tempgresClient

}
