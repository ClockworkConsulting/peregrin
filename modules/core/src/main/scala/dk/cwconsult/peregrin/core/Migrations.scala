package dk.cwconsult.peregrin.core

import java.sql.Connection

import dk.cwconsult.peregrin.core.impl.MigrationsImpl

object Migrations {

  /**
    * Apply a list of migrations to the given database schema.
    *
    * Must all be of the same Migration definition format.
    */
  def applyMigrations(connection: Connection, schema: Schema, migrations: Seq[Migration]): AppliedMigrations = {
    new MigrationsImpl(connection, schema).applyChangeLog(migrations.toVector)
  }

}
