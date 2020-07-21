name := "amf-metadata"
organization in ThisBuild := "com.github.amlorg"
scalaVersion in ThisBuild := "2.12.11"

lazy val amfVocabularyVersion = majorVersionOrSnapshot(1)
val amfCanonicalVersion = versionOrSnapshot(1, 0)

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Root ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

lazy val root = project.in(file("."))

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Vocabulary ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

lazy val vocabulary = project
  .in(file("vocabulary"))
  .settings(
    commonSettings,
    name := "amf-vocabulary",
    version := amfVocabularyVersion
  )

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Canonical ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

lazy val transform = project
  .in(file("transform"))
  .dependsOn(vocabulary)
  .settings(
    commonSettings,
    name := "amf-transform",
    version := amfCanonicalVersion,
    libraryDependencies ++= commonDependencies ++ Seq(dependencies.apacheJena)
  )

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Exporters ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

lazy val exporters = project.in(file("exporters"))
  .dependsOn(transform)
  .settings(
    commonSettings,
    libraryDependencies ++= commonDependencies
  )

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Common ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

val commonSettings = Common.settings ++ Common.publish ++ Seq(
  organization := "com.github.amlorg",
  resolvers ++= List(Common.releases, Common.snapshots, Resolver.mavenLocal),
  resolvers += "jitpack" at "https://jitpack.io",
  credentials ++= Common.credentials()
)

lazy val dependencies = new {
  val scalaTestVersion="3.1.2"
  val amfVersion = "4.1.3"
  val jenaVersion = "3.11.0"

  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % Test
  val apacheJena = "org.apache.jena" % "jena-arq" % jenaVersion
  val amfClient = "com.github.amlorg" %% "amf-client" % amfVersion
}

lazy val commonDependencies = Seq(dependencies.scalaTest, dependencies.amfClient)

def majorVersionOrSnapshot(major: Int) = {
  lazy val branch = sys.env.get("BRANCH_NAME")
  if (branch.contains("master")) s"$major.0.0" else s"$major.0.0-SNAPSHOT"
}

def versionOrSnapshot(major: Int, minor: Int) = {
  lazy val build = sys.env.getOrElse("BUILD_NUMBER", "0")
  lazy val branch = sys.env.get("BRANCH_NAME")
  if (branch.contains("master")) s"$major.$minor.$build" else s"$major.${minor + 1}.0-SNAPSHOT"
}