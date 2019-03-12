package dk.cwconsult.peregrin.core

import java.util.UUID

import dk.cwconsult.peregrin.core.{migrations => internal}

/**
  * Migration step.
  */
case class Migration(
  identifier: Int,
  sql: String)

object Migration {

  def apply(uuid: String, sql: String): internal.Migration =
    internal.Migration(uuid = uuid, sql = sql)

  def apply(uuid: UUID, sql: String): internal.Migration =
    internal.Migration(uuid, sql)

  def apply(legacyIdentifier: Int, uuid: UUID, sql: String): internal.Migration =
    internal.Migration(legacyIdentifier, uuid, sql)

}
