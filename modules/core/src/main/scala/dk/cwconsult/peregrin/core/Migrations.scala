package dk.cwconsult.peregrin.core

import java.sql.Connection

import dk.cwconsult.peregrin.core
import dk.cwconsult.peregrin.core.impl.MigrationsImpl
import dk.cwconsult.peregrin.core.migrations.MigrationId.LegacyId
import dk.cwconsult.peregrin.core.{migrations => internal}

object Migrations {

  /**
    * Apply a list of migrations to the given database schema.
    *
    * Must all be of the same Migration definition format.
    */
  def applyMigrations(connection: Connection, schema: Schema, migrations: Seq[core.Migration]): AppliedMigrations = {
    applyMigrations(connection, schema,
      migrations.map(externalToInternal).toVector)
  }

  def applyMigrations(connection: Connection, schema: Schema, migrations: Vector[internal.Migration]): AppliedMigrations = {
    new MigrationsImpl(connection, schema).applyChangeLog(migrations)
  }

  private[this] def externalToInternal(migration: Migration): internal.Migration =
    internal.Migration.MigrationV1(
      identifier = LegacyId(migration.identifier),
      sql = migration.sql)

}
