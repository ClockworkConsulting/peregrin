package dk.cwconsult.peregrin.core.impl.dao

import dk.cwconsult.peregrin.core.impl.ChangeLogEntry

trait ChangeLogDAO {

  /**
    * Create ChangeLog table, if missing.
    */
  def createChangeLogIfMissing(): Unit

  /**
    * Read a changelog.
    */
  def readChangeLogEntries(): Vector[ChangeLogEntry]

  /**
    * Create a new Change Log Entry in the changelog table
    */
  def insertChangeLogEntry(changeLogEntry: ChangeLogEntry): Unit

  /**
    * Migrate an existing Change Log entry
    */
  def updateChangeLogEntry(
    updatedChangeLogEntry: ChangeLogEntry,
    oldChangeLogEntry: ChangeLogEntry): Unit

  /**
    * Run the given block in with a (transactional) lock.
    */
  def withChangeLogLock[A](block: => A): A

}
