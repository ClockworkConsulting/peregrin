package dk.cwconsult.peregrin.core.impl

import dk.cwconsult.peregrin.core.migrations.Migration
import dk.cwconsult.peregrin.core.migrations.Migration.MigrationV1
import dk.cwconsult.peregrin.core.migrations.Migration.MigrationV2
import dk.cwconsult.peregrin.core.migrations.Migration.MigrationV3

object ChangeLogResolver {

  case class ChangeLogEntries(
    unresolvedEntries: Seq[ChangeLogEntry],
    migrationsWithEntries: Seq[(Migration, Option[ChangeLogEntry])])

  def resolveChangeLogEntries(
    migrations: Seq[Migration],
    changeLogEntries: Seq[ChangeLogEntry]
  ): ChangeLogEntries = {
    // Perform mapping
    val mappings =
      for (migration <- migrations) yield {
        val maybeMatchingChangeLog =
          changeLogEntries.find(correspondsTo(migration, _))
        (migration, maybeMatchingChangeLog)
      }
    // Resolve changelog entries that were not resolved
    val unresolved =
      changeLogEntries.filterNot(e => mappings.exists(_._2.contains(e)))
    // Return result
    ChangeLogEntries(
      unresolvedEntries = unresolved,
      migrationsWithEntries = mappings)
  }

  private[this] def correspondsTo(m: Migration, changeLogEntryRow: ChangeLogEntry): Boolean =
    m match {
      case v1: MigrationV1 =>
        v1.identifier.legacyId == changeLogEntryRow.legacyIdentifier
      case v2: MigrationV2 =>
        v2.legacyId.legacyId == changeLogEntryRow.legacyIdentifier ||
          changeLogEntryRow.migrationId.contains(v2.identifier.id)
      case v3: MigrationV3 =>
        changeLogEntryRow.migrationId.contains(v3.identifier.id)
    }

}
