package amf.transform.internal.canonical

import amf.core.client.scala.model.domain.AmfObject
import amf.core.client.scala.vocabulary.{Namespace, ValueType}
import amf.core.internal.metamodel.Field
import amf.core.internal.metamodel.domain.{DataNodeModel, DomainElementModel, ModelDoc, ModelVocabularies}

private[transform] object PropertyNodeModel extends DomainElementModel {

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

