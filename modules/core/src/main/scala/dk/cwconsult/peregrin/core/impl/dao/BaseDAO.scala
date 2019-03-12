package dk.cwconsult.peregrin.core.impl.dao

import java.sql.Connection

import dk.cwconsult.peregrin.core.Table
import dk.cwconsult.peregrin.core.impl.ConnectionImplicits._
import dk.cwconsult.peregrin.core.impl.dao.BaseDAO.ColumnProbe
import dk.cwconsult.peregrin.core.impl.dao.BaseDAO.ColumnProbe.Exists
import dk.cwconsult.peregrin.core.impl.dao.BaseDAO.ColumnProbe.Missing

import scala.util.Failure
import scala.util.Success
import scala.util.Try

abstract class BaseDAO(connection: Connection) {

  /**
    * Probe a given table for available columns, and process a handler,
    * if the column either exists or is missing, according to the configuration.
    */
  protected[this] def probeAndMigrateTable(t: Table)(columnProbes: (ColumnProbe, () => Unit)*): Unit = {
    // Resolve which probe handlers we need to process
    val pendingProbeActions: Seq[(ColumnProbe, () => Unit)] =
      connection.executeQuery(s"SELECT * FROM $t LIMIT 1") { resultSet =>
        // Process the provided probes, and collect actions to be performed afterwarsd
        columnProbes.flatMap { probe =>
          val probeResult = Try(resultSet.findColumn(probe._1.columnName))
          (probe._1, probeResult) match {
            case (Exists(_), Success(_)) => Some(probe)
            case (Exists(_), Failure(_)) => None
            case (Missing(_), Success(_)) => None
            case (Missing(_), Failure(_)) => Some(probe)
          }
        }
      }
    // Run matching handlers
    pendingProbeActions.foreach { probe =>
      probe._2()
    }
  }

}

object BaseDAO {

  sealed trait ColumnProbe {
    def columnName: String

    def ->(f: => Unit): (ColumnProbe, () => Unit) =
      (this, { () => f })
  }

  object ColumnProbe {

    case class Exists private[dao] (
      override val columnName: String)
      extends ColumnProbe

    case class Missing private[dao] (
      override val columnName: String)
      extends ColumnProbe

  }


}
