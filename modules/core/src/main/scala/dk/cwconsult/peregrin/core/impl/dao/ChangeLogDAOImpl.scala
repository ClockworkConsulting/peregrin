package dk.cwconsult.peregrin.core.impl.dao

import java.sql.Connection
import java.util.UUID

import dk.cwconsult.peregrin.core.Schema
import dk.cwconsult.peregrin.core.Table
import dk.cwconsult.peregrin.core.impl.ChangeLogEntry
import dk.cwconsult.peregrin.core.impl.dao.ChangeLogDAOImpl.ColumnProbe
import dk.cwconsult.peregrin.core.impl.dao.ChangeLogDAOImpl.ColumnProbe.Exists
import dk.cwconsult.peregrin.core.impl.dao.ChangeLogDAOImpl.ColumnProbe.Missing

import scala.collection.mutable.ArrayBuffer
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class ChangeLogDAOImpl(schema: Schema, connection: Connection) extends ChangeLogDAO {

  import dk.cwconsult.peregrin.core.impl.ConnectionImplicits._

  private[this] val changeLogTable =
    Table(
      name = "__peregrin_changelog__",
      schema)

  private[this] val createPeregrinSchema: String =
    s"CREATE SCHEMA IF NOT EXISTS $schema"

  private[this] val createPeregrinChangeLogTable: String =
    s"""
        CREATE TABLE IF NOT EXISTS $changeLogTable (
          "identifier" INT NOT NULL,
          "sql" TEXT NOT NULL,
          "executed" TIMESTAMP WITH TIME ZONE NOT NULL)
      """.stripMargin

  private[this] val alterPeregrinChangeLogTableAddUUIDId: String =
    s"""
        ALTER TABLE $changeLogTable
        ADD COLUMN "migration_id" UUID
     """.stripMargin

  private[this] val alterPeregrinChangeLogTableAddParentId: String =
    s"""
        ALTER TABLE $changeLogTable
        ADD COLUMN "migration_parent_id" UUID
     """.stripMargin

  /**
    * Create change log if necessary.
    */
  override def createChangeLogIfMissing(): Unit = {
    // Create the schema if necessary.
    connection.execute(createPeregrinSchema)
    // Create the migration log table.
    val _ = connection.execute(createPeregrinChangeLogTable)
    // Migrate the schema, based on which columns we have available
    probeAndMigrateTable(changeLogTable)(
      // Add new UUID identifier for migration, if it doesn't exist already
      Missing("migration_id") -> {
        connection.execute(alterPeregrinChangeLogTableAddUUIDId)
      },
      // Add new parent UUID identifier, if it doesn't exist already
      Missing("migration_parent_id") -> {
        connection.execute(alterPeregrinChangeLogTableAddParentId)
      })
  }

  // ---------------------------------------------

  private[this] val selectChangeLogEntries: String =
    s"""
        SELECT "identifier", "migration_id", "migration_parent_id", "sql"
        FROM $changeLogTable
        ORDER BY "identifier" ASC
    """.stripMargin

  override def readChangeLogEntries(): Vector[ChangeLogEntry] = {
    connection.executeQuery(selectChangeLogEntries) { resultSet =>
      // Extract all the results
      val rows = new ArrayBuffer[ChangeLogEntry]()
      while (resultSet.next()) {
        // Build new changelog entry
        rows += ChangeLogEntry(
          legacyIdentifier = resultSet.getInt(1),
          migrationId = Option(resultSet.getString(2)).map(UUID.fromString),
          migrationParentId = Option(resultSet.getString(3)).map(UUID.fromString),
          sql = resultSet.getString(4))
      }
      rows.toVector
    }
  }

  // ---------------------------------------------

  private[this] val insertChangeLogEntry: String =
    s"""INSERT INTO $changeLogTable (
       "identifier", "sql", "migration_id", "migration_parent_id", "executed")
       VALUES (?, ?, ?, ?, now())""".stripMargin

  def insertChangeLogEntry(changeLogEntry: ChangeLogEntry): Unit = {
    // Insert the entry.
    val insertCount = connection.executeUpdatePrepared(insertChangeLogEntry) { stmt =>
      stmt.setInt(1, changeLogEntry.legacyIdentifier)
      stmt.setString(2, changeLogEntry.sql)
      stmt.setObject(3, changeLogEntry.migrationId.orNull)
      stmt.setObject(4, changeLogEntry.migrationParentId.orNull)
    }
    // Sanity check: Must have inserted exactly one row.
    if (insertCount != 1) {
      throw new IllegalStateException(s"Internal consistency error: No rows inserted")
    }
  }

  // ---------------------------------------------

  private[this] val updateChangeLogEntry: String =
    s"""UPDATE $changeLogTable SET
       "identifier" = ?,
       "migration_id" = ?,
       "migration_parent_id" = ?,
       "sql" = ?
       WHERE
       "identifier" = ? AND
       "migration_id" IS NOT DISTINCT FROM ? AND
       "migration_parent_id" IS NOT DISTINCT FROM ?
       """.stripMargin

  override def updateChangeLogEntry(updatedChangeLogEntry: ChangeLogEntry, oldChangeLogEntry: ChangeLogEntry): Unit = {
    // update the entry.
    val updateCount = connection.executeUpdatePrepared(updateChangeLogEntry) { stmt =>
      stmt.setObject(1, updatedChangeLogEntry.legacyIdentifier)
      stmt.setObject(2, updatedChangeLogEntry.migrationId.orNull)
      stmt.setObject(3, updatedChangeLogEntry.migrationParentId.orNull)
      stmt.setString(4, updatedChangeLogEntry.sql)
      // Params
      stmt.setInt(5, oldChangeLogEntry.legacyIdentifier)
      stmt.setObject(6, oldChangeLogEntry.migrationId.orNull)
      stmt.setObject(7, oldChangeLogEntry.migrationParentId.orNull)
    }
    // Sanity check: Must have updated exactly one row.
    if (updateCount != 1) {
      throw new IllegalStateException(s"Internal consistency error: $updateCount rows updated")
    }
  }

  // ---------------------------------------------

  private[this] val lockChangeLogTable: String =
    s"LOCK $changeLogTable IN EXCLUSIVE MODE"

  override def withChangeLogLock[A](block: => A): A = {
    connection.execute(lockChangeLogTable)
    try {
      block
    } finally {
      // Nothing to do; the lock will automatically be released at the end
      // of the transaction.
    }
  }

  // ---------------------------------------------

  /**
    * Probe a given table for available columns, and process a handler,
    * if the column either exists or is missing, according to the configuration.
    */
  private[this] def probeAndMigrateTable(t: Table)(columnProbes: (ColumnProbe, () => Unit)*): Unit = {
    // Resolve which probe handlers we need to process
    val pendingProbeActions: Seq[(ColumnProbe, () => Unit)] =
      connection.executeQuery(s"SELECT * FROM $t LIMIT 1") { resultSet =>
        // Process the provided probes, and collect actions to be performed afterwarsd
        columnProbes.flatMap { probe =>
          val probeResult = Try(resultSet.findColumn(probe._1.columnName))
          (probe._1, probeResult) match {
            case (Exists(_), Success(_)) => Some(probe)
            case (Exists(_), Failure(_)) => None
            case (Missing(_), Success(_)) => None
            case (Missing(_), Failure(_)) => Some(probe)
          }
        }
      }
    // Run matching handlers
    pendingProbeActions.foreach { probe =>
      probe._2()
    }
  }

}

object ChangeLogDAOImpl {

  sealed trait ColumnProbe {
    def columnName: String

    def ->(f: => Unit): (ColumnProbe, () => Unit) =
      (this, { () => f })
  }

  object ColumnProbe {

    case class Exists private[dao] (
      override val columnName: String)
      extends ColumnProbe

    case class Missing private[dao] (
      override val columnName: String)
      extends ColumnProbe

  }

}