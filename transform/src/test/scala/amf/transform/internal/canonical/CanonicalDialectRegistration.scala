package amf.transform.internal.canonical

import amf.apicontract.client.scala.AMFConfiguration

import scala.concurrent.Future

object CanonicalDialectRegistration {
  def registerDialect(conf: AMFConfiguration): Future[AMFConfiguration] =
    conf.withDialect(CanonicalTransform.CANONICAL_WEBAPI_DIALECT)
}
