package amf.transform.canonical

import amf.core.metamodel.Field
import amf.core.metamodel.Type._
import amf.core.metamodel.domain.extensions.DomainExtensionModel
import amf.core.metamodel.domain.{ModelDoc, ModelVocabularies}
import amf.core.vocabulary.Namespace

private[amf] object CanonicalWebAPISpecExtraModel {
  val DesignLinkTargetField = Field(
    Iri,
    Namespace.Document + "design-link-target",
    ModelDoc(ModelVocabularies.AmlDoc, "design link target", "URI of the linked element linked at design time"))

  val DesignAnnotationField = Field(
    Array(DomainExtensionModel),
    Namespace.Document + "designAnnotation",
    ModelDoc(ModelVocabularies.AmlDoc,
             "design annotation",
             "Extensions provided for a particular domain element during design time")
  )

  val DataPropertiesField = Field(Array(PropertyNodeModel),
                                  Namespace.Data + "properties",
                                  ModelDoc(ModelVocabularies.Data, "properties", "properties in a dynamic object"))
}
