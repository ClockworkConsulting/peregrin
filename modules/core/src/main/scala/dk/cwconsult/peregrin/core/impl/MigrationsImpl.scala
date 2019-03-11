package dk.cwconsult.peregrin.core.impl

import java.sql.Connection

import dk.cwconsult.peregrin.core.AppliedMigrations
import dk.cwconsult.peregrin.core.DisjointMigrationsException
import dk.cwconsult.peregrin.core.ImpossibleChangeLogMigrationException
import dk.cwconsult.peregrin.core.InvalidMigrationSequenceException
import dk.cwconsult.peregrin.core.Migration
import dk.cwconsult.peregrin.core.MigrationModifiedException
import dk.cwconsult.peregrin.core.Schema
import dk.cwconsult.peregrin.core.UnresolvedChangeLogEntriesFoundException
import dk.cwconsult.peregrin.core.impl.ChangeLogEntry.ChangeLogVersion
import dk.cwconsult.peregrin.core.impl.ConnectionImplicits._
import dk.cwconsult.peregrin.core.impl.dao.ChangeLogDAO
import dk.cwconsult.peregrin.core.impl.dao.ChangeLogDAOImpl
import dk.cwconsult.peregrin.core.impl.dao.ChangeLogMetaDataDAO
import dk.cwconsult.peregrin.core.impl.dao.ChangeLogMetaDataDAOImpl

/**
 * Main class for performing migrations.
 */
