package dk.cwconsult.peregrin.core

import java.util.UUID

import dk.cwconsult.peregrin.core.migrations.MigrationId
import dk.cwconsult.peregrin.core.migrations.MigrationId.ChildParentRelation
import dk.cwconsult.peregrin.core.migrations.MigrationId.LegacyId

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
    override val identifier: ChildParentRelation,
    override val sql: String) extends Migration {
    override val legacyIdentifier: Option[LegacyId] = Some(legacyId)
  }

  case class MigrationV3(
    override val identifier: ChildParentRelation,
    override val sql: String) extends Migration {
    override val legacyIdentifier: Option[LegacyId] = None
  }

  def apply(identifier: Int, sql: String): Migration =
    MigrationV1(
      identifier = LegacyId(identifier),
      sql = sql)

  def apply(uuid: UUID, parentId: Option[UUID], sql: String): Migration =
    MigrationV3(
      identifier = MigrationId.ChildParentRelation(
        id = uuid,
        parentId = parentId),
      sql = sql)

  def apply(legacyIdentifier: Int, uuid: UUID, parentId: Option[UUID], sql: String): Migration =
    MigrationV2(
      legacyId = LegacyId(legacyIdentifier),
      identifier = ChildParentRelation(
        id = uuid,
        parentId = parentId),
      sql = sql)

}
