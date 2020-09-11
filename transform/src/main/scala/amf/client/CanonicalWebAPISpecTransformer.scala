package amf.client

import amf.client.convert.CoreClientConverters
import amf.client.convert.CoreClientConverters.BaseUnitMatcher._
import amf.client.convert.CoreClientConverters.ClientFuture
import amf.client.execution.{DefaultExecutionEnvironment, ExecutionEnvironment}
import amf.client.model.document.BaseUnit
import amf.transform.canonical.{CanonicalWebAPISpecTransformer => CanonicalTransformation}

import scala.concurrent.ExecutionContext

class CanonicalWebAPISpecTransformer() {

  def transform(unit: BaseUnit): ClientFuture[BaseUnit] = transform(unit, DefaultExecutionEnvironment())

  def transform(unit: BaseUnit, executionEnvironment: ExecutionEnvironment): ClientFuture[BaseUnit] = {
    val executionContext: ExecutionContext = executionEnvironment._internal.context
    val transformed = CanonicalTransformation.transform(asInternal(unit))
    CoreClientConverters.InternalFutureOps(transformed)(CoreClientConverters.BaseUnitMatcher, executionContext).asClient
  }
}
