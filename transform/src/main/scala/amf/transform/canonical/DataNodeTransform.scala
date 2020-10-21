package amf.transform.canonical

import amf.core.metamodel.Obj
import amf.core.metamodel.domain.{DataNodeModel, ObjectNodeModel}
import amf.core.vocabulary.{Namespace, ValueType}
import amf.transform.canonical.CanonicalWebAPISpecExtraModel.DataPropertiesField
import amf.transform.canonical.CanonicalWebAPISpecTransformer.{DialectNode, TypeUri, defaultIri, querySubjectsWith}
import org.apache.jena.rdf.model.{Model, RDFNode, Resource}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

trait DataNodeTransform extends TransformHelpers {

  protected def transformDataNodes(typeMapping: Map[TypeUri, DialectNode], nativeModel: Model): Unit = {
    // we first remove the name from al ldata nodes
    // we first list all the data nodes
    val dataNodes = querySubjectsWith(nativeModel, Namespace.Rdf + "type", defaultIri(DataNodeModel)).toList.asScala
    dataNodes.foreach(removeDataNodeName(nativeModel, _))
    // Now we add the reified properties to dynamic object nodes
    val objectNodes = querySubjectsWith(nativeModel, Namespace.Rdf + "type", defaultIri(ObjectNodeModel))

    while (objectNodes.hasNext) {
      val nextDataNode = objectNodes.nextResource()
      val props: ListBuffer[(Resource, RDFNode)] = queryDynamicProperties(nativeModel, nextDataNode)

      props.foreach {
        case (property, propertyValue) =>
          val name  = propertyName(property)
          val reifiedProperty = nativeModel.createResource(nextDataNode.getURI + "_prop_" + name)

          // link parent node and the reified property
          nativeModel.add(
            nextDataNode,
            nativeModel.createProperty(DataPropertiesField.value.iri()),
            reifiedProperty
          )

          // name
          nativeModel.add(
            reifiedProperty,
            nativeModel.createProperty(DataNodeModel.Name.value.iri()),
            nativeModel.createLiteral(name)
          )

          // range
          nativeModel.add(
            reifiedProperty,
            nativeModel.createProperty(PropertyNodeModel.Range.value.iri()),
            propertyValue
          )

          // types
          nativeModel.add(
            reifiedProperty,
            nativeModel.createProperty((Namespace.Rdf + "type").iri()),
            nativeModel.createResource((Namespace.Meta + "DialectDomainElement").iri())
          )
          nativeModel.add(
            reifiedProperty,
            nativeModel.createProperty((Namespace.Rdf + "type").iri()),
            nativeModel.createResource(typeMapping(defaultIri(PropertyNodeModel)))
          )
      }

      props.foreach {
        case (property, propertyValue) => removeOldProperty(nativeModel, nextDataNode, property, propertyValue)
      }
    }
  }

  private def removeOldProperty(nativeModel: Model, nextDataNode: Resource, p: Resource, v: RDFNode) = {
    val toRemove = nativeModel.createStatement(nextDataNode, nativeModel.createProperty(p.getURI), v)
    nativeModel.remove(toRemove)
  }

  private def queryDynamicProperties(nativeModel: Model, nextDataNode: Resource) = {
    val propertiesIt = nativeModel.listStatements(nextDataNode, null, null)
    val props = mutable.ListBuffer[(Resource, RDFNode)]()
    while (propertiesIt.hasNext) {
      val nextStatement = propertiesIt.next()
      val nextProperty = nextStatement.getPredicate.asResource()
      val nextValue = nextStatement.getObject
      if (nextProperty.getURI.startsWith(Namespace.Data.base)) {
        props.+=((nextProperty, nextValue))
      }
    }
    props
  }

  private def propertyName(p: Resource) = {
    p.getURI.split("#").last.split("/").last
  }

  private def removeDataNodeName(nativeModel: Model, node: Resource) = {
    val propertyNames = nativeModel
      .listObjectsOfProperty(node, nativeModel.createProperty(DataNodeModel.Name.value.iri()))
      .toList.asScala
    propertyNames.foreach { name =>
      nativeModel.remove(
        nativeModel.createStatement(
          node,
          nativeModel.createProperty(DataNodeModel.Name.value.iri()),
          name
        ))
    }
  }
}
