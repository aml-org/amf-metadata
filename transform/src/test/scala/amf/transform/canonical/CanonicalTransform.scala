package amf.transform.canonical

import amf.apicontract.client.scala.{AMFConfiguration, APIConfiguration}
import amf.core.client.scala.errorhandling.UnhandledErrorHandler
import amf.core.client.scala.model.document.BaseUnit
import amf.core.internal.remote.Hint
import amf.core.internal.unsafe.PlatformSecrets

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CanonicalTransform extends CanonicalTransform

trait CanonicalTransform extends PlatformSecrets {

  val CANONICAL_WEBAPI_DIALECT: String = "file://vocabulary/src/main/resources/dialects/canonical_webapi_spec.yaml"

  def canonicalTransform(webApiPath: String, conf: AMFConfiguration): Future[BaseUnit] = {
    for {
      unit        <- conf.createClient().parse(webApiPath).map(_.bu)
      transformed <- CanonicalWebAPISpecTransformer.transform(unit, conf)
    } yield {
      transformed
    }
  }
}
