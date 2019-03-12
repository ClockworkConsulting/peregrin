package dk.cwconsult.peregrin.core.impl

import java.util.UUID

import dk.cwconsult.peregrin.core.InvalidMigrationSequenceException
import dk.cwconsult.peregrin.core.MigrationModifiedException
import dk.cwconsult.peregrin.core.Schema
import dk.cwconsult.peregrin.core.Migration
import dk.cwconsult.peregrin.core.test.Fixture
import org.scalatest.WordSpec

class MigrationsV3ImplSpec extends WordSpec {

  for (schema <- Vector(Schema.Public, Schema.Named(UUID.randomUUID.toString))) {
    val migrationId0 = UUID.randomUUID()
    val migrationId1 = UUID.randomUUID()
    val migrationId2 = UUID.randomUUID()

    "MigrationiV3.applyMigrations method" when {
      s"applied to schema $schema" should {

        "can apply a single migration" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Vector(
              Migration(migrationId0, createXSql)))
            // Verify
            assertCanSelectFromX()
          }
        }

        "abort if duplicate migrations are provided(single call)" in new Fixture(schema) {
          withTransaction { implicit session =>
            assertThrows[InvalidMigrationSequenceException] {
              // Exercise
              migrate(Vector(
                Migration(migrationId0, createXSql),
                Migration(migrationId0, createXSql))) // Causes aborted migration
            }
          }
        }

        "ignores migrations that have already been applied (multiple calls)" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Vector(
              Migration(migrationId0, createXSql)))
            migrate(Vector(
              Migration(migrationId0, createXSql))) // Would fail if applied again
            // Verify
            assertCanSelectFromX()
          }
        }

        "throws an exception if SQL is changes for a given change set ID (single call)" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            val exception = intercept[MigrationModifiedException] {
              migrate(Vector(
                Migration(migrationId0, createXSql),
                Migration(migrationId0, createXSqlBad)))
            }
            // Verify
            assert(exception.getMessage.contains("does not match"))
          }
        }

        "throws an exception if SQL is changed for a given change set ID (multiple calls)" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Vector(
              Migration(migrationId0, createXSql)))
            val exception = intercept[MigrationModifiedException] {
              migrate(Vector(
                Migration(migrationId0, createXSqlBad)))
            }
            // Verify
            assert(exception.getMessage.contains("does not match"))
          }
        }

        "can apply multiple distinct migrations in a single call" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Vector(
              Migration(migrationId0, createXSql),
              Migration(migrationId1, createYSql)))
            // Verify: Make sure both migrations have been applied
            assertCanSelectFromXY()
          }
        }

        "trim whitespace from beginning/end of SQL on insert" in new Fixture(schema) {
          withTransaction { implicit sessions =>
            // Exercise: Create the migration
            migrate(Vector(
              Migration(migrationId0, "\t    " + createXSql + "    ")
            ))
            // Verify
            val migrations = readMigrations()
            assert(migrations.head.legacyId === -1)
            assert(migrations.head.migrationId === Some(migrationId0))
            assert(!migrations.head.sql.head.isSpaceChar)
            assert(!migrations.head.sql.last.isSpaceChar)
            assertCanSelectFromX()
          }
        }

        "ignore differences in whitespace from beginning/end of SQL" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise: Create the 'base' migration
            migrate(Vector(
              Migration(migrationId0, createXSql)
            ))
            // Exercise: Add 'spurious' whitespace
            migrate(Vector(
              Migration(migrationId0, "  \t\n" + createXSql + "\r\n")
            ))
            // If we get here, we're OK
            succeed
          }
        }

        "have deterministic order of execution" in new Fixture(schema) {
          withTransaction { implicit session =>
            val migrationId2 = UUID.randomUUID()
            val migrationId3 = UUID.randomUUID()
            val migrationId4 = UUID.randomUUID()
            val migrationId5 = UUID.randomUUID()
            // Exercise
            migrate(Vector(
              Migration(migrationId0, createXSql),
              Migration(migrationId1, "ALTER TABLE X RENAME COLUMN A TO B"),
              Migration(migrationId2, "ALTER TABLE X RENAME COLUMN B TO C"),
              Migration(migrationId3, "ALTER TABLE X RENAME COLUMN C TO D"),
              Migration(migrationId4, "ALTER TABLE X RENAME COLUMN D TO E"),
              Migration(migrationId5, "ALTER TABLE X RENAME COLUMN E TO F")))
            // Verify: Make sure both migrations have been applied
            assertCanQuery("SELECT F FROM X")
          }
        }

      }
    }
  }

}
