package amf.transform.canonical

import amf.client.parse.DefaultParserErrorHandler
import amf.core.AMF
import amf.core.parser.errorhandler.UnhandledParserErrorHandler
import amf.core.remote.{Hint, RamlYamlHint, VocabularyYamlHint}
import amf.core.unsafe.PlatformSecrets
import amf.facades.{AMFCompiler, Validation}
import amf.plugins.document.vocabularies.AMLPlugin
import amf.plugins.document.vocabularies.model.document.Dialect

import scala.concurrent.{ExecutionContext, Future}

trait CanonicalTransform extends PlatformSecrets {

  private val CANONICAL_WEBAPI_DIALECT: String = "file://vocabulary/src/main/resources/dialects/canonical_webapi_spec.yaml"
  implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  def canonicalTransform(webApiPath: String, hint: Hint = RamlYamlHint, dialect: DialectRegistration = CanonicalDialectRegistration()) =
    for {
      _           <- AMF.init()
      _           <- Validation(platform)
      unit        <- AMFCompiler(webApiPath, platform, hint, eh = DefaultParserErrorHandler()).build()
      _           <- dialect.registerDialect()
      transformed <- CanonicalWebAPISpecTransformer.transform(unit)
    } yield {
      transformed
    }

  sealed trait DialectRegistration {
    def registerDialect(): Future[Unit]
  }

  case class UnregisterDialectRegistration() extends DialectRegistration {
    override def registerDialect(): Future[Unit] = Future.successful(AMLPlugin().registry.unregisterDialect(CANONICAL_WEBAPI_DIALECT))
  }

  case class CanonicalDialectRegistration() extends DialectRegistration {

    def registerDialect(): Future[Unit] =
      for {
        _ <- Future.successful(amf.Core.registerPlugin(AMLPlugin))
        d <- AMFCompiler(CANONICAL_WEBAPI_DIALECT, platform, VocabularyYamlHint, eh = UnhandledParserErrorHandler).build()
      } yield {
        AMLPlugin().registry.resolveRegisteredDialect(d.asInstanceOf[Dialect].header)
      }
  }
}
