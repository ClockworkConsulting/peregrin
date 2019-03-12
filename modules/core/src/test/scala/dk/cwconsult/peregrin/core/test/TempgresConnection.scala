package dk.cwconsult.peregrin.core.test

import java.util.UUID

import dk.cwconsult.tempgres.TempgresClient
import scalikejdbc.ConnectionPool
import scalikejdbc.GlobalSettings
import scalikejdbc.LoggingSQLAndTimeSettings

object TempgresConnection {

  /**
   * Create connection pool connected to a temporary database.
   */
  def createConnectionPool(): ConnectionPool = {
    // Generate connection pool name which is unlikely to collide with anything.
    // We use this as a way to catch mistakes where the wrong connection pool
    // is being accessed. This also prevents conflicts with other tests which
    // could run simultaneously.
    val connectionPoolName: String =
      UUID.randomUUID().toString

    // Create the database and connection pool
    val database =
      TempgresClient.createTemporaryDatabase(System.getProperty("tempgres.url", "http://tempgres:8080"))

    ConnectionPool.add(
      connectionPoolName,
      database.getUrl,
      database.getCredentials.getUserName,
      database.getCredentials.getPassword)

    // Reduce log spam.
    GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
      singleLineMode = true,
      logLevel = 'trace)

    // Return the connection pool
    ConnectionPool.get(connectionPoolName)
  }

}
