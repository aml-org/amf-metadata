package amf.transform.internal.canonical

import amf.aml.client.scala.AMLConfiguration
import amf.apicontract.client.scala.APIConfiguration
import amf.core.client.common.validation.ProfileName
import amf.core.client.scala.config.RenderOptions
import amf.core.internal.remote.{AmlDialectSpec, Async20YamlHint, Hint, Mimes, Raml10YamlHint}
import amf.io.FunSuiteCycleTests
import amf.transform.internal.canonical.CanonicalDialectRegistration.registerDialect
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

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
    "raml-library/api.raml",
    "fragments/raml/annotation-declaration/api.raml",
    "fragments/raml/datatype/api.raml",
    "fragments/raml/documentation-item/api.raml",
    "fragments/raml/named-example/api.raml",
    "fragments/raml/resource-type/api.raml",
    "fragments/raml/security-scheme/api.raml",
    "fragments/raml/trait/api.raml",
//        "modular-recursion/api.raml"
  )

  tests.foreach { input =>
    val golden = input.replace("api.raml", "webapi")
    test(s"Test '$input' for WebAPI dialect transformation and yaml/json rendering") {
      checkCanonicalDialectTransformation(input, golden, Raml10YamlHint)
    }
  }

  test("Test that dialect instance with bindings has correct discriminator") {
    checkCanonicalDialectTransformation("message-bindings/api.yaml", "message-bindings/webapi", Async20YamlHint)
  }

  test("Test that dialect instance with operation traits has operation in extends") {
    checkCanonicalDialectTransformation("operation-trait/api.yaml", "operation-trait/webapi", Async20YamlHint)
  }

  test("Test that dialect instance with message traits has message in extends") {
    checkCanonicalDialectTransformation("message-trait/api.yaml", "message-trait/webapi", Async20YamlHint)
  }

  test("Test transformation of an ExternalFragment") {
    checkCanonicalDialectTransformation("fragments/external/fragment.yaml", "fragments/external/webapi", None)
  }

  test("Test that canonical transform raises exception on Recursive Unit") {
    val transformRecursive = {
      for {
        conf <- registerDialect(APIConfiguration.API())
        _ <- canonicalTransform(s"${basePath}/recursive-unit/root.json", conf)
      } yield assert(false)
    }
    recoverToSucceededIf[RecursiveUnitsPresentException](transformRecursive)
  }

  test("Test canonical transformation throws exception if dialect is not found") {
    recoverToSucceededIf[CanonicalDialectNotFoundException](
      // does not register dialect
      canonicalTransform(s"${basePath}simple/api.raml", APIConfiguration.API())
    )
  }

  test("Test canonical transform dialect has the webapi dialect source spec") {
    for {
      config <- registerDialect(APIConfiguration.API())
      transformed <- canonicalTransform(s"${basePath}/simple/api.raml", config)
    } yield {
      transformed.sourceSpec shouldBe Some(AmlDialectSpec("WebAPI Spec 1.0"))
      val profileFromSpec = transformed.sourceSpec.map(spec => ProfileName(spec.id)).get
      // TODO: This should be tested in a more black box way. Improve
      config.registry.constraintsRules.contains(profileFromSpec) shouldBe true
    }
  }

  def checkCanonicalDialectTransformation(source: String, target: String, hint: Hint = Raml10YamlHint): Future[Assertion] = checkCanonicalDialectTransformation(source, target, Some(hint))

  def checkCanonicalDialectTransformation(source: String, target: String, hint: Option[Hint]): Future[Assertion] = {
    val amfWebApi  = basePath + source
    val goldenYaml = s"$basePath$target.yaml"
    val goldenJson = s"$basePath$target.json"

    val loadedConfig = registerDialect(AMLConfiguration.predefined())

    for {
      config <- loadedConfig
      transformed <- canonicalTransform(amfWebApi, config)
      yamlDiffOk <- diff(goldenYaml) { () =>
        val client = config.withRenderOptions(RenderOptions().withNodeIds).baseUnitClient()
        client.render(transformed)
      }
      jsonDiffOk <- diff(goldenJson) { () =>
        val client = config.withRenderOptions(RenderOptions().withPrettyPrint).baseUnitClient()
        client.render(transformed, Mimes.`application/ld+json`)
      }
      report <- {
        config.baseUnitClient().validate(transformed)
      }
      reportOk <- assert(report.conforms)
    } yield {
      val assertions = Seq(yamlDiffOk, jsonDiffOk, reportOk)
      assert { assertions.forall(_ == succeed) }
    }
  }

  private def diff(golden: String)(render: () => String): Future[Assertion] = {
    if (shouldIgnore(golden)) {
      Future.successful(succeed)
    } else {
      for {
        actual <- Future.successful(render())
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
