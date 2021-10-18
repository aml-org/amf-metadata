package amf.transform.client.platform

import amf.aml.client.platform.BaseAMLConfiguration
import amf.apicontract.internal.convert.ApiClientConverters._
import amf.core.client.platform.model.document.BaseUnit
import amf.transform.internal.canonical.{CanonicalWebAPISpecTransformer => CanonicalTransformation}

class CanonicalWebAPISpecTransformer() {

  def transform(unit: BaseUnit, config: BaseAMLConfiguration): BaseUnit = {
    CanonicalTransformation.transform(unit, config._internal)
  }
}
