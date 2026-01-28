package za.co.absa.spline.persistence

import com.google.cloud.spanner.{DatabaseClient, Mutation, Statement}
import za.co.absa.commons.version.Version
import za.co.absa.commons.version.impl.SemVer20Impl.SemanticVersion
import za.co.absa.spline.persistence.model.DBVersion.Status

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.jdk.CollectionConverters._

class SpannerDatabaseVersionManager(client: DatabaseClient)(implicit ec: ExecutionContext) {

  // Assumes a table: CREATE TABLE DBVersion (Status STRING(MAX), Version STRING(MAX)) PRIMARY KEY (Status)
  private val BaselineVersion = Version.asSemVer("1.0.0")

  def currentVersion: Future[SemanticVersion] = {
    // Fix: Convert Enum to String explicitly
    getDBVersion(Status.Current.toString).map(_.getOrElse(BaselineVersion))
  }

  /**
   * Updates or Inserts the version into the DBVersion table.
   */
  def insertDbVersion(currentVersion: SemanticVersion): Future[SemanticVersion] = Future {
    blocking {
      val mutation = Mutation.newInsertOrUpdateBuilder("DBVersion")
        .set("Status").to(Status.Current.toString)
        .set("Version").to(currentVersion.asString)
        .build()

      client.write(Seq(mutation).asJava)
      currentVersion
    }
  }

  private def getDBVersion(status: String): Future[Option[SemanticVersion]] = Future {
    blocking {
      val sql = "SELECT Version FROM DBVersion WHERE Status = @status"
      val stmt = Statement.newBuilder(sql).bind("status").to(status).build()

      val resultSet = client.singleUse().executeQuery(stmt)
      try {
        if (resultSet.next()) {
          val verStr = resultSet.getString("Version")
          Some(Version.asSemVer(verStr))
        } else {
          None
        }
      } finally {
        resultSet.close()
      }
    }
  }
}