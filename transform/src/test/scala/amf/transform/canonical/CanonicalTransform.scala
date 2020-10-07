package amf.transform.canonical

import amf.core.AMF
import amf.core.model.document.BaseUnit
import amf.core.parser.errorhandler.UnhandledParserErrorHandler
import amf.core.remote.Syntax.{Json, PlainText, Yaml}
import amf.core.remote.{Cache, Context, Hint, RamlYamlHint}
import amf.core.services.RuntimeCompiler
import amf.core.unsafe.PlatformSecrets
import amf.facades.Validation
import amf.transform.canonical.CanonicalTransform.mediaType

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CanonicalTransform extends CanonicalTransform {

  def mediaType(hint: Hint) = hint.syntax match {
    case Yaml      => Some("application/yaml")
    case Json      => Some("application/json")
    case PlainText => Some("text/plain") // we cannot parse this?
    case _         => None
  }
}

trait CanonicalTransform extends PlatformSecrets {

  val CANONICAL_WEBAPI_DIALECT: String = "file://vocabulary/src/main/resources/dialects/canonical_webapi_spec.yaml"

  def canonicalTransform(webApiPath: String, hint: Option[Hint], dialect: DialectRegistration = CanonicalDialectRegistration()): Future[BaseUnit] =
    for {
      _           <- AMF.init()
      _           <- Validation(platform)
//      unit        <- AMFCompiler(webApiPath, platform, hint, eh = DefaultParserErrorHandler()).build()
      unit        <- RuntimeCompiler(webApiPath, mediaType = hint.flatMap(mediaType), hint.map(h => h.vendor.name), Context(platform), Cache(), errorHandler = UnhandledParserErrorHandler)
      _           <- dialect.registerDialect()
      transformed <- CanonicalWebAPISpecTransformer.transform(unit)
    } yield {
      transformed
    }
}