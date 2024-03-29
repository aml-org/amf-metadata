package amf.transform.internal.canonical

import amf.core.client.scala.vocabulary.Namespace
import amf.core.internal.metamodel.domain.LinkableElementModel
import amf.transform.internal.canonical.CanonicalWebAPISpecExtraModel.DesignLinkTargetField
import amf.transform.internal.canonical.CanonicalWebAPISpecTransformer.{DialectNode, DomainElementUri, TypeUri}
import org.apache.jena.rdf.model.Model

import scala.collection.JavaConverters._

trait DomainElementTransform extends AnnotationTransform with TransformHelpers {
  protected def transformDomainElements(typeMapping: Map[TypeUri, DialectNode], nativeModel: Model): Unit = {
    domainElementsFrom(nativeModel).foreach { domainElement =>
      // we map types to dialect nodes and add them to the rdf:type property
      transformType(nativeModel, domainElement, typeMapping)

      // now we transform regular link-targets into design-link-targets so we can render them in a dialect without
      // triggering element dereference logic
      transformLink(nativeModel, domainElement)

      // same for custom domain properties
      // domain properties are generated as properties, now they will become
      // reified so we can list them
      transformAnnotations(nativeModel, typeMapping, domainElement)
    }
  }

  private def domainElementsFrom(nativeModel: Model): Seq[DomainElementUri] = {
    val domainElements = querySubjectsWith(nativeModel, Namespace.Rdf + "type", Namespace.Document + "DomainElement").toList.asScala
    val shapes = querySubjectsWith(nativeModel, Namespace.Rdf + "type", Namespace.Shapes + "Shape").toList.asScala

    domainElements ++ shapes map { _.getURI }
  }

  private def transformType(nativeModel: Model, domainElement: DomainElementUri, mapping: Map[TypeUri, DialectNode]): Unit = {
    val types = queryObjectsWith(nativeModel, domainElement, Namespace.Rdf + "type").asScala.toList
    val typesIterator = types.iterator

    // We need to deal with node shape inheritance
    // These flags allow us to track if we found any shape or shape in case
    // we cannot find a more specific shape
    var foundShape: Option[String]      = None
    var foundAnyShape: Option[String]   = None
    var foundArrayShape: Option[String] = None
    var foundApi: Option[String]        = None
    var found                           = false
    var mappedDialectNode               = ""

    def isNotShape(dialectNode: DialectNode) = {
      !dialectNode.endsWith("#/declarations/Shape") && !dialectNode.endsWith("#/declarations/AnyShape") && !dialectNode
        .endsWith("#/declarations/DataNode") && !dialectNode.endsWith("#/declarations/ArrayShape")
    }

    def isNotApi(dialectNode: DialectNode) = {
      !dialectNode.endsWith("#/declarations/API")
    }

    def isNotSetting(dialectNode: DialectNode) = {
      !dialectNode.endsWith("#/declarations/Settings")
    }

    def isNotAbstractNode(dialectNode: DialectNode) = {
      val abstractNodesSuffixes = Seq(
        "#/declarations/AbstractResponse",
        "#/declarations/AbstractRequest",
        "#/declarations/AbstractParameter",
        "#/declarations/AbstractPayload",
        "#/declarations/AbstractOperation"
      )

      !abstractNodesSuffixes.exists(suffix => dialectNode.endsWith(suffix))
    }

    while (typesIterator.hasNext) {
      val nextType = typesIterator.next().asResource().getURI
      mapping.get(nextType) match {
        case Some(dialectNode) =>
          // dealing with inheritance here
          if (isNotShape(dialectNode) && isNotApi(dialectNode) && isNotSetting(dialectNode) && isNotAbstractNode(dialectNode)) {
            found = true
            mappedDialectNode = dialectNode
          } else if (dialectNode.endsWith("#/declarations/Shape")) {
            foundShape = Some(dialectNode)
          } else if (dialectNode.endsWith("#/declarations/AnyShape")) {
            foundAnyShape = Some(dialectNode)
          } else if (dialectNode.endsWith("#/declarations/ArrayShape")) {
            foundArrayShape = Some(dialectNode)
          } else if (dialectNode.endsWith("#/declarations/API")) {
            foundApi = Some(dialectNode)
          }
        case _ => // ignore
      }
    }

    // Set the base shape node if we have find it and we didn't find anything more specific
    if (!found && foundArrayShape.isDefined) {
      mappedDialectNode = foundArrayShape.get
      found = true
    }
    if (!found && foundAnyShape.isDefined) {
      mappedDialectNode = foundAnyShape.get
      found = true
    }
    if (!found && foundShape.isDefined) {
      mappedDialectNode = foundShape.get
      found = true
    }
    if (!found && foundApi.isDefined) {
      mappedDialectNode = foundApi.get
      found = true
    }

    nativeModel.add(
      nativeModel.createResource(domainElement),
      nativeModel.createProperty((Namespace.Rdf + "type").iri()),
      nativeModel.createResource((Namespace.Meta + "DialectDomainElement").iri())
    )
    nativeModel.add(
      nativeModel.createResource(domainElement),
      nativeModel.createProperty((Namespace.Rdf + "type").iri()),
      nativeModel.createResource(mappedDialectNode)
    )
  }

  private def transformLink(nativeModel: Model, domainElement: DomainElementUri): Unit = {
    val linksIt = queryObjectsWith(nativeModel, domainElement, LinkableElementModel.TargetId.value)
    while (linksIt.hasNext) {
      val nextLink = linksIt.next()
      nativeModel.remove(
        nativeModel.createResource(domainElement),
        nativeModel.createProperty(LinkableElementModel.TargetId.value.iri()),
        nextLink
      )
      nativeModel.add(
        nativeModel.createResource(domainElement),
        nativeModel.createProperty(DesignLinkTargetField.value.iri()),
        nextLink
      )
    }
  }
}
