package amf.transform.internal.canonical

import amf.core.client.scala.model.domain.DomainElement
import amf.core.internal.metamodel.Obj
import amf.core.internal.parser.domain.{Annotations, Fields}
import org.yaml.model.YPart

// This model is just to reify the dynamic properties in an
// ObjectNode
private[transform] class PropertyNode(override val fields: Fields, val annotations: Annotations) extends DomainElement {
  override def meta: Obj           = PropertyNodeModel
  override def componentId: String = "/property"
}

private[transform] object PropertyNode {
  def apply(): PropertyNode = apply(Annotations())

  def apply(ast: YPart): PropertyNode = apply(Annotations(ast))

  def apply(annotations: Annotations): PropertyNode =
    new PropertyNode(Fields(), annotations)
}
