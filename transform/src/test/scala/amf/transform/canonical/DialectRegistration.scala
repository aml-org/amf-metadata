package amf.transform.canonical

import amf.client.parse.DefaultParserErrorHandler
import amf.core.remote.VocabularyYamlHint
import amf.core.unsafe.PlatformSecrets
import amf.facades.AMFCompiler
import amf.plugins.document.vocabularies.AMLPlugin
import amf.plugins.document.vocabularies.model.document.Dialect

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait DialectRegistration {
  def registerDialect(): Future[Unit]
}

case class UnregisterDialectRegistration() extends DialectRegistration {
  override def registerDialect(): Future[Unit] = Future.successful(AMLPlugin().registry.unregisterDialect(CanonicalTransform.CANONICAL_WEBAPI_DIALECT))
}

case class CanonicalDialectRegistration() extends DialectRegistration with PlatformSecrets {

  def registerDialect(): Future[Unit] =
    for {
      _ <- Future.successful(amf.Core.registerPlugin(AMLPlugin))
      d <- AMFCompiler(CanonicalTransform.CANONICAL_WEBAPI_DIALECT, platform, VocabularyYamlHint, eh = DefaultParserErrorHandler.withRun()).build()
    } yield {
      AMLPlugin().registry.resolveRegisteredDialect(d.asInstanceOf[Dialect].header)
    }
}