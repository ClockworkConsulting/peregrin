package dk.cwconsult.peregrin.core.impl

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

private[impl] object ConnectionImplicits {

  implicit class RichConnection(private val underlying: Connection) extends AnyVal {

    private[this] def using[A, B](open: => A)(close: A => Unit)(run: A => B): B = {
      val resource = open
      try {
        run(resource)
      } finally {
        close(resource)
      }
    }

    def withPreparedStatement[R](sql: String)(block: PreparedStatement => R): R = {
      using(underlying.prepareStatement(sql))(_.close())(block)
    }

    def withStatement[R](block: Statement => R): R = {
      using(underlying.createStatement)(_.close())(block)
    }

    def withResultSet[R](rs: => ResultSet)(run: ResultSet => R): R =
      using(rs)(_.close())(run)

    def execute(sql: String): Boolean = {
      withStatement { stmt =>
        stmt.execute(sql)
      }
    }

    def executeQuery[A](sql: String)(processResultSet: ResultSet => A): A = {
      withStatement { stmt =>
        withResultSet(stmt.executeQuery(sql))(processResultSet)
      }
    }

    def executeUpdate(sql: String): Int = {
      withStatement { stmt =>
        stmt.executeUpdate(sql)
      }
    }

    def executeUpdatePrepared(sql: String)(setParameters: PreparedStatement => Unit): Int = {
      withPreparedStatement(sql) { stmt =>
        // Set up all the parameters
        setParameters(stmt)
        // Run and retrieve the row count
        stmt.executeUpdate()
      }
    }

    def executeQueryPrepared[A](sql: String)(setParameters: PreparedStatement => Unit)(processResultSet: ResultSet => A): A = {
      withPreparedStatement(sql) { stmt =>
        // Set up all the parameters
        setParameters(stmt)
        // Run
        val resultSet = stmt.executeQuery()
        // Process the result set, but ensure that it is always freed
        try {
          processResultSet(resultSet)
        } finally {
          resultSet.close()
        }
      }
    }

  }

}
