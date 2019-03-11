package dk.cwconsult.peregrin.core.impl

import java.util.UUID

private[impl] case class ChangeLogEntry(
  legacyIdentifier: Int,
  migrationId: Option[UUID],
  migrationParentId: Option[UUID],
  sql: String)

object ChangeLogEntry {

  sealed abstract class ChangeLogVersion(
    val versionInt: Int)

  object ChangeLogVersion {

    case object V1 extends ChangeLogVersion(1)
    case object V2 extends ChangeLogVersion(2)
    case object V3 extends ChangeLogVersion(3)

  }

}
