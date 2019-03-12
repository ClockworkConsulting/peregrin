package dk.cwconsult.peregrin.core.migrations

import java.util.UUID

import dk.cwconsult.peregrin.core.migrations.MigrationId.LegacyId
import dk.cwconsult.peregrin.core.migrations.MigrationId.UniqueIdentifier

/**
 * Migration step.
 */
sealed trait Migration {

  def identifier: MigrationId

  def legacyIdentifier: Option[LegacyId]

  def sql: String

}

object Migration {

  case class MigrationV1(
    override val identifier: LegacyId,
    override val sql: String) extends Migration {
    override val legacyIdentifier: Option[LegacyId] = Some(identifier)
  }

  case class MigrationV2(
    legacyId: LegacyId,
    override val identifier: UniqueIdentifier,
    override val sql: String) extends Migration {
    override val legacyIdentifier: Option[LegacyId] = Some(legacyId)
  }

  case class MigrationV3(
    override val identifier: UniqueIdentifier,
    override val sql: String) extends Migration {
    override val legacyIdentifier: Option[LegacyId] = None
  }

  def apply(identifier: Int, sql: String): Migration =
    MigrationV1(
      identifier = LegacyId(identifier),
      sql = sql)

  def apply(uuid: String, sql: String): Migration =
    apply(UUID.fromString(uuid), sql)

  def apply(uuid: UUID, sql: String): Migration =
    MigrationV3(
      identifier = MigrationId.UniqueIdentifier(
        id = uuid),
      sql = sql)

  def apply(legacyIdentifier: Int, uuid: UUID, sql: String): Migration =
    MigrationV2(
      legacyId = LegacyId(legacyIdentifier),
      identifier = MigrationId.UniqueIdentifier(
        id = uuid),
      sql = sql)

}
