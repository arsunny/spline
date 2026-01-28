package za.co.absa.spline.persistence

import com.google.cloud.spanner.DatabaseClient
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.scalalogging.LazyLogging
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.{Bean, Configuration}
import za.co.absa.commons.config.ConfTyped
import za.co.absa.spline.common.config.DefaultConfigurationStack
import org.apache.commons.configuration2.ConfigurationImplicits._

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

@Configuration
class SpannerRepoConfig extends InitializingBean with LazyLogging {

  import za.co.absa.spline.persistence.SpannerRepoConfig._

  override def afterPropertiesSet(): Unit = {
    logger.info(s"Spline Spanner Project ID: ${Database.ProjectId}")
    logger.info(s"Spline Spanner Instance ID: ${Database.InstanceId}")
    logger.info(s"Spline Spanner Database ID: ${Database.DatabaseId}")
    logger.info(s"Spline Spanner Pool Size : ${Database.MaxSessions}")
  }

  @Bean def spannerDatabaseFacade: SpannerDatabaseFacade = {
    new SpannerDatabaseFacade(
      Database.ProjectId,
      Database.InstanceId,
      Database.DatabaseId,
      Database.MinSessions,
      Database.MaxSessions
    )
  }

  @Bean def spannerDatabaseClient: DatabaseClient = spannerDatabaseFacade.client

  /**
   * Dedicated ExecutionContext for blocking Spanner IO operations.
   * This is critical to prevent thread starvation on the main application pool.
   */
  @Bean def dbExecutionContext: ExecutionContext = {
    val threadFactory = new ThreadFactoryBuilder()
      .setNameFormat("spanner-io-%d")
      .setDaemon(true)
      .build()

    // We align the thread pool size with the max sessions. 
    // This ensures that if we have a DB connection, we have a thread to use it.
    val executor = Executors.newFixedThreadPool(Database.MaxSessions, threadFactory)
    ExecutionContext.fromExecutor(executor)
  }

  @Bean def databaseVersionManager: SpannerDatabaseVersionManager = {
    new SpannerDatabaseVersionManager(spannerDatabaseClient)(dbExecutionContext)
  }

  @Bean def databaseVersionChecker(versionManager: SpannerDatabaseVersionManager): SpannerDatabaseVersionChecker = {
    new SpannerDatabaseVersionChecker(versionManager)
  }
}

object SpannerRepoConfig extends DefaultConfigurationStack with ConfTyped {

  override val rootPrefix: String = "spline"

  object Database extends Conf("database") {
    private val conf = SpannerRepoConfig.this

    val ProjectId: String = conf.getRequiredString(Prop("spanner.projectId"))
    val InstanceId: String = conf.getRequiredString(Prop("spanner.instanceId"))
    val DatabaseId: String = conf.getRequiredString(Prop("spanner.databaseId"))

    // Defaults are critical for sizing the thread pool correctly
    val MinSessions: Int = conf.getInt(Prop("spanner.minSessions"), 10)
    val MaxSessions: Int = conf.getInt(Prop("spanner.maxSessions"), 100)
  }
}