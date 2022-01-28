package amf.transform.internal.canonical

import amf.aml.client.scala.model.document.DialectInstance
import amf.apicontract.client.scala.{AMFConfiguration, OASConfiguration}
import amf.core.client.scala.config.RenderOptions
import amf.core.client.scala.model.document.BaseUnit
import amf.core.internal.remote.Mimes
import amf.io.FileAssertionTest
import amf.rdf.client.scala.RdfUnitConverter
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

class SemanticExtensionsCanonicalTransformTest extends AsyncFunSuite with FileAssertionTest with CanonicalTransform with Matchers {
  def basePath: String = "file://transform/src/test/resources/transformations/semantic"

  test("Semantic Extensions should be flattened after transform and emitted to JSON-LD") {
    val base = s"$basePath/obj-extension"
    for {
      (transformed, config) <- transformedModel(base)
      jsonld <- Future.successful(config.baseUnitClient().render(transformed, Mimes.`application/ld+json`))
      jsonldAsFile <- writeTemporaryFile(s"$base/transformed.jsonld")(jsonld)
      diff <- assertDifferences(jsonldAsFile, s"$base/transformed.jsonld")
    } yield {
      diff
    }
  }

  test("Semantic Extensions should be flattened after transform and emitted to RDF") {
    val base = s"$basePath/obj-extension"
    for {
      (transformed, config) <- transformedModel(base)
      rdf <- Future.successful(RdfUnitConverter.toNativeRdfModel(transformed, config, RenderOptions()).toN3())
      rdfAsFile <- writeTemporaryFile(s"$base/transformed.rdf")(rdf)
      diff <- assertDifferences(rdfAsFile, s"$base/transformed.rdf")
    } yield {
      diff
    }
  }

  test("Semantic Extension should be flattened after transform and available in memory") {
    val base = s"$basePath/obj-extension"
    for {
      (transformed, _) <- transformedModel(base)
    } yield {
      transformed.asInstanceOf[DialectInstance].encodes
        .graph
        .getObjectByProperty("http://a.ml/vocabularies/document#encodes").head.graph
        .getObjectByProperty("http://a.ml/vocabularies/apiContract#endpoint").head.graph
        .containsProperty("http://a.ml/vocabularies/apiContract#deprecated") shouldBe true
    }
  }

  test("Semantic Extension should be flattened after transform and available in memory with nested objects") {
    val base = s"$basePath/nested-obj-extension"
    for {
      (transformed, _) <- transformedModel(base)
    } yield {
      transformed.asInstanceOf[DialectInstance].encodes
        .graph
        .getObjectByProperty("http://a.ml/vocabularies/document#encodes").head.graph
        .getObjectByProperty("http://a.ml/vocabularies/apiContract#endpoint").head.graph
        .getObjectByProperty("http://a.ml/vocabularies/apiContract#deprecated").head.graph
        .getObjectByProperty("http://a.ml/vocabularies/common#date").head.graph
        .scalarByProperty("http://a.ml/vocabularies/common#day").head shouldBe "28"
    }
  }

  private def transformedModel(base: String): Future[(BaseUnit, AMFConfiguration)] = {
    val apiPath = s"$base/api.yaml"
    for {
      config <- loadExtensions(base, OASConfiguration.OAS30().withRenderOptions(RenderOptions().withPrettyPrint.withCompactUris))
      unit        <- config.baseUnitClient().parse(apiPath).map(_.baseUnit)
      transformed <- Future.successful(CanonicalWebAPISpecTransformer.transform(unit, config))
    } yield {
      (transformed, config)
    }
  }

  private def loadExtensions(base: String, config: AMFConfiguration): Future[AMFConfiguration] = {
    val pathToDialect = s"$base/dialect.yaml"
    config.withDialect(CanonicalTransform.CANONICAL_WEBAPI_DIALECT)
      .flatMap(_.withDialect(pathToDialect))
  }
}
