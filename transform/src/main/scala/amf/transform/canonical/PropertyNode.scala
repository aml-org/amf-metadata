package amf.transform.canonical

import amf.core.metamodel.Obj
import amf.core.model.domain.DomainElement
import amf.core.parser.{Annotations, Fields}
import org.yaml.model.YPart

// This model is just to reify the dynamic properties in an
// ObjectNode
class PropertyNode(override val fields: Fields, val annotations: Annotations) extends DomainElement {
  override def meta: Obj           = PropertyNodeModel
  override def componentId: String = "/property"
}

// COMMMENTS
// COMMMENTS
// COMMMENTS
// COMMMENTS
// COMMMENTS

object PropertyNode {
  def apply(): PropertyNode = apply(Annotations())

  def apply(ast: YPart): PropertyNode = apply(Annotations(ast))

  def apply(annotations: Annotations): PropertyNode =
    new PropertyNode(Fields(), annotations)
}
