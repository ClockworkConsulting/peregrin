package dk.cwconsult.peregrin.core

class UnresolvedChangeLogEntriesFoundException(
  message: String,
  throwable: Throwable = null)
  extends MigrationException(message, throwable)
