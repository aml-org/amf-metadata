import java.io.FileInputStream
import java.util.Properties

import sbt.Path

import scala.collection.JavaConverters._

object Versions {

  val sourceModeProjects = Seq("amf-runner", "amf-TCKutor")

  def versions(path: String): Map[String, String] = {
    val props        = new Properties()
    val versionsFile =
      if (currentProjectUsesSourceMode) Path(s"../amf-metadata/$path").asFile
      else Path(path).asFile
    props.load(new FileInputStream(versionsFile))
    props.entrySet().asScala.map(e => e.getKey.toString -> e.getValue.toString).toMap
  }

  def currentProjectUsesSourceMode: Boolean = {
    val absolutePath = Path("").asFile.getAbsolutePath
    val endingDir = absolutePath.split("/").last
    endingDir.equalsIgnoreCase("amf-runner") || endingDir.equalsIgnoreCase("amf-TCKutor")
  }
}
