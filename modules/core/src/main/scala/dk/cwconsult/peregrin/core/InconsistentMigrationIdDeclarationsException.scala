package dk.cwconsult.peregrin.core

class InconsistentMigrationIdDeclarationsException(
  message: String,
  throwable: Throwable = null)
  extends MigrationException(message, throwable)
