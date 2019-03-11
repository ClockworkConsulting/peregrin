package dk.cwconsult.peregrin.core

class ImpossibleChangeLogMigrationException(
  message: String,
  throwable: Throwable = null)
  extends MigrationException(message, throwable)
