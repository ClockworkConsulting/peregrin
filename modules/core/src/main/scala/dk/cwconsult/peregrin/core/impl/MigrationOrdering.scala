package dk.cwconsult.peregrin.core.impl

import java.util.UUID

import dk.cwconsult.peregrin.core.InvalidMigrationSequenceException
import dk.cwconsult.peregrin.core.Migration
import dk.cwconsult.peregrin.core.migrations.MigrationId.LegacyId
import dk.cwconsult.peregrin.core.migrations.MigrationId.ChildParentRelation

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

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

  case class UuidMigration(
    uuidId: ChildParentRelation,
    migration: Migration
  )

  private[this] def tsort(migrations: Vector[UuidMigration]): Vector[Migration] = {
    // Build lookup table by Parent Id
    val migrationsByParent: mutable.Map[Option[UUID], Vector[UuidMigration]] =
      mutable.Map.empty ++ migrations.groupBy(_.uuidId.parentId)
    // Prepare queue for discovered migrations
    val discoveredMigrations: mutable.Queue[UuidMigration] =
      new mutable.Queue[UuidMigration]()

    def queueWithParent(parentId: Option[UUID]): Unit = {
      discoveredMigrations ++= migrationsByParent.remove(parentId).getOrElse(Vector.empty)
    }

    // Prepare result, topologically sorted migrations
    val result: ArrayBuffer[Migration] = new ArrayBuffer[Migration]()

    // Sanity check, that we have at least one root node
    if (!migrationsByParent.contains(None) && migrationsByParent.nonEmpty) {
      throw new InvalidMigrationSequenceException(
        s"Migration sequence MUST contain at least one root node (no parent)")
    }

    // Queue all root nodes first
    queueWithParent(None)
    // While we have referenced migrations, build the result
    while (discoveredMigrations.nonEmpty) {
      // Get a migration from the queue
      val m = discoveredMigrations.dequeue()
      // Queue all migrations that has this migration as a parent
      queueWithParent(Some(m.uuidId.id))
      // Add current migration to the output
      result += m.migration
    }

    // Sanity check, that all migrations have been identified
    if (migrationsByParent.nonEmpty) {
      throw new InvalidMigrationSequenceException(
        s"Migrations MUST reference valid parents (Do you have a cycle?). The following Migrations referenced unknown parents: "+
        migrationsByParent.values.toVector.flatMap(_.map(_.uuidId.id)))
    }

    result.toVector
  }

  def orderByParentReferences(allMigrations: Vector[Migration]): Either[Int, Vector[Migration]] = {
    // Extract UUID based id for ordering
    val migrationsWithUUID: Vector[Option[UuidMigration]] =
      allMigrations.map {
        case _: Migration.MigrationV1 =>
          None
        case m: Migration.MigrationV2 =>
          Some(UuidMigration(
            uuidId = m.identifier,
            migration = m))
        case m: Migration.MigrationV3 =>
          Some(UuidMigration(
            uuidId = m.identifier,
            migration = m))
      }
    // Split migrations based on declaration of parent ids
    val (missingUuid, hasUuid) =
      migrationsWithUUID.partition(_.isEmpty)
    // Sort the migrations, if we have UUIDs declared for all
    if (missingUuid.nonEmpty) {
      // Return number of migrations missing uuid identifier,
      // if it is not all, an error should be raised
      Left(missingUuid.size)
    } else {
      Right(tsort(hasUuid.flatten))
    }
  }

}
