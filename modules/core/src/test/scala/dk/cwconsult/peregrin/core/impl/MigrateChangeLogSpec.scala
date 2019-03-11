package dk.cwconsult.peregrin.core.impl

import java.util.UUID

import dk.cwconsult.peregrin.core.InvalidMigrationSequenceException
import dk.cwconsult.peregrin.core.Migration
import dk.cwconsult.peregrin.core.Schema
import dk.cwconsult.peregrin.core.UnresolvedChangeLogEntriesFoundException
import dk.cwconsult.peregrin.core.test.Fixture
import org.scalatest.WordSpec

class MigrateChangeLogSpec extends WordSpec {

  private[this] val migrationId0 = UUID.randomUUID()


  for (schema <- Vector(Schema.Public, Schema.Named(UUID.randomUUID.toString))) {

    "Migration.applyMigrations method" when {
      s"applied to schema $schema" should {

        "allow migration of an empty sequence" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            migrate(Seq.empty)
            // Verify: no change logs
            assert(readMigrations().isEmpty)
          }
        }

        "throws an exception if a migration is missing a legacy id, if provided for one" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            val exception = intercept[InvalidMigrationSequenceException] {
              migrate(Seq(
                Migration(0, createXSql),
                Migration(migrationId0, None, createYSql)))
            }
            // Verify
            assert(exception.getMessage.contains("MUST have a common identifier type defined"))
          }
        }

        "throws an exception if a migration is missing a child-parent relation, if provided for one" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Exercise
            val exception = intercept[InvalidMigrationSequenceException] {
              migrate(Seq(
                Migration(0, createXSql),
                Migration(1, migrationId0, None, createYSql)))
            }
            // Verify
            assert(exception.getMessage.contains("ALL MUST be given a UUID identifier"))
          }
        }

        "migrate changelog V1 entries to V2" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Setup: Prepare changelog db with a V1 entry
            migrate(Seq(
              Migration(0, createXSql)))

            // Setup: Verify DB contains V1 entry
            val v1Migrations = readMigrations()
            assert(v1Migrations.nonEmpty)
            assert(v1Migrations.head.legacyId === 0)
            assert(v1Migrations.head.migrationId === None)
            assert(v1Migrations.head.migrationParentId === None)
            assertChangeLogVersionIs(1)

            // Setup: Verify existence of X
            assertCanSelectFromX()

            // Exercise: Migrate change log entry to V2 format
            migrate(Seq(
              Migration(0, migrationId0, None, createXSql)))

            // Verify
            val migrations = readMigrations()
            assert(migrations.length === 1)
            assert(migrations.head.legacyId === 0)
            assert(migrations.head.migrationId === Some(migrationId0))
            assert(migrations.head.migrationParentId === None)
            assertChangeLogVersionIs(2)

          }
        }

        "migrate changelog V2 entries to V3" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Setup: Prepare changelog db with a V3 entry
            migrate(Seq(
              Migration(0, migrationId0, None, createXSql)))

            // Setup: Verify DB contains V2 entry
            val v1Migrations = readMigrations()
            assert(v1Migrations.length === 1)
            assert(v1Migrations.head.legacyId === 0)
            assert(v1Migrations.head.migrationId === Some(migrationId0))
            assert(v1Migrations.head.migrationParentId === None)
            assertChangeLogVersionIs(2)

            // Setup: Verify existence of X
            assertCanSelectFromX()

            // Exercise: Migrate change log entry to V3 format
            migrate(Seq(
              Migration(migrationId0, None, createXSql)))

            // Setup: Verify DB contains V2 entry
            val migrations = readMigrations()
            assert(migrations.length === 1)
            assert(migrations.head.legacyId === 0)
            assert(migrations.head.migrationId === Some(migrationId0))
            assert(migrations.head.migrationParentId === None)
            assertChangeLogVersionIs(3)

          }
        }

        "migrate changelog V1 entries to V2 and V3" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Setup: Prepare changelog db with a V1 entry
            migrate(Seq(
              Migration(0, createXSql)))

            // Setup: Verify DB contains V1 entry
            val v1Migrations = readMigrations()
            assert(v1Migrations.nonEmpty)
            assert(v1Migrations.head.legacyId === 0)
            assert(v1Migrations.head.migrationId === None)
            assert(v1Migrations.head.migrationParentId === None)
            assertChangeLogVersionIs(1)

            // Setup: Verify existence of X
            assertCanSelectFromX()

            // Exercise: Migrate change log entry to V2 format
            migrate(Seq(
              Migration(0, migrationId0, None, createXSql)))

            // Verify migration to V2
            val v2Migrations = readMigrations()
            assert(v2Migrations.length === 1)
            assert(v2Migrations.head.legacyId === 0)
            assert(v2Migrations.head.migrationId === Some(migrationId0))
            assert(v2Migrations.head.migrationParentId === None)
            assertChangeLogVersionIs(2)

            // Exercise: Migrate change log entry to V3 format
            migrate(Seq(
              Migration(migrationId0, None, createXSql)))

            // Setup: Verify DB contains V3 entry
            val migrations = readMigrations()
            assert(migrations.length === 1)
            assert(migrations.head.legacyId === 0)
            assert(migrations.head.migrationId === Some(migrationId0))
            assert(migrations.head.migrationParentId === None)
            assertChangeLogVersionIs(3)
          }
        }

        "abort migration from changelog V1 entries directly to V3" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Setup: Prepare changelog db with a V1 entry
            migrate(Seq(
              Migration(0, createXSql)))

            // Setup: Verify DB contains V1 entry
            val v1Migrations = readMigrations()
            assert(v1Migrations.nonEmpty)
            assert(v1Migrations.head.legacyId === 0)
            assert(v1Migrations.head.migrationId === None)
            assert(v1Migrations.head.migrationParentId === None)
            assertChangeLogVersionIs(1)

            // Setup: Verify existence of X
            assertCanSelectFromX()

            // Exercise: Attempt to migrate directly to V3 format
            val exception = intercept[UnresolvedChangeLogEntriesFoundException] {
              migrate(Seq(
                Migration(migrationId0, None, createXSql)))
            }
            assert(exception.getMessage.contains("MUST include all known change log entries"))
          }
        }

        "migrate changelog V3 entries to V2 and V1" in new Fixture(schema) {
          withTransaction { implicit session =>
            // Setup: Prepare changelog db with a V3 entry
            migrate(Seq(
              Migration(migrationId0, None, createXSql)))

            // Setup: Verify DB contains V3 entry
            val v3Migrations = readMigrations()
            assert(v3Migrations.nonEmpty)
            assert(v3Migrations.head.legacyId === -1)
            assert(v3Migrations.head.migrationId === Some(migrationId0))
            assert(v3Migrations.head.migrationParentId === None)
            assertChangeLogVersionIs(3)

            // Setup: Verify existence of X
            assertCanSelectFromX()

            // Exercise: Migrate change log entry to V2 format
            migrate(Seq(
              Migration(0, migrationId0, None, createXSql)))

            // Verify migration to V2
            val v2Migrations = readMigrations()
            assert(v2Migrations.length === 1)
            assert(v2Migrations.head.legacyId === 0)
            assert(v2Migrations.head.migrationId === Some(migrationId0))
            assert(v2Migrations.head.migrationParentId === None)
            assertChangeLogVersionIs(3)

            // Exercise: Migrate change log entry to V1 format
            migrate(Seq(
              Migration(0, createXSql)))

            // Verify: Verify DB contains V3 entry with V1 info
            val migrations = readMigrations()
            assert(migrations.length === 1)
            assert(migrations.head.legacyId === 0)
            assert(migrations.head.migrationId === Some(migrationId0))
            assert(migrations.head.migrationParentId === None)
            assertChangeLogVersionIs(3)
          }
        }


      }
    }

  }

}
