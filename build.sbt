import Versions.versions

name := "amf-metadata"
organization in ThisBuild := "com.github.amlorg"
scalaVersion in ThisBuild := "2.12.11"

val artifactVersions = new {
  val vocabularyVersion = versions("versions.yaml")("amf.vocabulary")
  val transformVersion = versions("versions.yaml")("amf.transform")
}

val ivyLocal = Resolver.file("ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)

lazy val workspaceDirectory: File =
  sys.props.get("sbt.mulesoft") match {
    case Some(x) => file(x)
    case _       => Path.userHome / "mulesoft"
  }

lazy val amfApiContractLibJVM = "com.github.amlorg" %% "amf-api-contract" % dependencies.amfVersion
lazy val amfApiContractRef    = ProjectRef(workspaceDirectory / "amf", "apiContractJVM")

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Root ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

lazy val root = project.in(file("."))
  .aggregate(vocabulary, transform, exporters)
  .settings(
    publish / aggregate := false
  )

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Vocabulary ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

lazy val vocabulary = project
  .in(file("vocabulary"))
  .settings(
    commonSettings,
    name := "amf-vocabulary",
    version := artifactVersions.vocabularyVersion
  ).disablePlugins(SonarPlugin)

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Canonical ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

lazy val transform = project
  .in(file("transform"))
  .dependsOn(vocabulary)
  .settings(
    commonSettings,
    name := "amf-transform",
    version := artifactVersions.transformVersion,
    libraryDependencies ++= commonDependencies
  )
  .sourceDependency(amfApiContractRef, amfApiContractLibJVM)
  .disablePlugins(SonarPlugin)

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Exporters ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

lazy val exporters = project
  .in(file("exporters"))
  .dependsOn(transform)
  .settings(
    commonSettings,
    libraryDependencies ++= commonDependencies,
    libraryDependencies += "org.reflections" % "reflections" % "0.9.12"
  )
  .sourceDependency(amfApiContractRef, amfApiContractLibJVM)
  .disablePlugins(SonarPlugin)

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Common ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

val commonSettings = Common.settings ++ Common.publish ++ Seq(
  organization := "com.github.amlorg",
  resolvers ++= List(ivyLocal, Common.releases, Common.snapshots, Resolver.mavenLocal),
  resolvers += "jitpack" at "https://jitpack.io",
  credentials ++= Common.credentials(),
  logBuffered in Test := false
)

lazy val dependencies = new {
  val scalaTestVersion = "3.1.2"
  val amfVersion       = versions("transform/dependencies.properties")("amf.apicontract")

  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % Test
}

lazy val commonDependencies = Seq(dependencies.scalaTest, amfApiContractLibJVM)

lazy val sonarUrl   = sys.env.getOrElse("SONAR_SERVER_URL", "Not found url.")
lazy val sonarToken = sys.env.getOrElse("SONAR_SERVER_TOKEN", "Not found token.")
lazy val branch = sys.env.getOrElse("BRANCH_NAME", "develop")

//enablePlugins(ScalaJSBundlerPlugin)

sonarProperties ++= Map(
  "sonar.login"                      -> sonarToken,
  "sonar.projectKey"                 -> "mulesoft.amf-metadata",
  "sonar.projectName"                -> "AMF-Metadata",
  "sonar.projectVersion"             -> artifactVersions.transformVersion,
  "sonar.sourceEncoding"             -> "UTF-8",
  "sonar.github.repository"          -> "mulesoft/amf-metadata",
  "sonar.branch.name"                -> branch,
  "sonar.scala.coverage.reportPaths" -> "transform/target/scala-2.12/scoverage-report/scoverage.xml,exporters/target/scala-2.12/scoverage-report/scoverage.xml",
  "sonar.sources"                    -> "transform/src/main/scala,exporters/src/main/scala",
  "sonar.tests"                      -> "transform/src/test/scala,exporters/src/test/scala"
)
