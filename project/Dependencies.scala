import sbt._

object Dependencies {

  val scalaTest =
    "org.scalatest" %% "scalatest" % "3.0.8"

  object log4j {
    private val v = "2.8.2"
    val api = Seq(
      "org.apache.logging.log4j" % "log4j-api" % v,
    )
    val impl = Seq(
      "org.apache.logging.log4j" % "log4j-core" % v,
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % v,
    )
  }

  val postgresqlDriver =
    "org.postgresql" % "postgresql" % "42.1.4.jre7"

  val scalikeJdbcCore =
    "org.scalikejdbc" %% "scalikejdbc-core" % "3.4.0"

  val tempgresClient =
    "dk.cwconsult" % "tempgres-client" % "1.1.0"

}
