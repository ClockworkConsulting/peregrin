package dk.cwconsult.peregrin.core.impl.dao

import dk.cwconsult.peregrin.core.impl.ChangeLogEntry.ChangeLogVersion

trait ChangeLogMetaDataDAO {

  /**
    * Create ChangeLogMetaData table if missing
    */
  def createChangeLogMetaDataIfMissing(): Unit

  /**
    * Record a new Change Log format version in the metadata table
    */
  def writeChangeLogVersion(changeLogVersion: ChangeLogVersion): Unit

  /**
    * Query metadata table for format version
    */
  def readChangeLogVersionOrDefault(): ChangeLogVersion

}
