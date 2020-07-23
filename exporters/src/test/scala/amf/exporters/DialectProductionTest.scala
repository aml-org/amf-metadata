package amf.exporters

import amf.client.parse.DefaultParserErrorHandler
import amf.core.parser.errorhandler.UnhandledParserErrorHandler
import amf.core.remote.VocabularyYamlHint
import amf.core.unsafe.PlatformSecrets
import amf.facades.{AMFCompiler, Validation}
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

class DialectProductionTest extends AsyncFunSuite with PlatformSecrets with Matchers{

  val canonicalWebApiDialect = "vocabulary/src/main/resources/dialects/canonical_webapi_spec.yaml"

  test("Canonical Web Api Dialect should be valid according to AMF") {
    val errorHandler = DefaultParserErrorHandler()
    for {
      _ <- Validation(platform)
      _ <- AMFCompiler(s"file://$canonicalWebApiDialect", platform, VocabularyYamlHint, eh = errorHandler)
        .build()
    } yield {
      errorHandler.getErrors shouldBe empty
    }
  }
}
