package dk.cwconsult.peregrin.core

class InvalidMigrationSequenceException(
  message: String,
  throwable: Throwable = null)
  extends MigrationException(message, throwable)
