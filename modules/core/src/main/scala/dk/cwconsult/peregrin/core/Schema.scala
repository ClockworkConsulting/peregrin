package dk.cwconsult.peregrin.core

import dk.cwconsult.peregrin.core.impl.SQLSupport

/**
  * Schema identifier. Schema identifiers should be regarded
  * as __case-sensitive__ since they are always used in quoted
  * form by the library.
  */
sealed trait Schema {

  protected[this] def asSql: String

  /**
    * Return a string representation, safe from quotes
    */
  def name: String

  /**
    * Return a string representing an SQL-safe string representing the Schema.
    */
  override final def toString(): String =
    asSql

}

object Schema {

  /**
   * Use named schema.
   */
  object Named {

    // Private implementation to avoid direct pattern matching
    // since we cannot properly support safe pattern matching on
    // given the "special" names.
    private[Named] case class NamedImpl(override val name: String) extends Schema {
      override val asSql: String =
        SQLSupport.quoteIdentifier(name)
    }

    def apply(name: String): Schema =
      NamedImpl(name)

  }

  /**
   * Use the default schema.
   */
  val Public = Named("public")

  /**
    * Use the built-in "pg_catalog" metadata schema.
    */
  val Catalog = Named("pg_catalog")

}
