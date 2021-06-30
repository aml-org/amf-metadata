package amf.transform.client.platform

import amf.apicontract.internal.convert.ApiClientConverters._
import amf.core.client.platform.AMFGraphConfiguration
import amf.core.client.platform.model.document.BaseUnit
import amf.transform.internal.canonical.{CanonicalWebAPISpecTransformer => CanonicalTransformation}

class CanonicalWebAPISpecTransformer() {

  def transform(unit: BaseUnit, config: AMFGraphConfiguration): BaseUnit = {
    CanonicalTransformation.transform(unit, config)
  }
}
