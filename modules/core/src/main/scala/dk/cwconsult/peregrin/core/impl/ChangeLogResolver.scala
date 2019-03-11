package dk.cwconsult.peregrin.core.impl

import dk.cwconsult.peregrin.core.Migration

object ChangeLogResolver {

  case class ChangeLogEntries(
    unresolvedEntries: Seq[ChangeLogEntry],
    migrationsWithEntries: Seq[(Migration, Option[ChangeLogEntry])])

  def resolveChangeLogEntries(
    migrations: Seq[Migration],
    changeLogEntries: Seq[ChangeLogEntry]
  ): ChangeLogEntries = {
    // Perform mapping
    val mappings =
      for (migration <- migrations) yield {
        val maybeMatchingChangeLog =
          changeLogEntries.find(
            ChangeLogIdentity.correspondsTo(migration, _))
        (migration, maybeMatchingChangeLog)
      }
    // Resolve changelog entries that were not resolved
    val unresolved =
      changeLogEntries.filterNot(e => mappings.exists(_._2.contains(e)))
    // Return result
    ChangeLogEntries(
      unresolvedEntries = unresolved,
      migrationsWithEntries = mappings)
  }

}
