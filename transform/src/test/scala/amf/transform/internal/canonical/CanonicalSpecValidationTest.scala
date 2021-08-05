package amf.transform.internal.canonical

import amf.aml.client.scala.AMLConfiguration
import amf.apicontract.client.scala.APIConfiguration
import amf.core.client.common.validation.ProfileName
import amf.core.client.scala.validation.AMFValidationReport
import amf.core.internal.remote.{Hint, Spec}
import amf.transform.internal.canonical.CanonicalDialectRegistration.registerDialect
import amf.transform.internal.canonical.CanonicalSpecValidationTest.canonicalConfig
import org.scalatest.{Assertion, BeforeAndAfterAll}
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future}

object CanonicalSpecValidationTest {
  val canonicalConfig: Future[AMLConfiguration] = registerDialect(AMLConfiguration.predefined())
}

trait CanonicalSpecValidationTest extends AsyncFunSuite with CanonicalTransform with Matchers with BeforeAndAfterAll{

  val basePath: String
  val apiPaths: Set[String]
  val spec: Spec
  val ignoredApis: Set[String] = Set()

  override implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  def runValidations(): Unit = {
    apiPaths.foreach { apiPath =>
      val message = s"Test canonical validation for $apiPath with ${spec.toString}"
      if (ignoredApis.contains(apiPath)) ignore(message) { validate(basePath + apiPath) }
      else test(message) { validate(basePath + apiPath)}
    }
  }

  def handleReport(report: AMFValidationReport): Assertion =
    if (!report.conforms) fail("Report not conforms:\n" + report.toString)
    else if (report.results.nonEmpty) fail("Report conforms but there is results, probably some warnings\n:" + report.toString)
    else succeed

  case class Fixture(apiPath: String, hint: Hint)

  private def validate(apiPath: String): Future[Assertion] = {
    for {
      config <- canonicalConfig
      transformed <- canonicalTransform(apiPath, config)
      report <- config.baseUnitClient().validate(transformed)
    } yield {
      handleReport(report)
    }
  }
}
