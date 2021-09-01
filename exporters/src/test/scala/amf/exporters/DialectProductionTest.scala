package amf.exporters

import amf.aml.client.scala.AMLConfiguration
import amf.core.client.scala.errorhandling.DefaultErrorHandler
import amf.core.internal.unsafe.PlatformSecrets
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

class DialectProductionTest extends AsyncFunSuite with PlatformSecrets with Matchers{

  val canonicalWebApiDialect = "vocabulary/src/main/resources/dialects/canonical_webapi_spec.yaml"

  test("Canonical Web Api Dialect should be valid according to AMF") {
    val config = AMLConfiguration.predefined()
    for {
      result <- config.baseUnitClient().parse(s"file://$canonicalWebApiDialect")
    } yield {
      result.results shouldBe empty
    }
  }
}
