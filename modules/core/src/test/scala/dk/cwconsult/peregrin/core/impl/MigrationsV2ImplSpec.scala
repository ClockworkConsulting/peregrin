package dk.cwconsult.peregrin.core.impl

import java.util.UUID

import dk.cwconsult.peregrin.core.DisjointMigrationsException
import dk.cwconsult.peregrin.core.InvalidMigrationSequenceException
import dk.cwconsult.peregrin.core.MigrationModifiedException
import dk.cwconsult.peregrin.core.Schema
import dk.cwconsult.peregrin.core.Migration
import dk.cwconsult.peregrin.core.test.Fixture
import org.scalatest.WordSpec

class MigrationsV2ImplSpec extends WordSpec {

  for (schema <- Vector(Schema.Public, Schema.Named(UUID.randomUUID.toString))) {

    val migrationId0 = UUID.randomUUID()
    val migrationId1 = UUID.randomUUID()
    val migrationId2 = UUID.randomUUID()

    "MigrationV2.applyMigrations method" when {
      s"applied to schema $schema" should {

        "can apply a single migration" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Vector(
              Migration(0, migrationId0, createXSql)))
            // Verify
            assertCanSelectFromX()
          }
        }

        "abort if duplicate migrations are provided(single call)" in new Fixture(schema) {
          withTransaction { implicit session =>
            assertThrows[InvalidMigrationSequenceException] {
              // Exercise
              migrate(Vector(
                Migration(0, migrationId0, createXSql),
                Migration(0, migrationId0, createXSql))) // Causes aborted migration
            }
          }
        }

        "ignores migrations that have already been applied (multiple calls)" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Vector(
              Migration(0, migrationId0, createXSql)))
            migrate(Vector(
              Migration(0, migrationId0, createXSql))) // Would fail if applied again
            // Verify
            assertCanSelectFromX()
          }
        }

        "throws an exception if SQL is changes for a given change set ID (single call)" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            val exception = intercept[MigrationModifiedException] {
              migrate(Vector(
                Migration(0, migrationId0, createXSql),
                Migration(0, migrationId0, createXSqlBad)))
            }
            // Verify
            assert(exception.getMessage.contains("does not match"))
          }
        }

        "throws an exception if SQL is changed for a given change set ID (multiple calls)" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Vector(
              Migration(0, migrationId0, createXSql)))
            val exception = intercept[MigrationModifiedException] {
              migrate(Vector(
                Migration(0, migrationId0, createXSqlBad)))
            }
            // Verify
            assert(exception.getMessage.contains("does not match"))
          }
        }

        "can apply multiple distinct migrations in a single call" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Vector(
              Migration(0, migrationId0, createXSql),
              Migration(1, migrationId1, createYSql)))
            // Verify: Make sure both migrations have been applied
            assertCanSelectFromXY()
          }
        }

        "throws an exception for sequences of migrations that do not start at 0" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            val exception = intercept[DisjointMigrationsException] {
              migrate(Vector(
                Migration(1, migrationId0, createXSql),
                Migration(2, migrationId1, createYSql)))
            }
            // Verify
            assert(exception.getMessage.contains("MUST be contiguous"))
          }
        }

        "throws an exception for non-contiguous sequences of migrations" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            val exception = intercept[DisjointMigrationsException] {
              migrate(Vector(
                Migration(0, migrationId0, createXSql),
                Migration(2, migrationId1, createYSql)))
            }
            // Verify
            assert(exception.getMessage.contains("MUST be contiguous"))
          }
        }

        "trim whitespace from beginning/end of SQL on insert" in new Fixture(schema) {
          withTransaction { implicit sessions =>
            // Exercise: Create the migration
            migrate(Vector(
              Migration(0, migrationId0, "\t    " + createXSql + "    ")
            ))
            // Verify
            val migrations = readMigrations()
            assert(migrations.head.legacyId === 0)
            assert(!migrations.head.sql.head.isSpaceChar)
            assert(!migrations.head.sql.last.isSpaceChar)
          }
        }

        "ignore differences in whitespace from beginning/end of SQL" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise: Create the 'base' migration
            migrate(Vector(
              Migration(0, migrationId0, createXSql)
            ))
            // Exercise: Add 'spurious' whitespace
            migrate(Vector(
              Migration(0, migrationId0, "  \t\n" + createXSql + "\r\n")
            ))
            // If we get here, we're OK
            succeed
          }
        }

      }
    }

  }

}
