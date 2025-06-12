import java.io.FileInputStream
import java.util.Properties

import sbt.Path

import scala.collection.JavaConverters._

object Versions {

  def versions(path: String): Map[String, String] = {
    val props        = new Properties()
    val versionsFile = Path(s"${Common.workspaceDirectory}/amf-metadata/$path").asFile
    props.load(new FileInputStream(versionsFile))
    props.entrySet().asScala.map(e => e.getKey.toString -> e.getValue.toString).toMap
  }
}
