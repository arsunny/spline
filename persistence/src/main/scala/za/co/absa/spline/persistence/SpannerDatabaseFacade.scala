package za.co.absa.spline.persistence

import com.google.cloud.spanner.{DatabaseClient, DatabaseId, ResultSet, SessionPoolOptions, Spanner, SpannerOptions, Statement}
import com.typesafe.scalalogging.LazyLogging
import org.springframework.beans.factory.DisposableBean

class SpannerDatabaseFacade(
  projectId: String, 
  instanceId: String, 
  databaseId: String,
  minSessions: Int,
  maxSessions: Int
) extends DisposableBean with LazyLogging {

  private val sessionPoolOptions = SessionPoolOptions.newBuilder()
    .setMinSessions(minSessions)
    .setMaxSessions(maxSessions)
    .build()
  
  private val spannerOptions: SpannerOptions = SpannerOptions.newBuilder()
    .setProjectId(projectId)
    .setSessionPoolOption(sessionPoolOptions)
    .build()

  private val spanner: Spanner = spannerOptions.getService

  lazy val client: DatabaseClient = {
    val dbId = DatabaseId.of(projectId, instanceId, databaseId)
    logger.info(s"Connecting to Spanner: $dbId (MinSessions: $minSessions, MaxSessions: $maxSessions)")
    val dbClient = spanner.getDatabaseClient(dbId)
    warmUp(dbClient)
    dbClient
  }

  private def warmUp(dbClient: DatabaseClient): Unit = {
    logger.info(s"Warming up Spanner connection to $databaseId...")
    try {
      // Simple query to force session creation and auth check
      val resultSet: ResultSet = dbClient.singleUse().executeQuery(Statement.of("SELECT 1"))
      try {
        if (resultSet.next()) {
          logger.info("Spanner connection smoke test successful.")
        }
      } finally {
        resultSet.close()
      }
    } catch {
      case e: Exception =>
        logger.error(s"Failed to connect to Spanner: ${e.getMessage}")
        throw e
    }
  }

  override def destroy(): Unit = {
    logger.info("Closing Spanner connection")
    spanner.close()
  }
}