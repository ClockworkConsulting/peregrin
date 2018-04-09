package dk.cwconsult.peregrin.core

import Schema._
import org.scalatest.FlatSpec

class SchemaTest extends FlatSpec {

  behavior of "Schema"

  it should """compare Named("pg_catalog") as equal to Catalog""" in {
    // Setup
    val a = Named("pg_catalog")
    val b = Catalog
    // Exercise
    assert(a === b)
  }

  it should """compare Named("public") as equal to Public""" in {
    // Setup
    val a = Named("public")
    val b = Public
    // Exercise
    assert(a === b)
  }

}
