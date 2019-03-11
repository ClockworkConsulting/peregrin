package dk.cwconsult.peregrin.core.impl

import java.util.UUID

import dk.cwconsult.peregrin.core.DisjointMigrationsException
import dk.cwconsult.peregrin.core.InvalidMigrationSequenceException
import dk.cwconsult.peregrin.core.Migration
import dk.cwconsult.peregrin.core.MigrationModifiedException
import dk.cwconsult.peregrin.core.Schema
import dk.cwconsult.peregrin.core.test.Fixture
import org.scalatest.WordSpec

class MigrationsV3ImplSpec extends WordSpec {

  for (schema <- Vector(Schema.Public, Schema.Named(UUID.randomUUID.toString))) {

    val migrationId0 = UUID.randomUUID()
    val migrationId1 = UUID.randomUUID()
    val migrationId2 = UUID.randomUUID()

    "MigrationiV3.applyMigrations method" when {
      s"applied to schema $schema" should {

        "require at least one root node" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            val exception = intercept[InvalidMigrationSequenceException] {
              migrate(Seq(
                Migration(migrationId1, Some(migrationId0), createXSql)))
            }
            // Verify
            assert(exception.getMessage.contains("MUST contain at least one root node"))
          }
        }

        "can apply a single migration" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Seq(
              Migration(migrationId0, None, createXSql)))
            // Verify
            assertCanSelectFromX()
          }
        }

        "abort if duplicate migrations are provided(single call)" in new Fixture(schema) {
          withTransaction { implicit session =>
            assertThrows[InvalidMigrationSequenceException] {
              // Exercise
              migrate(Seq(
                Migration(migrationId0, None, createXSql),
                Migration(migrationId0, None, createXSql))) // Causes aborted migration
            }
          }
        }

        "ignores migrations that have already been applied (multiple calls)" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Seq(
              Migration(migrationId0, None, createXSql)))
            migrate(Seq(
              Migration(migrationId0, None, createXSql))) // Would fail if applied again
            // Verify
            assertCanSelectFromX()
          }
        }

        "throws an exception if SQL is changes for a given change set ID (single call)" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            val exception = intercept[MigrationModifiedException] {
              migrate(Seq(
                Migration(migrationId0, None, createXSql),
                Migration(migrationId0, None, createXSqlBad)))
            }
            // Verify
            assert(exception.getMessage.contains("does not match"))
          }
        }

        "throws an exception if SQL is changed for a given change set ID (multiple calls)" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Seq(
              Migration(migrationId0, None, createXSql)))
            val exception = intercept[MigrationModifiedException] {
              migrate(Seq(
                Migration(migrationId0, None, createXSqlBad)))
            }
            // Verify
            assert(exception.getMessage.contains("does not match"))
          }
        }

        "can apply multiple distinct migrations in a single call" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Seq(
              Migration(migrationId0, None, createXSql),
              Migration(migrationId1, Some(migrationId0), createYSql)))
            // Verify: Make sure both migrations have been applied
            assertCanSelectFromXY()
          }
        }

        "throws an exception for sequences with references to unknown migrations" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            val exception = intercept[InvalidMigrationSequenceException] {
              migrate(Seq(
                Migration(migrationId0, None, createXSql),
                Migration(migrationId1, Some(migrationId2), createYSql)))
            }
            // Verify
            assert(exception.getMessage.contains("MUST reference valid parents"))
          }
        }

        "trim whitespace from beginning/end of SQL on insert" in new Fixture(schema) {
          withTransaction { implicit sessions =>
            // Exercise: Create the migration
            migrate(Seq(
              Migration(migrationId0, None, "\t    " + createXSql + "    ")
            ))
            // Verify
            val migrations = readMigrations()
            assert(migrations.head.legacyId === -1)
            assert(migrations.head.migrationId === Some(migrationId0))
            assert(migrations.head.migrationParentId === None)
            assert(!migrations.head.sql.head.isSpaceChar)
            assert(!migrations.head.sql.last.isSpaceChar)
            assertCanSelectFromX()
          }
        }

        "ignore differences in whitespace from beginning/end of SQL" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise: Create the 'base' migration
            migrate(Seq(
              Migration(migrationId0, None, createXSql)
            ))
            // Exercise: Add 'spurious' whitespace
            migrate(Seq(
              Migration(migrationId0, None, "  \t\n" + createXSql + "\r\n")
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
            migrate(Seq(
              Migration(migrationId0, None, createXSql),
              Migration(migrationId1, Some(migrationId0),
                "ALTER TABLE X RENAME COLUMN A TO B"),
              Migration(migrationId2, Some(migrationId0),
                "ALTER TABLE X RENAME COLUMN B TO C"),
              Migration(migrationId3, Some(migrationId0),
                "ALTER TABLE X RENAME COLUMN C TO D"),

              // This should be performed last, because it's
              // deepest in the tree
              Migration(migrationId4, Some(migrationId2),
                "ALTER TABLE X RENAME COLUMN E TO F"),

              Migration(migrationId5, Some(migrationId0),
                "ALTER TABLE X RENAME COLUMN D TO E")))
            // Verify: Make sure both migrations have been applied
            assertCanQuery("SELECT F FROM X")
          }
        }

        "report any cycles in migration order as unresolved migrations" in new Fixture(schema) {
          withTransaction { implicit session =>
            val migrationId2 = UUID.randomUUID()
            val migrationId3 = UUID.randomUUID()
            val migrationId4 = UUID.randomUUID()
            // Exercise
            val exception = intercept[InvalidMigrationSequenceException] {
              migrate(Seq(
                Migration(migrationId0, None, createXSql),
                Migration(migrationId2, Some(migrationId3), createTableSql("A")),
                Migration(migrationId3, Some(migrationId2), createTableSql("B")),
                Migration(migrationId4, Some(migrationId0), createYSql)
              ))
            }
            assert(exception.getMessage.contains("Do you have a cycle?"))
          }
        }

      }
    }

  }

}
