package dk.cwconsult.peregrin.core.impl

import java.sql.Connection

import dk.cwconsult.peregrin.core.DisjointMigrationsException
import dk.cwconsult.peregrin.core.Migration
import dk.cwconsult.peregrin.core.MigrationModifiedException
import dk.cwconsult.peregrin.core.AppliedMigrations
import dk.cwconsult.peregrin.core.InvalidMigrationSequenceException
import dk.cwconsult.peregrin.core.Schema
import dk.cwconsult.peregrin.core.Table
import dk.cwconsult.peregrin.core.impl.ConnectionImplicits._

import scala.collection.mutable.ArrayBuffer

/**
 * Main class for performing migrations.
 */
private[peregrin] class MigrationsImpl(connection: Connection, schema: Schema) {

  private[this] val changeLogTable =
    Table(
      name = "__peregrin_changelog__",
      schema)

  /**
    * Create change log if necessary.
    */
  private[this] def createChangeLogIfMissing(): Unit = {
    // Create the schema if necessary.
    connection.execute(s"CREATE SCHEMA IF NOT EXISTS $schema")
    // Create the migration log table.
    val _ = connection.execute(s"""
        CREATE TABLE IF NOT EXISTS $changeLogTable (
          "identifier" INT NOT NULL,
          "sql" TEXT NOT NULL,
          "executed" TIMESTAMP WITH TIME ZONE NOT NULL)
      """)
  }

  /**
    * Read a changelog.
    */
  private[this] def readChangeLogEntries(): Vector[ChangeLogEntry] = {
    connection.executeQuery(s"""SELECT "identifier", "sql" FROM $changeLogTable ORDER BY "identifier" ASC""") { resultSet =>
      // Extract all the results
      val rows = new ArrayBuffer[ChangeLogEntry]()
      while (resultSet.next()) {
        rows += ChangeLogEntry(
          identifier = resultSet.getInt(1),
          sql = resultSet.getString(2))
      }
      rows.toVector
    }
  }

  private[this] def insertChangeLogEntry(changeLogEntry: ChangeLogEntry): Unit = {
    // Insert the entry.
    val insertCount = connection.executeUpdatePrepared(s"""INSERT INTO $changeLogTable ("identifier", "sql", "executed") VALUES (?, ?, now())""") { stmt =>
      stmt.setInt(1, changeLogEntry.identifier)
      stmt.setString(2, changeLogEntry.sql)
    }
    // Sanity check: Must have inserted exactly one row.
    if (insertCount != 1) {
      throw new IllegalStateException(s"Internal consistency error: No rows inserted")
    }
  }

  private[this] def applyMigration(changeLogEntry: ChangeLogEntry): ChangeLogEntry = {
    // Perform the migration. We assume that all statements are "updates", i.e.
    // either UPDATE/INSERT/etc. or DDL statements.
    connection.executeUpdate(changeLogEntry.sql)
    // Insert the change log entry.
    insertChangeLogEntry(changeLogEntry)
    // Return applied changeLogEntry
    changeLogEntry
  }

  /**
    * Throw a "SQL does not match" error.
    */
  private[this] def throwDoesNotMatch(existingSql: String, newSql: String): Unit = {
    val message =
      s"""
         |SQL in existing migration does not match new migration.
         |
         |Existing: [$existingSql]
         |
         |New: [$newSql]
       """.stripMargin
    throw new MigrationModifiedException(message)
  }

  /**
    * Trim leading/trailing whitespace.
    */
  private[this] def trimSql(sql: String): String =
    sql.trim

  /**
    * Verify stored checksums of existing migrations and return the
    * list of migrations that have yet to be performed.
    */
  private[this] def verifyChangeLogEntries(migrations: Vector[Migration]): Vector[ChangeLogEntry] = {
    // Compute map all the migrations to ChangeLogEntry so that we have
    // checksums, etc. to compare against.
    val inputChangeLogEntries = migrations.map { migration =>
      ChangeLogEntry(
        identifier = migration.identifier,
        sql = migration.sql)
    }
    // Load up all the existing change log
    val existingChangeLogEntries = readChangeLogEntries()
    // Make sure existing entries match. Our assumption here
    // is that the given input migrations are in sorted order
    // and start at "identifier == 1".
    for (correspondingChangeLogEntries <- existingChangeLogEntries.zip(inputChangeLogEntries)) {
      val existingChangeLogEntry = correspondingChangeLogEntries._1
      val inputChangeLogEntry = correspondingChangeLogEntries._2
      // Check that the SQL matches. We need to explicitly trim existing
      // SQL entries since they may have been inserted before we started
      // trimming input migration SQL.
      if (trimSql(existingChangeLogEntry.sql) != inputChangeLogEntry.sql) {
        throwDoesNotMatch(
          existingSql = existingChangeLogEntry.sql,
          newSql = inputChangeLogEntry.sql)
      }
    }
    // Everything matches up to the list of existing entries,
    // so we just need to apply the remainder; we can get that
    // by just dropping the prefix (i.e. the existing entries).
    inputChangeLogEntries.drop(existingChangeLogEntries.size)
  }

  /**
    * Run the given block in with a (transactional) lock.
    */
  private[this] def withChangeLogLock[A](block: => A): A = {
    connection.execute(s"LOCK $changeLogTable IN EXCLUSIVE MODE")
    try {
      block
    } finally {
      // Nothing to do; the lock will automatically be released at the end
      // of the transaction.
    }
  }

  /**
    * Verify changelog IDs are contiguous. We assume that the input
    * list is sorted by identifier.
    */
  private[this] def isContiguous(migrations: Vector[Migration]): Boolean = {
    // Expected identifiers are [0..]
    val expectedIdentifiers = 0 until migrations.length
    // Check each migration against its expected index.
    migrations.zip(expectedIdentifiers).forall {
      case (migration, expectedIdentifier) =>
        migration.identifier == expectedIdentifier
    }
  }

  /**
    * Check for inconsistent duplicate migrations in input and remove
    * all consistent duplicates. This is for consistency
    * with what happens if you run the same migration against a database
    * over multiple calls to [[applyChangeLog()]] (as opposed to doing it
    * in a single call -- which is the case this is meant for handling).
    *
    * We assume that the input list is sorted by identifier.
    */
  private[this] def removeDuplicates(migrations: Vector[Migration]): Vector[Migration] = {
    // Check that all adjacent "duplicates" are in fact identical. By the
    // "sorted" property we know that all duplicates will be adjacent, and
    // by the transitive property of equality we know that once we're through
    // this loop *all* migrations with a given identifier must have identical
    // SQL.
    migrations.zip(migrations.drop(1)).foreach {
      case (existingMigration, newMigration) =>
        if (existingMigration.identifier == newMigration.identifier) {
          if (existingMigration.sql != newMigration.sql) {
            throwDoesNotMatch(
              existingSql = existingMigration.sql,
              newSql = newMigration.sql)
          }
        }
    }
    // Remove the duplicates.
    migrations.distinct
  }

  /**
   * Apply change log to the database accessible through the given connection.
   */
  def applyChangeLog(_migrations: Vector[Migration]): AppliedMigrations = {
    // Trim all input SQL.
    val trimmedMigrations = _migrations.map(m => m.copy(sql = trimSql(m.sql)))
    // Sanitize input
    val allMigrations = removeDuplicates(trimmedMigrations
      // Make sure the migrations are in sorted order.
      .sortBy(_.identifier))
    // Make sure that all the identifiers are contiguous.
    if (!isContiguous(allMigrations)) {
      throw new DisjointMigrationsException(
        s"Identifiers for migrations MUST be contiguous and start at 0")
    }
    // Make sure nothing changed with duplicate-removal, and sorting
    if (allMigrations != trimmedMigrations) {
      throw new InvalidMigrationSequenceException(
        s"Provided migrations MUST be ordered by identifier, and contain no duplicates")
    }
    // Disable auto-commit; we absolutely cannot have commits at "random" points
    // during the migration.
    connection.setAutoCommit(false)
    // Create the change log itself (if missing). This is an atomic
    // and idempotent operation, so we don't need a lock here; indeed
    // we could not possibly take one since we may not actually have
    // a table to lock.
    createChangeLogIfMissing()
    // Commit
    connection.commit()
    // Everything else needs the lock; note that this means that
    // exactly one process will get to perform the operations while
    // the others wait and will discover that updates have already
    // been performed.
    val appliedChangeLogEntries: Vector[ChangeLogEntry] =
      withChangeLogLock {
        // We verify all the change log entries and select which
        // have yet to be applied.
        val changeLogEntriesToApply = verifyChangeLogEntries(allMigrations)
        // Apply everything from there and up.
        changeLogEntriesToApply.map(applyMigration)
      }
    // Commit all changes
    connection.commit()
    // Return migration result
    AppliedMigrations(
      migrations = appliedChangeLogEntries
        .map { cl =>
          Migration(
            identifier = cl.identifier,
            sql = cl.sql)
        })
  }

}
