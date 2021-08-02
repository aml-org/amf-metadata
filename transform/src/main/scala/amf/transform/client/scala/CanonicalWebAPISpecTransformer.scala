package amf.transform.client.scala

import amf.aml.client.scala.AMLConfiguration
import amf.core.client.scala.AMFGraphConfiguration
import amf.core.client.scala.model.document.BaseUnit
import amf.transform.internal.canonical.{CanonicalWebAPISpecTransformer => CanonicalTransformation}

class CanonicalWebAPISpecTransformer {

  def transform(unit: BaseUnit, config: AMLConfiguration): BaseUnit = {
    CanonicalTransformation.transform(unit, config)
  }
}
