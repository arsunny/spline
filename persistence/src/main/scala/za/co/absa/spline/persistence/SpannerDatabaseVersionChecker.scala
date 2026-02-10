package za.co.absa.spline.persistence

import org.springframework.beans.factory.InitializingBean
import za.co.absa.spline.persistence.migration.MigrationScriptRepository
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class SpannerDatabaseVersionChecker(dbVersionManager: SpannerDatabaseVersionManager) extends InitializingBean {

  override def afterPropertiesSet(): Unit = {
    val requiredDBVersion = MigrationScriptRepository.latestToVersion
    
    // We intentionally block here (Await) because the app cannot start 
    // if the database version is incorrect.
    val currentDBVersion = Await.result(dbVersionManager.currentVersion, Duration.Inf)

    if (requiredDBVersion != currentDBVersion) {
      sys.error(
        s"Database version ${currentDBVersion.asString} is out of date, version ${requiredDBVersion.asString} is required. " +
        s"Please execute upgrade tools to upgrade the Spanner database."
      )
    }
  }
}