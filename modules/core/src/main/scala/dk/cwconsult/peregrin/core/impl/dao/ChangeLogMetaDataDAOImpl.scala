package dk.cwconsult.peregrin.core.impl.dao

import java.sql.Connection

import dk.cwconsult.peregrin.core.InvalidChangeLogVersionException
import dk.cwconsult.peregrin.core.Schema
import dk.cwconsult.peregrin.core.Table
import dk.cwconsult.peregrin.core.impl.ChangeLogEntry.ChangeLogVersion
import dk.cwconsult.peregrin.core.impl.dao.BaseDAO.ColumnProbe.Missing
import dk.cwconsult.peregrin.core.impl.dao.ChangeLogMetaDataDAOImpl.MetaData

import scala.util.Try

class ChangeLogMetaDataDAOImpl(schema: Schema, connection: Connection)
  extends BaseDAO(connection)
    with ChangeLogMetaDataDAO {

  import dk.cwconsult.peregrin.core.impl.ConnectionImplicits._

  private[this] val metaDataTable =
    Table(
      name = "__peregrin_metadata__",
      schema)

  private[this] val VERSION_IDENTIFIER: String = "version"
  private[this] val VERSION_DEFAULT_VAL: ChangeLogVersion = ChangeLogVersion.V1

  private[this] val createPeregrinMetaDataTable: String =
    s"""
        CREATE TABLE IF NOT EXISTS $metaDataTable (
          "identifier" VARCHAR(255) NOT NULL,
          "value" TEXT NOT NULL)
      """.stripMargin


  private[this] val lockPeregrinMetaDataTable: String =
    s"LOCK $metaDataTable IN EXCLUSIVE MODE"

  /**
    * Create meta data table if necessary.
    */
  override def createChangeLogMetaDataIfMissing(): Unit = {
    // Create the migration log table.
    connection.execute(createPeregrinMetaDataTable)
    // Lock the table while we inspect the entries (actually until end of transaction)
    connection.execute(lockPeregrinMetaDataTable)
    // Determine if we need to create a version entry
    if (readChangeLogVersion().isEmpty) {
      // Default to version 1
      writeChangeLogVersion(VERSION_DEFAULT_VAL)
    }
  }

  // ----------------------------------------

  private[this] def readChangeLogVersion(): Option[ChangeLogVersion] =
    readMetaDataByName(VERSION_IDENTIFIER)
      .flatMap(_.asInt)
      .map {
        case ChangeLogVersion.V1.versionInt => ChangeLogVersion.V1
        case ChangeLogVersion.V2.versionInt => ChangeLogVersion.V2
        case ChangeLogVersion.V3.versionInt => ChangeLogVersion.V3
        case other => throw new InvalidChangeLogVersionException(
          s"Invalid version in changelog entry: $other")
      }

  override def readChangeLogVersionOrDefault(): ChangeLogVersion =
    readChangeLogVersion().getOrElse(VERSION_DEFAULT_VAL)

  override def writeChangeLogVersion(changeLogVersion: ChangeLogVersion): Unit =
    writeMetaDataValue(VERSION_IDENTIFIER, changeLogVersion.versionInt.toString)

  // ----------------------------------------

  private[this] val selectMetaDataIdentifierValue: String =
    s"""
       SELECT "identifier", "value" FROM $metaDataTable
       WHERE "identifier" = ?
     """.stripMargin

  private[this] def readMetaDataByName(identifier: String): Option[MetaData] =
    connection.executeQueryPrepared(selectMetaDataIdentifierValue) { stmt =>
      stmt.setString(1, identifier)
    } { resultSet =>
      Iterator
        .continually(resultSet.next())
        .takeWhile(identity)
        .flatMap(_ => Option(resultSet.getString(2)))
        .map(MetaData)
        .toVector
        .headOption
    }

  // ----------------------------------------

  private[this] val insertMetaDataEntry: String =
    s"""INSERT INTO $metaDataTable ("value", "identifier") VALUES (?, ?)"""

  private[this] val updateMetaDataEntry: String =
    s"""UPDATE $metaDataTable SET "value" = ? WHERE "identifier" = ?"""

  private[this] def writeMetaDataValue(identifier: String, value: String): Unit = {
    // Determine if we need to do an update or insert
    val upsertQuery = readMetaDataByName(identifier) match {
      case Some(_) => updateMetaDataEntry
      case None => insertMetaDataEntry
    }
    // Perform the UPSERT
    connection.executeUpdatePrepared(upsertQuery) { stmt =>
      // The ordering of parameters is identical for both INSERT and UPDATE query
      stmt.setString(1, value)
      stmt.setString(2, identifier)
    }
  }

}

object ChangeLogMetaDataDAOImpl {

  case class MetaData private[ChangeLogMetaDataDAOImpl] (value: String) {
    def asInt: Option[Int] = Try(value.toInt).toOption
  }

}


