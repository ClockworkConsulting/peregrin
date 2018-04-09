package dk.cwconsult.peregrin.core

class MigrationException(
  message: String,
  throwable: Throwable)
  extends RuntimeException(message, throwable)
