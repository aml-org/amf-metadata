package amf.client

import amf.apicontract.client.common.ProvidedMediaType
import amf.apicontract.client.platform.RAMLConfiguration
import amf.core.client.platform.config.RenderOptions
import amf.core.client.platform.execution.{DefaultExecutionEnvironment, ExecutionEnvironment}
import amf.core.internal.convert.NativeOpsFromJvm
import amf.io.FileAssertionTest
import amf.transform.canonical.CanonicalTransform
import org.scalatest.funsuite.AsyncFunSuite

import scala.concurrent.{ExecutionContext, Future}

class ClientCanonicalTransformTest extends AsyncFunSuite with NativeOpsFromJvm with FileAssertionTest{

  private val environment: ExecutionEnvironment = DefaultExecutionEnvironment()
  implicit val context: ExecutionContext = environment._internal.context

  test("Test client interfaces of the canonical transoformation") {
    val file = "file://transform/src/test/resources/client/api.raml"
    val golden = "file://transform/src/test/resources/client/webapi.canonical.jsonld"
    val options = new RenderOptions().withCompactUris.withSourceMaps.withPrettyPrint.withoutFlattenedJsonLd
    for {
      config   <- RAMLConfiguration.RAML10().withRenderOptions(options).withDialect(CanonicalTransform.CANONICAL_WEBAPI_DIALECT).asFuture
      client   <- Future.successful(config.createClient())
      unit     <- client.parse(file).asFuture.map(_.baseUnit)
      transformed <- new CanonicalWebAPISpecTransformer().transform(unit, config).asFuture
      render <- Future.successful(client.render(transformed, ProvidedMediaType.AMF))
      actual <- writeTemporaryFile(golden)(render)
      r      <- assertDifferences(actual, golden)
    } yield {
      r
    }
  }
}
