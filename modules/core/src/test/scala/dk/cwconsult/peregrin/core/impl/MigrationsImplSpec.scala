package dk.cwconsult.peregrin.core.impl

import java.util.UUID

import dk.cwconsult.peregrin.core.DisjointMigrationsException
import dk.cwconsult.peregrin.core.Migration
import dk.cwconsult.peregrin.core.MigrationModifiedException
import dk.cwconsult.peregrin.core.Migrations
import dk.cwconsult.peregrin.core.Schema
import dk.cwconsult.peregrin.core.Table
import dk.cwconsult.peregrin.core.impl.MigrationsImplSpec.createConnectionPool
import dk.cwconsult.tempgres.TempgresClient
import org.scalatest.Assertion
import org.scalatest.WordSpec
import scalikejdbc.ConnectionPool
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.GlobalSettings
import scalikejdbc.LoanPattern
import scalikejdbc.LoggingSQLAndTimeSettings

class MigrationsImplSpec extends WordSpec {

  /**
  * Fixture for running the tests.
  */
  class Fixture(schema: Schema) {

    val connectionPool: ConnectionPool =
      createConnectionPool()

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

    def assertCanSelectFromX()(implicit dbSession: DBSession): Assertion =
      assertCanQuery("SELECT * FROM X")

    def assertCanSelectFromP(p: Table)(implicit dbSession: DBSession): Assertion =
      assertCanQuery(s"SELECT * FROM $p")

    def assertCanSelectFromPP(p: Table)(implicit dbSession: DBSession): Assertion =
      assertCanQuery(s"SELECT * FROM $p, $p")

    def assertCanSelectFromXY()(implicit dbSession: DBSession): Assertion =
      assertCanQuery("SELECT * FROM X, Y")

    def migrate(migrations: Seq[Migration])(implicit dbSession: DBSession): Unit = {
      Migrations.applyMigrations(dbSession.connection, schema, migrations)
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

        "ignores migrations that have already been applied (single call)" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Seq(
              Migration(0, createXSql),
              Migration(0, createXSql))) // Would fail if applied again
            // Verify:
            assertCanSelectFromX()
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

      }
    }

  }

}

object MigrationsImplSpec {

  /**
   * Create connection pool connected to a temporary database.
   */
  def createConnectionPool(): ConnectionPool = {
    // Generate connection pool name which is unlikely to collide with anything.
    // We use this as a way to catch mistakes where the wrong connection pool
    // is being accessed. This also prevents conflicts with other tests which
    // could run simultaneously.
    val connectionPoolName: String =
      UUID.randomUUID().toString

    // Create the database and connection pool
    val database =
      TempgresClient.createTemporaryDatabase(System.getProperty("tempgres.url", "http://tempgres:8080"))

    ConnectionPool.add(
      connectionPoolName,
      database.getUrl,
      database.getCredentials.getUserName,
      database.getCredentials.getPassword)

    // Reduce log spam.
    GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
      singleLineMode = true,
      logLevel = 'trace)

    // Return the connection pool
    ConnectionPool.get(connectionPoolName)
  }

}
