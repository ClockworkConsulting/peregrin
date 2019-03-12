package dk.cwconsult.peregrin.core.migrations

import java.util.UUID

sealed trait MigrationId

object MigrationId {

  case class LegacyId(
    legacyId: Int)
    extends MigrationId

  case class UniqueIdentifier(
    id: UUID)
    extends MigrationId

}
