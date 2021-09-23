package amf.transform.internal.canonical

import amf.aml.client.scala.AMLConfiguration
import amf.aml.client.scala.model.document.DialectInstance
import amf.aml.internal.entities.AMLEntities
import amf.apicontract.client.scala.RAMLConfiguration
import amf.core.client.scala.config.RenderOptions
import amf.core.client.scala.errorhandling.UnhandledErrorHandler
import amf.core.internal.remote.Mimes
import amf.io.FileAssertionTest
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

class RoundTripCanonicalWebApiDialectTest extends AsyncFunSuite with FileAssertionTest with Matchers with CanonicalTransform {

  test("Round trip canonical dialect validation") {
    val file = "file://transform/src/test/resources/americans-flights-api-1.0.2-raml/americans-flights-api.raml"
    for {
      config <- AMLConfiguration.predefined().withDialect(CanonicalTransform.CANONICAL_WEBAPI_DIALECT)
      canonical <- canonicalTransform(file, config)
      jsonld <- Future { config.baseUnitClient().render(canonical, Mimes.`application/ld+json`) }
      parsedCanonical <- config.baseUnitClient().parseContent(jsonld, Mimes.`application/ld+json`)
      report <- config.baseUnitClient().validate(parsedCanonical.baseUnit)
    } yield {
      canonical shouldBe a[DialectInstance]
      parsedCanonical.conforms shouldBe true
      report.conforms shouldBe true
    }
  }
}
