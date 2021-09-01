package amf.client

import amf.apicontract.client.platform.RAMLConfiguration
import amf.core.client.platform.config.RenderOptions
import amf.core.client.platform.execution.{DefaultExecutionEnvironment, ExecutionEnvironment}
import amf.core.internal.convert.NativeOpsFromJvm
import amf.core.internal.remote.Mimes
import amf.io.FileAssertionTest
import amf.transform.client.platform.CanonicalWebAPISpecTransformer
import amf.transform.internal.canonical.CanonicalTransform
import org.scalatest.funsuite.AsyncFunSuite

import scala.concurrent.{ExecutionContext, Future}

class ClientCanonicalTransformTest extends AsyncFunSuite with NativeOpsFromJvm with FileAssertionTest{

  private val environment: ExecutionEnvironment = DefaultExecutionEnvironment()
  implicit val context: ExecutionContext = environment._internal.context

  test("Test client interfaces of the canonical transoformation") {
    val file = "file://transform/src/test/resources/client/api.raml"
    val golden = "file://transform/src/test/resources/client/webapi.canonical.jsonld"
    val options = new RenderOptions().withCompactUris().withSourceMaps().withPrettyPrint()
    for {
      config   <- RAMLConfiguration.RAML10().withRenderOptions(options).withDialect(CanonicalTransform.CANONICAL_WEBAPI_DIALECT).asFuture
      client   <- Future.successful(config.baseUnitClient())
      unit     <- client.parse(file).asFuture.map(_.baseUnit)
      transformed <- Future.successful(new CanonicalWebAPISpecTransformer().transform(unit, config))
      render <- Future.successful(client.render(transformed, Mimes.`application/ld+json`))
      actual <- writeTemporaryFile(golden)(render)
      r      <- assertDifferences(actual, golden)
    } yield {
      r
    }
  }
}
