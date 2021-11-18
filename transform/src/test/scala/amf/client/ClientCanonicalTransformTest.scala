package amf.client

import amf.apicontract.client.platform.RAMLConfiguration
import amf.core.client.platform.config.RenderOptions
import amf.core.client.platform.errorhandling.ErrorHandlerProvider
import amf.core.client.platform.execution.{DefaultExecutionEnvironment, ExecutionEnvironment}
import amf.core.internal.convert.NativeOpsFromJvm
import amf.core.internal.remote.Mimes
import amf.io.FileAssertionTest
import amf.transform.client.platform.CanonicalWebAPISpecTransformer
import amf.transform.internal.canonical.CanonicalTransform
import org.scalatest.funsuite.AsyncFunSuite

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class ClientCanonicalTransformTest extends AsyncFunSuite with NativeOpsFromJvm with FileAssertionTest{

  private val environment: ExecutionEnvironment = DefaultExecutionEnvironment()
  implicit val context: ExecutionContext = environment._internal.context

  test("Test client interfaces of the canonical transoformation") {
    val file = "file://transform/src/test/resources/client/api.raml"
    val golden = "file://transform/src/test/resources/client/webapi.canonical.jsonld"
    val options = new RenderOptions().withCompactUris().withSourceMaps().withPrettyPrint()
    for {
      config   <- RAMLConfiguration.RAML10().withErrorHandlerProvider(ErrorHandlerProvider.unhandled()).withRenderOptions(options).withDialect(CanonicalTransform.CANONICAL_WEBAPI_DIALECT).asFuture
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

  test("Performance") {
    var i = 0
    while(i<20) {
      val file = "file://transform/src/test/resources/client/api.raml"
      val golden = "file://transform/src/test/resources/client/webapi.canonical.jsonld"
      val options = new RenderOptions().withCompactUris().withSourceMaps().withPrettyPrint()
      val a = for {
        config <- RAMLConfiguration.RAML10().withErrorHandlerProvider(ErrorHandlerProvider.unhandled()).withRenderOptions(options).withDialect(CanonicalTransform.CANONICAL_WEBAPI_DIALECT).asFuture
        client <- Future.successful(config.baseUnitClient())
        unit <- client.parse(file).asFuture.map(_.baseUnit)
        transformed <- Future.successful {
          val s = System.currentTimeMillis()
          val t = new CanonicalWebAPISpecTransformer().transform(unit, config)
          val e = System.currentTimeMillis()
          println(s"Time = ${e - s}")
          t
        }
        render <- Future.successful(client.render(transformed, Mimes.`application/ld+json`))
        actual <- writeTemporaryFile(golden)(render)
        r <- assertDifferences(actual, golden)
      } yield {
        r
      }
      Await.result(a, Duration.Inf)
      i += 1
    }
    assert(true)
  }
}