private[peregrin] class MigrationsImpl(connection: Connection, schema: Schema) {


  private[this] val changeLogTableDAO: ChangeLogDAO =
    new ChangeLogDAOImpl(schema, connection)

  private[this] val changeLogMetaDataDAO: ChangeLogMetaDataDAO =
    new ChangeLogMetaDataDAOImpl(schema, connection)

  private[this] def applyMigration(changeLogEntry: ChangeLogEntry): ChangeLogEntry = {
    // Perform the migration. We assume that all statements are "updates", i.e.
    // either UPDATE/INSERT/etc. or DDL statements.
    connection.executeUpdate(changeLogEntry.sql)
    // Insert the change log entry.
    changeLogTableDAO.insertChangeLogEntry(changeLogEntry)
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
    // Load up all the existing change log
    val existingChangeLogEntries =
      changeLogTableDAO.readChangeLogEntries()

    // Read existing change log version
    val version: ChangeLogVersion =
      changeLogMetaDataDAO.readChangeLogVersionOrDefault()

    // Next changelog version, after upcasting.
    val versionsInMigrations =
      migrations.map {
        case _: Migration.MigrationV1 => ChangeLogVersion.V1
        case _: Migration.MigrationV2 => ChangeLogVersion.V2
        case _: Migration.MigrationV3 => ChangeLogVersion.V3
      }
      .distinct

    if (versionsInMigrations.size > 1) {
      throw new InvalidMigrationSequenceException("All migrations MUST be of the same version")
    }

    val maybeNextVersion: Option[ChangeLogVersion] =
      versionsInMigrations.headOption

    // Resolve input migrations with existing changelog entries
    val entries =
      ChangeLogResolver.resolveChangeLogEntries(
        migrations = migrations,
        changeLogEntries = existingChangeLogEntries)

    // Ensure we do not have any unresolved entries. That could indicate
    // an invalid progression through migration formats, so we disallow it.
    if (entries.unresolvedEntries.nonEmpty) {
      throw new UnresolvedChangeLogEntriesFoundException(
        "The provided list of migrations MUST include all known change log entries. "+
        s"The following entries were found in the DB, and were unresolvable: ${entries.unresolvedEntries.mkString(",")}")
    }

    // Make sure existing entries match.
    for (correspondingChangeLogEntries <- entries.migrationsWithEntries) {
      val maybeExistingChangeLogEntry = correspondingChangeLogEntries._2
      maybeExistingChangeLogEntry foreach { existingChangeLogEntry =>
        val inputMigration = correspondingChangeLogEntries._1
        // Check that the SQL matches. We need to explicitly trim existing
        // SQL entries since they may have been inserted before we started
        // trimming input migration SQL.
        if (trimSql(existingChangeLogEntry.sql) != inputMigration.sql) {
          throwDoesNotMatch(
            existingSql = existingChangeLogEntry.sql,
            newSql = inputMigration.sql)
        }
      }
    }

    // Determine which Change Log entries need to be migrated to new version,
    // and collect the new migrated entries
    val entriesForUpdate: Seq[Option[(ChangeLogEntry, ChangeLogEntry)]] =
      for (correspondingEntries <- entries.migrationsWithEntries) yield {
        correspondingEntries._2 flatMap { existingChangeLogEntry =>
          val inputMigration = correspondingEntries._1
          MigrateChangeLogEntry.migrateChangeLogEntry(
            version = version,
            changeLogEntry = existingChangeLogEntry,
            migration = inputMigration
          ) match {
            case Left(error) =>
              throw new ImpossibleChangeLogMigrationException(
                s"Unable to migrate $existingChangeLogEntry. Error: $error")
            case Right(migratedChangeLogEntry) =>
              if (migratedChangeLogEntry != existingChangeLogEntry) {
                Some((migratedChangeLogEntry, existingChangeLogEntry))
              } else {
                None
              }
          }
        }
      }

    // Perform update in changelog table, we do this after, to ensure
    // we were able to migrate all entries, before updating the first
    for (entry <- entriesForUpdate.flatten) {
      val migrated = entry._1
      val existing = entry._2
      changeLogTableDAO.updateChangeLogEntry(migrated, existing)
    }

    // All updates performed, if any. Write new version information
    // to changelog metadata table.
    maybeNextVersion match {
      case Some(next) if next.versionInt > version.versionInt =>
        changeLogMetaDataDAO.writeChangeLogVersion(next)
      case _ =>
        // Either nothing to do, or a downgrade, which we wont write,
        // we don't throwaway information, so the changelogs are still valid
        ()
    }

    // Everything matches up to the list of existing entries,
    // so we just need to apply the remainder; we can get that
    // by finding the migrations without associated changelog entries
    entries.migrationsWithEntries
      .filter(_._2.isEmpty)
      .toVector
      .map { pair =>
        val migration = pair._1
        val (maybeId, maybeParent) =
          migration match {
            case _: Migration.MigrationV1 =>
              (None, None)
            case m: Migration.MigrationV2 =>
              (Some(m.identifier.id), m.identifier.parentId)
            case m: Migration.MigrationV3 =>
              (Some(m.identifier.id), m.identifier.parentId)
          }
        ChangeLogEntry(
          legacyIdentifier = migration.legacyIdentifier.map(_.legacyId).getOrElse(-1),
          migrationId = maybeId,
          migrationParentId = maybeParent,
          sql = migration.sql)
      }
  }

  /**
    * Verify changelog IDs are contiguous. We assume that the input
    * list is sorted by identifier.
    */
  private[this] def isContiguous(migrations: Vector[Migration]): Boolean = {
    // Get migrations with a legacy identifier
    val migrationIds =
      migrations
        .flatMap(_.legacyIdentifier)
        .map(_.legacyId)
    // Expected identifiers are [0..]
    val expectedIdentifiers = 0 until migrationIds.length
    // Check each migration against its expected index.
    migrationIds
      .zip(expectedIdentifiers)
      .forall {
        case (migrationIdentifier, expectedIdentifier) =>
          migrationIdentifier == expectedIdentifier
      }
  }

  /**
    * Check for inconsistent duplicate migrations in input and remove
    * all consistent duplicates. This is for consistency
    * with what happens if you run the same migration against a database
    * over multiple calls to [[applyChangeLog()]] (as opposed to doing it
    * in a single call -- which is the case this is meant for handling).
    *
    * We assume that the input list is sorted.
    */
  private[this] def removeDuplicates(migrations: Vector[Migration]): Vector[Migration] = {
    // Check that all "duplicates" are in fact identical. We group all migrations
    // with identical identifiers, and iterate through all migrations with identical
    // identifiers and compare the SQL, so that we know that once we're through
    // this loop *all* migrations with a given identifier must have identical
    // SQL.
    migrations
      .groupBy(_.identifier)
      .filter(_._2.size > 1)
      .foreach { kv =>
        val differentSqls =
          kv._2.map(_.sql).distinct
        if (differentSqls.length > 1) {
          throwDoesNotMatch(
            existingSql = differentSqls.headOption.getOrElse(""),
            newSql = differentSqls.lift(1).getOrElse(""))
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
    val trimmedMigrations = _migrations.map {
      case m: Migration.MigrationV1 => m.copy(sql = trimSql(m.sql))
      case m: Migration.MigrationV2 => m.copy(sql = trimSql(m.sql))
      case m: Migration.MigrationV3 => m.copy(sql = trimSql(m.sql))
    }

    // Order the given migrations. We first attempt to order them
    // based on the legacy integer id, and secondly using the declared
    // parent chain. If both are available, we ensure they produce the
    // same ordering, before we continue.

    val orderedByLegacyId =
      MigrationOrdering.orderByLegacyId(trimmedMigrations)
    val orderedByParentRef =
      MigrationOrdering.orderByParentReferences(trimmedMigrations)

    // Sanitize input
    val allMigrations: Vector[Migration] =
      (orderedByLegacyId, orderedByParentRef) match {
        case (Left(err), Left(_)) =>
          throw new InvalidMigrationSequenceException(
            s"Provided migrations MUST have a common identifier type defined: $err")
        case (Right(sortedByLegacyId), Right(sortedByRef)) =>
          if (sortedByLegacyId == sortedByRef) {
            sortedByRef
          } else {
            throw new InvalidMigrationSequenceException(
              s"Provided migrations MUST have the SAME ordering, when both Legacy Id and Parent Id provided")
          }
        case (Left(_), Right(sortedByRef)) =>
          // This case: Only if existing migrations have UUID refs stamped in DB?
          sortedByRef
        case (Right(sortedByLegacyId), Left(numUnresolved)) =>
          if (sortedByLegacyId.length != numUnresolved) {
            throw new InvalidMigrationSequenceException(
              s"If some migrations are given a UUID identifier, ALL MUST be given a UUID identifier")
          } else {
            sortedByLegacyId
          }
      }

    // Remove duplicates
    val deduplicatedMigrations = removeDuplicates(allMigrations)

    // Make sure nothing changed with duplicate-removal, and sorting
    if (allMigrations != deduplicatedMigrations) {
      throw new InvalidMigrationSequenceException(
        s"Provided migrations MUST be ordered by identifier, and contain no duplicates")
    }

    // Make sure that all the identifiers are contiguous.
    if (!isContiguous(allMigrations)) {
      throw new DisjointMigrationsException(
        s"Identifiers for migrations MUST be contiguous and start at 0")
    }

    // Disable auto-commit; we absolutely cannot have commits at "random" points
    // during the migration.
    connection.setAutoCommit(false)
    // Create the change log itself (if missing). This is an atomic
    // and idempotent operation, so we don't need a lock here; indeed
    // we could not possibly take one since we may not actually have
    // a table to lock.
    changeLogTableDAO.createChangeLogIfMissing()
    // Create the change log meta data table (if missing). The table
    // creation is an atomic and idempotent operation, but filling
    // with default values is not, so an exclusive lock is obtained
    // on the metadata table after creation.
    changeLogMetaDataDAO.createChangeLogMetaDataIfMissing()
    // Commit
    connection.commit()
    // Everything else needs the lock; note that this means that
    // exactly one process will get to perform the operations while
    // the others wait and will discover that updates have already
    // been performed.
    val appliedChangeLogEntries: Vector[ChangeLogEntry] =
      changeLogTableDAO.withChangeLogLock {
        // We verify all the change log entries and select which
        // have yet to be applied.
        val changeLogEntriesToApply = verifyChangeLogEntries(allMigrations)
        // Apply everything from there and up.
        changeLogEntriesToApply.map(applyMigration)
      }
    // Commit all changes
    connection.commit()
    // Return migration result
    AppliedMigrations(appliedChangeLogEntries.size)
  }

}
