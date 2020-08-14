package amf.transform.canonical

import amf.ProfileName
import amf.core.AMF
import amf.core.emitter.RenderOptions
import amf.core.model.document.ExternalFragment
import amf.core.remote._
import amf.core.services.RuntimeValidator
import amf.helpers.AMFRenderer
import amf.io.FunSuiteCycleTests
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future}

class E2ECanonicalWebApiDialectTest extends FunSuiteCycleTests with CanonicalTransform with Matchers {

  override def basePath: String        = "file://transform/src/test/resources/transformations/"

  override implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  val tests: Seq[String] = Seq(
    "simple/api.raml",
    "annotations/api.raml",
    "macros/api.raml",
    "modular/api.raml",
    "security/api.raml",
    "declares/api.raml",
    "tuple-shape-schema/api.raml",
    "raml-extension/api.raml",
    "raml-overlay/api.raml",
    //    "modular-recursion/api.raml"
  )

  tests.foreach { input =>
    val golden = input.replace("api.raml", "webapi")
    test(s"Test '$input' for WebAPI dialect transformation and yaml/json rendering") {
      checkCanonicalDialectTransformation(input, golden, shouldTransform = false)
    }
  }

  test("Test that canonical transformer only accepts Documents") {
    val unit = ExternalFragment()
    assertThrows[DocumentExpectedException](CanonicalWebAPISpecTransformer.transform(unit))
  }

  test("Test that canonical transform raises exception on Recursive Unit") {
    recoverToSucceededIf[RecursiveUnitsPresentException](
      canonicalTransform(s"${basePath}/recursive-unit/root.json", OasJsonHint))
  }

  test("Test canonical tranformation throws exception if dialect is not found") {
    recoverToSucceededIf[CanonicalDialectNotFoundException](
      AMF.init().flatMap(_ => canonicalTransform(s"${basePath}simple/api.raml", RamlYamlHint, UnregisterDialectRegistration())))
  }

  def checkCanonicalDialectTransformation(source: String,
                                          target: String,
                                          shouldTransform: Boolean): Future[Assertion] = {
    val amfWebApi  = basePath + source
    val goldenYaml = s"$basePath$target.yaml"
    val goldenJson = s"$basePath$target.json"

    for {
      transformed <- canonicalTransform(amfWebApi)
      yamlDiffOk <- diff(goldenYaml) { () =>
        new AMFRenderer(transformed, Vendor.AML, RenderOptions().withNodeIds, Some(Syntax.Yaml)).renderToString
      }
      jsonDiffOk <- diff(goldenJson) { () =>
        new AMFRenderer(transformed, Vendor.AMF, RenderOptions().withPrettyPrint, Some(Syntax.Json)).renderToString
      }
      report <- {
        RuntimeValidator(
          transformed,
          ProfileName(CanonicalWebAPISpecTransformer.CANONICAL_WEBAPI_NAME)
        )
      }
      reportOk <- assert(report.conforms)
    } yield {
      val assertions = Seq(yamlDiffOk, jsonDiffOk, reportOk)
      assert { assertions.forall(_ == succeed) }
    }
  }

  private def diff(golden: String)(render: () => Future[String]): Future[Assertion] = {
    if (shouldIgnore(golden)) {
      Future.successful(succeed)
    } else {
      for {
        actual <- render()
        tmp    <- writeTemporaryFile(golden)(actual)
        diff   <- assertDifferences(tmp, golden)
      } yield {
        diff
      }
    }
  }

  private def shouldIgnore(golden: String): Boolean = {
    fs.syncFile(s"$golden.ignore".stripPrefix("file://")).exists
  }
}
