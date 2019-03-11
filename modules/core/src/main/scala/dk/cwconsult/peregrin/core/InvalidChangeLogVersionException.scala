package dk.cwconsult.peregrin.core

class InvalidChangeLogVersionException(
  message: String,
  throwable: Throwable = null)
  extends MigrationException(message, throwable)
