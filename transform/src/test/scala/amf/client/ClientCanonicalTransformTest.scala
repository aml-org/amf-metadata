package amf.client

import amf.client.execution.{DefaultExecutionEnvironment, ExecutionEnvironment}
import amf.client.parse.Raml10Parser
import amf.client.render.RenderOptions
import amf.convert.NativeOpsFromJvm
import amf.io.FileAssertionTest
import amf.transform.canonical.CanonicalDialectRegistration
import org.scalatest.funsuite.AsyncFunSuite

import scala.concurrent.ExecutionContext

class ClientCanonicalTransformTest extends AsyncFunSuite with NativeOpsFromJvm with FileAssertionTest{

  private val environment: ExecutionEnvironment = DefaultExecutionEnvironment()
  implicit val context: ExecutionContext = environment._internal.context

  test("Test client interfaces of the canonical transoformation") {
    val file = "file://transform/src/test/resources/client/api.raml"
    val golden = "file://transform/src/test/resources/client/webapi.canonical.jsonld"
    val options = new RenderOptions().withCompactUris.withSourceMaps.withPrettyPrint.withoutFlattenedJsonLd
    for {
      _        <- AMF.init().asFuture
      _        <- CanonicalDialectRegistration().registerDialect()
      unit     <- new Raml10Parser().parseFileAsync(file).asFuture
      transformed <- new CanonicalWebAPISpecTransformer().transform(unit, environment).asFuture
      render <- amf.Core.generator("AMF Graph", "application/ld+json").generateString(transformed, options).asFuture
      actual <- writeTemporaryFile(golden)(render)
      r      <- assertDifferences(actual, golden)
    } yield {
      r
    }
  }
}
