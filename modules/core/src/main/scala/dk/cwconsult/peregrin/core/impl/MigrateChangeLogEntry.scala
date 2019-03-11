package dk.cwconsult.peregrin.core.impl

import dk.cwconsult.peregrin.core.Migration
import dk.cwconsult.peregrin.core.Migration.MigrationV1
import dk.cwconsult.peregrin.core.Migration.MigrationV2
import dk.cwconsult.peregrin.core.Migration.MigrationV3
import dk.cwconsult.peregrin.core.impl.ChangeLogEntry.ChangeLogVersion

/**
  * Migrate a given ChangeLogEntry to the used Migration version.
  *
  * In down-grade scenarios, new information is augmented to the
  * entry, but the version identifier will not be decremented.
  *
  * This means, that a migration from V3 to V1 is possible, as long
  * as V2 is used as an intermediate step. But the entry in the
  * change log, will still declare itself as a V3 compatible entry.
  */
object MigrateChangeLogEntry {

  private[this] def migrateChangeLog(version: ChangeLogVersion, changeLogEntry: ChangeLogEntry, migration: MigrationV1): Either[String, ChangeLogEntry] =
    // Do nothing
    version match {
      case ChangeLogVersion.V1 =>
        // Already at expected version
        Right(changeLogEntry)
      case ChangeLogVersion.V2 =>
        // No migration needed, preserve version
        Right(changeLogEntry)
      case ChangeLogVersion.V3 =>
        if (changeLogEntry.legacyIdentifier != -1) {
          Right(changeLogEntry)
        } else {
          Left(s"Unable to migrate from V3 to V1 directly")
        }
    }

  private[this] def migrateChangeLog(version: ChangeLogVersion,changeLogEntry: ChangeLogEntry, migration: MigrationV2): Either[String, ChangeLogEntry] =
    version match {
      case ChangeLogVersion.V1 =>
        // Migrate from version 1 to 2
        Right(changeLogEntry.copy(
          migrationId = Some(migration.identifier.id),
          migrationParentId = migration.identifier.parentId))
      case ChangeLogVersion.V2 =>
        // No migration needed, already expected version
        Right(changeLogEntry)
      case ChangeLogVersion.V3 =>
        // Add new information, but preserve version,
        // allowing further downgrade to V1
        Right(changeLogEntry.copy(
          legacyIdentifier = migration.legacyId.legacyId))
    }

  private[this] def migrateChangeLog(version: ChangeLogVersion, changeLogEntry: ChangeLogEntry, migration: MigrationV3): Either[String, ChangeLogEntry] =
    version match {
      case ChangeLogVersion.V1 =>
        Left(s"Unable to migrate from V1 to V3 directly")
      case ChangeLogVersion.V2 =>
        // V3 only drops information of old id
        Right(changeLogEntry)
      case ChangeLogVersion.V3 =>
        // No migration needed, already at expected version
        Right(changeLogEntry)
    }

  def migrateChangeLogEntry(
    version: ChangeLogVersion,
    changeLogEntry: ChangeLogEntry,
    migration: Migration
  ): Either[String, ChangeLogEntry] =
    migration match {
      case v: MigrationV1 => migrateChangeLog(version, changeLogEntry, v)
      case v: MigrationV2 => migrateChangeLog(version, changeLogEntry, v)
      case v: MigrationV3 => migrateChangeLog(version, changeLogEntry, v)
    }

}
