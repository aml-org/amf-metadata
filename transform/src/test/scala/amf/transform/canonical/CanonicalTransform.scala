package amf.transform.canonical

import amf.client.parse.DefaultParserErrorHandler
import amf.core.AMF
import amf.core.remote.{Hint, RamlYamlHint}
import amf.core.unsafe.PlatformSecrets
import amf.facades.{AMFCompiler, Validation}

import scala.concurrent.ExecutionContext

import scala.concurrent.ExecutionContext.Implicits.global

object CanonicalTransform extends CanonicalTransform

trait CanonicalTransform extends PlatformSecrets {

  val CANONICAL_WEBAPI_DIALECT: String = "file://vocabulary/src/main/resources/dialects/canonical_webapi_spec.yaml"

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
}