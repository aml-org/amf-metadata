package amf.transform.internal.canonical

import amf.apicontract.client.scala.AMFConfiguration
import amf.core.client.scala.model.document.BaseUnit
import amf.core.internal.unsafe.PlatformSecrets
import amf.transform.client.scala.{ CanonicalWebAPISpecTransformer => ClientScalaTransformer }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CanonicalTransform extends CanonicalTransform

trait CanonicalTransform extends PlatformSecrets {

  val CANONICAL_WEBAPI_DIALECT: String = "file://vocabulary/src/main/resources/dialects/canonical_webapi_spec.yaml"

  def canonicalTransform(webApiPath: String, conf: AMFConfiguration): Future[BaseUnit] = {
    for {
      unit        <- conf.baseUnitClient().parse(webApiPath).map(_.baseUnit)
      transformed <- Future.successful(new ClientScalaTransformer().transform(unit, conf))
    } yield {
      transformed
    }
  }
}
