package dk.cwconsult.peregrin.core

/**
  * A migration was modified.
  */
class MigrationModifiedException(
  message: String,
  throwable: Throwable = null)
  extends MigrationException(message, throwable)
