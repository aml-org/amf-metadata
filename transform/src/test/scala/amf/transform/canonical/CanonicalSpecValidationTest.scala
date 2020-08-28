package amf.transform.canonical

import amf.ProfileName
import amf.client.parse.DefaultParserErrorHandler
import amf.core.AMF
import amf.core.remote.Hint
import amf.core.services.RuntimeValidator
import amf.core.validation.AMFValidationReport
import amf.core.validation.core.ValidationReport
import amf.facades.{AMFCompiler, Validation}
import org.scalatest.{Assertion, BeforeAndAfterAll}
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future}

trait CanonicalSpecValidationTest extends AsyncFunSuite with CanonicalTransform with Matchers with BeforeAndAfterAll{

  val basePath: String
  val apiPaths: Set[String]
  val hint: Hint
  val ignoredApis: Set[String] = Set()

  override implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  def runValidations(): Unit = {
    apiPaths.foreach { apiPath =>
      val message = s"Test canonical validation for $apiPath with ${hint.vendor.toString}"
      if (ignoredApis.contains(apiPath)) ignore(message) { validate(basePath + apiPath, hint) }
      else test(message) { validate(basePath + apiPath, hint)}
    }
  }

  def handleReport(report: AMFValidationReport): Assertion =
    if (!report.conforms) fail("Report not conforms:\n" + report.toString)
    else if (report.results.nonEmpty) fail("Report conforms but there is results, probably some warnings\n:" + report.toString)
    else succeed

  case class Fixture(apiPath: String, hint: Hint)

  private def validate(apiPath: String, hint: Hint): Future[Assertion] = {
    for {
      transformed <- canonicalTransform(apiPath, hint)
      report <- {
        RuntimeValidator(
          transformed,
          ProfileName(CanonicalWebAPISpecTransformer.CANONICAL_WEBAPI_NAME)
        )
      }
    } yield {
      handleReport(report)
    }
  }
}
