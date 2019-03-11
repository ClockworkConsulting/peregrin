package dk.cwconsult.peregrin.core.impl

import dk.cwconsult.peregrin.core.Migration
import dk.cwconsult.peregrin.core.Migration.MigrationV1
import dk.cwconsult.peregrin.core.Migration.MigrationV2
import dk.cwconsult.peregrin.core.Migration.MigrationV3

object ChangeLogIdentity {

  def correspondsTo(m: Migration, changeLogEntryRow: ChangeLogEntry): Boolean =
    m match {

      case v1: MigrationV1 =>
        v1.identifier.legacyId == changeLogEntryRow.legacyIdentifier

      case v2: MigrationV2 =>
        v2.legacyId.legacyId == changeLogEntryRow.legacyIdentifier || (
          changeLogEntryRow.migrationId.contains(v2.identifier.id) &&
          changeLogEntryRow.migrationParentId == v2.identifier.parentId)

      case v3: MigrationV3 =>
        changeLogEntryRow.migrationId.contains(v3.identifier.id) &&
        changeLogEntryRow.migrationParentId == v3.identifier.parentId

    }

}
