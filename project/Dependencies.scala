import sbt._

object Dependencies {

  val scalaTest =
    "org.scalatest" %% "scalatest" % "3.2.11"

  object log4j {
    private val v = "2.17.1"
    val api = Seq(
      "org.apache.logging.log4j" % "log4j-api" % v,
    )
    val impl = Seq(
      "org.apache.logging.log4j" % "log4j-core" % v,
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % v,
    )
  }

  val postgresqlDriver =
    "org.postgresql" % "postgresql" % "42.3.2"

  val scalikeJdbcCore =
    "org.scalikejdbc" %% "scalikejdbc-core" % "4.0.0"

  val tempgresClient =
    "dk.cwconsult" % "tempgres-client" % "1.1.0"

}
