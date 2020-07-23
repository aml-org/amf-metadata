package amf.transform.canonical

import amf.core.metamodel.Field
import amf.core.metamodel.domain.{DataNodeModel, DomainElementModel, ModelDoc, ModelVocabularies}
import amf.core.model.domain.AmfObject
import amf.core.vocabulary.{Namespace, ValueType}

object PropertyNodeModel extends DomainElementModel {

  val Range =
    Field(DataNodeModel, Namespace.Data + "range", ModelDoc(ModelVocabularies.Data, "range", "value for a property"))

  override def fields: List[Field]      = Range :: DataNodeModel.fields
  override val `type`: List[ValueType]  = Namespace.Data + "Property" :: DataNodeModel.`type`
  override def modelInstance: AmfObject = PropertyNode()

  override val doc: ModelDoc = ModelDoc(
    ModelVocabularies.Data,
    "Property Node",
    "Node that represents a dynamic property in a dynamic node"
  )
}

