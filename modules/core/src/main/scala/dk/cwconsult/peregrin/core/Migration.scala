package dk.cwconsult.peregrin.core

/**
 * Migration step.
 */
case class Migration(
  identifier: Int,
  sql: String)
