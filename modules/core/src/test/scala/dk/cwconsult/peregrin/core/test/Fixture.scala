package dk.cwconsult.peregrin.core.test

import java.util.UUID

import dk.cwconsult.peregrin.core.Migrations
import dk.cwconsult.peregrin.core.Schema
import dk.cwconsult.peregrin.core.Table
import dk.cwconsult.peregrin.core.{migrations => internal}
import dk.cwconsult.peregrin.core.Migration
import org.scalatest.Assertion
import org.scalatest.Assertions
import scalikejdbc.ConnectionPool
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.LoanPattern

/**
 * Fixture for running the tests.
 */
class Fixture(schema: Schema) {

  val connectionPool: ConnectionPool =
    TempgresConnection.createConnectionPool()

  val createXSql: String = "CREATE TABLE X (A INT)"
  val createYSql: String = "CREATE TABLE Y (B INT)"
  val createXSqlBad: String = "CREATE TABLE X (Y CHAR(1))"
  def createTableSql(table: String): String = s"CREATE TABLE $table (X INT)"

  val peregrinChangeLogTable: Table =
    Table("__peregrin_changelog__", schema)

  val peregrinMetaDataTable: Table =
    Table("__peregrin_metadata__", schema)

  def assertCanQuery(sql  : String)(implicit dbSession: DBSession): Assertion = {
    // Force a query; we are relying on the "list" function being strict here.
    dbSession.list[Unit](sql)(rs => ())
    // If we didn't have an exception, then we are certain that the relevant
    // change sets were applied.
    Assertions.succeed
  }

  case class ChangeLogEntry(
    legacyId: Int,
    sql: String,
    migrationId: Option[UUID]
  )

  def readMigrations()(implicit dbSession: DBSession): Seq[ChangeLogEntry] =
    dbSession.list[ChangeLogEntry](
      s"""
         |  SELECT "identifier", "sql", "migration_id"
         |    FROM $peregrinChangeLogTable
         |ORDER BY "identifier" ASC
         |""".stripMargin)(
      rs => ChangeLogEntry(
        rs.int(1),
        rs.string(2),
        rs.stringOpt(3).map(UUID.fromString)))

  def readChangeLogVersion()(implicit dbSession: DBSession): Option[Int] =
    dbSession.first(
      s"""
         SELECT "value" FROM $peregrinMetaDataTable WHERE "identifier" = ?
       """.stripMargin,
      "version")(rs =>
        rs.int(1)
      )

  def assertCanSelectFromX()(implicit dbSession: DBSession): Assertion =
    assertCanQuery("SELECT * FROM X")

  def assertCanSelectFromP(p: Table)(implicit dbSession: DBSession): Assertion =
    assertCanQuery(s"SELECT * FROM $p")

  def assertCanSelectFrom(p: String)(implicit dbSession: DBSession): Assertion =
    assertCanQuery(s"SELECT * FROM $p")

  def assertCanSelectFromPP(p: Table)(implicit dbSession: DBSession): Assertion =
    assertCanQuery(s"SELECT * FROM $p, $p")

  def assertCanSelectFromXY()(implicit dbSession: DBSession): Assertion =
    assertCanQuery("SELECT * FROM X, Y")

  def assertChangeLogVersionIs(expectedVersion: Int)(implicit dbSession: DBSession): Assertion = {
    import Assertions._
    assert(readChangeLogVersion() === Some(expectedVersion))
  }

  def migrate(migrations: Seq[Migration])(implicit dbSession: DBSession): Unit = {
    val _ = Migrations.applyMigrations(dbSession.connection, schema, migrations)
  }

  def migrate(migrations: Vector[internal.Migration])(implicit dbSession: DBSession): Unit = {
    val _ = Migrations.applyMigrations(dbSession.connection, schema, migrations)
  }

  def withTransaction(f: DBSession => Assertion): Assertion = {
    LoanPattern.using(connectionPool.borrow()) { connection =>
      DB(connection).localTx { implicit session =>
        f(session)
      }
    }
  }

}

