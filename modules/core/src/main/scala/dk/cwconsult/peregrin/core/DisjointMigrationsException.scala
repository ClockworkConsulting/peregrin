package dk.cwconsult.peregrin.core

/**
  * The list of migrations is disjoint, i.e. the
  * sequence numbers are not contiguous.
  */
class DisjointMigrationsException(
  message: String,
  throwable: Throwable = null)
  extends MigrationException(message, throwable)
