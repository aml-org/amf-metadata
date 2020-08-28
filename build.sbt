name := "amf-metadata"
organization in ThisBuild := "com.github.amlorg"
scalaVersion in ThisBuild := "2.12.11"

lazy val amfVocabularyVersion = majorVersionOrSnapshot(2)
val amfCanonicalVersion       = versionOrSnapshot(1, 1)

val ivyLocal = Resolver.file("ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)

lazy val workspaceDirectory: File =
  sys.props.get("sbt.mulesoft") match {
    case Some(x) => file(x)
    case _       => Path.userHome / "mulesoft"
  }

lazy val amfClientLibJVM = "com.github.amlorg" %% "amf-client" % dependencies.amfVersion
lazy val amfClientRef    = ProjectRef(workspaceDirectory / "amf", "clientJVM")

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
    libraryDependencies ++= commonDependencies
  )
  .sourceDependency(amfClientRef, amfClientLibJVM)

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Exporters ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

lazy val exporters = project
  .in(file("exporters"))
  .dependsOn(transform)
  .settings(
    commonSettings,
    libraryDependencies ++= commonDependencies
  )
  .sourceDependency(amfClientRef, amfClientLibJVM)

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ Common ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

val commonSettings = Common.settings ++ Common.publish ++ Seq(
  organization := "com.github.amlorg",
  resolvers ++= List(ivyLocal, Common.releases, Common.snapshots, Resolver.mavenLocal),
  resolvers += "jitpack" at "https://jitpack.io",
  credentials ++= Common.credentials()
)

lazy val dependencies = new {
  val scalaTestVersion = "3.1.2"
  val amfVersion       = "4.4.0-SNAPSHOT"

  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % Test
}

lazy val commonDependencies = Seq(dependencies.scalaTest, amfClientLibJVM)

def majorVersionOrSnapshot(major: Int) = {
  lazy val branch = sys.env.get("BRANCH_NAME")
  if (branch.contains("master")) s"$major.0.0" else s"$major.1.0-SNAPSHOT"
}

def versionOrSnapshot(major: Int, minor: Int) = {
  lazy val branch = sys.env.get("BRANCH_NAME")
  if (branch.contains("master")) s"$major.$minor.0" else s"$major.${minor + 1}.0-SNAPSHOT"
}
