package amf.transform.internal.canonical

import amf.aml.client.scala.AMLConfiguration

import scala.concurrent.Future

object CanonicalDialectRegistration {
  def registerDialect(conf: AMLConfiguration): Future[AMLConfiguration] =
    conf.withDialect(CanonicalTransform.CANONICAL_WEBAPI_DIALECT)
}
