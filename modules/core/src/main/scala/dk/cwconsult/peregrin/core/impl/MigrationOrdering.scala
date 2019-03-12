package dk.cwconsult.peregrin.core.impl

import dk.cwconsult.peregrin.core.migrations.Migration
import dk.cwconsult.peregrin.core.migrations.MigrationId.LegacyId

object MigrationOrdering {

  def orderByLegacyId(allMigrations: Vector[Migration]): Either[String, Vector[Migration]] = {
    // Extract integer legacy id for ordering
    val migrationsWithLegacyIds: Vector[Option[(LegacyId, Migration)]] =
      allMigrations.map { m =>
        m.legacyIdentifier.map { legacyId =>
          (legacyId, m)
        }
      }
    // Split migrations based on declaration of legacy ids
    val (missingLegacyIds, hasLegacyIds) =
      migrationsWithLegacyIds.partition(_.isEmpty)
    // Sort the migrations, if all have a legacy id
    if (missingLegacyIds.nonEmpty) {
      Left(
        "The following Migration(s) are lacking a legacy Integer-identifier: "+
        missingLegacyIds.mkString(", "))
    } else {
      Right(hasLegacyIds.flatten.sortBy(_._1.legacyId).map(_._2))
    }
  }

}
