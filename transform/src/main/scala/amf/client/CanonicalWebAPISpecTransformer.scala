package amf.client

import amf.apicontract.client.scala.AMFConfiguration
import amf.core.client.platform.AMFGraphConfiguration
import amf.core.internal.convert.CoreClientConverters._
import amf.core.internal.convert.CoreClientConverters
import amf.core.internal.convert.CoreClientConverters.ClientFuture
import amf.core.client.platform.model.document.BaseUnit
import amf.transform.canonical.{CanonicalWebAPISpecTransformer => CanonicalTransformation}

import scala.concurrent.ExecutionContext

class CanonicalWebAPISpecTransformer() {

  def transform(unit: BaseUnit, config: AMFGraphConfiguration): ClientFuture[BaseUnit] = {
    val executionContext: ExecutionContext = config.getExecutionContext
    val transformed = CanonicalTransformation.transform(BaseUnitMatcher.asInternal(unit), AMFGraphConfigurationMatcher.asInternal(config))
    CoreClientConverters.InternalFutureOps(transformed)(BaseUnitMatcher, executionContext).asClient
  }
}
