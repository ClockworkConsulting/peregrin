package dk.cwconsult.peregrin.core.impl

import java.util.UUID

import dk.cwconsult.peregrin.core.DisjointMigrationsException
import dk.cwconsult.peregrin.core.InvalidMigrationSequenceException
import dk.cwconsult.peregrin.core.Migration
import dk.cwconsult.peregrin.core.MigrationModifiedException
import dk.cwconsult.peregrin.core.Migrations
import dk.cwconsult.peregrin.core.Schema
import dk.cwconsult.peregrin.core.Table
import dk.cwconsult.peregrin.core.test.TempgresConnection
import org.scalatest.Assertion
import org.scalatest.WordSpec
import scalikejdbc.ConnectionPool
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.LoanPattern

class MigrationsImplSpec extends WordSpec {

  /**
  * Fixture for running the tests.
  */
  class Fixture(schema: Schema) {

    val connectionPool: ConnectionPool =
      TempgresConnection.createConnectionPool()

    val createXSql: String = "CREATE TABLE X (A INT)"
    val createYSql: String = "CREATE TABLE Y (B INT)"
    val createXSqlBad: String = "CREATE TABLE X (Y CHAR(1))"
    val createTableSql: String = "CREATE TABLE ? (X INT)"

    def assertCanQuery(sql: String)(implicit dbSession: DBSession): Assertion = {
      // Force a query; we are relying on the "list" function being strict here.
      dbSession.list[Unit](sql)(rs => ())
      // If we didn't have an exception, then we are certain that the relevant
      // change sets were applied.
      succeed
    }

    def readMigrations()(implicit dbSession: DBSession): Seq[(Int, String)] =
      dbSession.list[(Int, String)](
        s"""
           |  SELECT "identifier", "sql"
           |    FROM $schema."__peregrin_changelog__"
           |ORDER BY "identifier" ASC
           |""".stripMargin)(rs => (rs.int(1), rs.string(2)))

    def assertCanSelectFromX()(implicit dbSession: DBSession): Assertion =
      assertCanQuery("SELECT * FROM X")

    def assertCanSelectFromP(p: Table)(implicit dbSession: DBSession): Assertion =
      assertCanQuery(s"SELECT * FROM $p")

    def assertCanSelectFromPP(p: Table)(implicit dbSession: DBSession): Assertion =
      assertCanQuery(s"SELECT * FROM $p, $p")

    def assertCanSelectFromXY()(implicit dbSession: DBSession): Assertion =
      assertCanQuery("SELECT * FROM X, Y")

    def migrate(migrations: Seq[Migration])(implicit dbSession: DBSession): Unit = {
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

  // -----------------------------------------------------------

  for (schema <- Vector(Schema.Public, Schema.Named(UUID.randomUUID.toString))) {

    "Migration.applyMigrations method" when {
      s"applied to schema $schema" should {

        "can apply a single migration" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Seq(
              Migration(0, createXSql)))
            // Verify
            assertCanSelectFromX()
          }
        }

        "abort if duplicate migrations are provided(single call)" in new Fixture(schema) {
          withTransaction { implicit session =>
            assertThrows[InvalidMigrationSequenceException] {
              // Exercise
              migrate(Seq(
                Migration(0, createXSql),
                Migration(0, createXSql))) // Causes aborted migration
            }
          }
        }

        "ignores migrations that have already been applied (multiple calls)" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Seq(
              Migration(0, createXSql)))
            migrate(Seq(
              Migration(0, createXSql))) // Would fail if applied again
            // Verify
            assertCanSelectFromX()
          }
        }

        "throws an exception if SQL is changes for a given change set ID (single call)" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            val exception = intercept[MigrationModifiedException] {
              migrate(Seq(
                Migration(0, createXSql),
                Migration(0, createXSqlBad)))
            }
            // Verify
            assert(exception.getMessage.contains("does not match"))
          }
        }

        "throws an exception if SQL is changed for a given change set ID (multiple calls)" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Seq(
              Migration(0, createXSql)))
            val exception = intercept[MigrationModifiedException] {
              migrate(Seq(
                Migration(0, createXSqlBad)))
            }
            // Verify
            assert(exception.getMessage.contains("does not match"))
          }
        }

        "can apply multiple distinct migrations in a single call" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Seq(
              Migration(0, createXSql),
              Migration(1, createYSql)))
            // Verify: Make sure both migrations have been applied
            assertCanSelectFromXY()
          }
        }

        "throws an exception for sequences of migrations that do not start at 0" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            val exception = intercept[DisjointMigrationsException] {
              migrate(Seq(
                Migration(1, createXSql),
                Migration(2, createYSql)))
            }
            // Verify
            assert(exception.getMessage.contains("MUST be contiguous"))
          }
        }

        "throws an exception for non-contiguous sequences of migrations" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            val exception = intercept[DisjointMigrationsException] {
              migrate(Seq(
                Migration(0, createXSql),
                Migration(2, createYSql)))
            }
            // Verify
            assert(exception.getMessage.contains("MUST be contiguous"))
          }
        }

        "trim whitespace from beginning/end of SQL on insert" in new Fixture(schema) {
          withTransaction { implicit sessions =>
            // Exercise: Create the migration
            migrate(Seq(
              Migration(0, "\t    " + createXSql + "    ")
            ))
            // Verify
            val migrations = readMigrations()
            assert(migrations.head._1 === 0)
            assert(!migrations.head._2.head.isSpaceChar)
            assert(!migrations.head._2.last.isSpaceChar)
          }
        }

        "ignore differences in whitespace from beginning/end of SQL" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise: Create the 'base' migration
            migrate(Seq(
              Migration(0, createXSql)
            ))
            // Exercise: Add 'spurious' whitespace
            migrate(Seq(
              Migration(0, "  \t\n" + createXSql + "\r\n")
            ))
            // If we get here, we're OK
            succeed
          }
        }

      }
    }

  }

}
