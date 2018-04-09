package dk.cwconsult.peregrin.core

import dk.cwconsult.peregrin.core.impl.SQLSupport

case class Table(name: String, schema: Schema) {

  /**
    * Return a string representing an SQL-safe string representing the Table.
    */
  override def toString(): String =
    s"$schema.${SQLSupport.quoteIdentifier(name)}"

}
