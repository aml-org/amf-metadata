package amf.transform.canonical

import amf.core.metamodel.Obj
import amf.core.metamodel.document.{DocumentModel, ExternalFragmentModel, ModuleModel}
import amf.core.metamodel.domain._
import amf.core.metamodel.domain.extensions.{CustomDomainPropertyModel, DomainExtensionModel}
import amf.core.model.document.{BaseUnit, Document}
import amf.core.parser.errorhandler.UnhandledParserErrorHandler
import amf.core.plugin.{CorePlugin, PluginContext}
import amf.core.rdf.RdfModelParser
import amf.core.unsafe.PlatformSecrets
import amf.core.vocabulary.{Namespace, ValueType}
import amf.plugins.document.graph.AMFGraphPlugin
import amf.plugins.document.vocabularies.AMLPlugin
import amf.plugins.document.vocabularies.metamodel.domain.NodeMappingModel
import amf.plugins.document.vocabularies.model.document.Dialect
import amf.plugins.document.vocabularies.model.domain.NodeMapping
import amf.plugins.document.webapi.Raml10Plugin
import amf.plugins.document.webapi.metamodel.{ExtensionModel, OverlayModel}
import amf.plugins.domain.shapes.DataShapesDomainPlugin
import amf.plugins.domain.webapi.WebAPIDomainPlugin
import amf.transform.canonical.CanonicalWebAPISpecExtraModel._
import amf.transform.jena.JenaUtils.all
import org.apache.jena.rdf.model.{Model, RDFNode, ResIterator, Resource, Statement}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

private[amf] object CanonicalWebAPISpecTransformer extends PlatformSecrets {
  type DomainElementUri = String
  type TypeUri          = String
  type DialectNode      = String

  val CANONICAL_WEBAPI_NAME = "WebAPI Spec 1.0"

  // Properties that will be inserted in the graph
  val REPO_INTERNAL_REF   = "http://anypoint.com/vocabs/digital-repository#internalReference"
  val REPO_ASSET_LOCATION = "http://anypoint.com/vocabs/digital-repository#location"
  val REPO_LINK_TARGET    = "http://anypoint.com/vocabs/digital-repository#link-target"
  val REPO_LINK_LABEL     = "http://anypoint.com/vocabs/digital-repository#link-label"
  val REPO_EXTENDS        = "http://anypoint.com/vocabs/digital-repository#extends"

  protected def findWebAPIDialect: Option[Dialect] =
    AMLPlugin().registry.allDialects().find(_.nameAndVersion() == CANONICAL_WEBAPI_NAME)

  /**
    * Creates a map from the node mapping in the canonical web api dialect to the mapped
    * class in the WebAPI vocabulary
    * @return
    */
  protected def buildCanonicalClassMapping: Map[TypeUri, DialectNode] = {
    findWebAPIDialect match {
      case Some(webApiDialect) =>
        val nodeMappings = webApiDialect.declares.collect { case mapping: NodeMapping => mapping }
        nodeMappings.foldLeft(Map[String, String]()) {
          case (acc, mapping) =>
            acc + (mapping.nodetypeMapping.value() -> mapping.id)
        }
      case None => throw CanonicalDialectNotFoundException("Cannot find WebAPI 1.0 Dialect in Dialect registry")
    }
  }

  /**
    * Renames the URI of a resource node in the RDF graph: incoming and outgoing edges
    */
  def renameResource(source: String, target: String, nativeModel: Model): Unit = {
    // remove matching S(source)-p-o statements, add S(target)-p-o statements
    val subjectsIterator = nativeModel.listStatements(nativeModel.createResource(source), null, null)
    val acc              = mutable.ArrayBuffer[Statement]()
    while (subjectsIterator.hasNext) {
      val nextStatement = subjectsIterator.nextStatement()
      acc += nextStatement
    }
    acc.foreach { st =>
      nativeModel.remove(st)
      val updatedStatement = nativeModel.createStatement(
        nativeModel.createResource(target),
        st.getPredicate,
        st.getObject
      )
      nativeModel.add(updatedStatement)
    }

    // remove matching s-p-O(source) statements, add s-p-O(target) statements
    val objectsIterator = nativeModel.listStatements(null, null, nativeModel.createResource(source))
    acc.clear()
    while (objectsIterator.hasNext) {
      val nextStatement = objectsIterator.nextStatement()
      acc += nextStatement
    }
    acc.foreach { st =>
      nativeModel.remove(st)
      val updatedStatement = nativeModel.createStatement(
        st.getSubject,
        st.getPredicate,
        nativeModel.createResource(target)
      )
      nativeModel.add(updatedStatement)
    }
  }

  private def mapBaseUnits(unit: String, dialect: Dialect, nativeModel: Model) = {
    val unitResource = nativeModel.createResource(unit)

    // Process and remove old types

    val allTypesIterator = nativeModel.listObjectsOfProperty(
      unitResource,
      nativeModel.createProperty((Namespace.Rdf + "type").iri())
    )
    val allTypes = mutable.ArrayBuffer[String]()
    while (allTypesIterator.hasNext) {
      allTypes += allTypesIterator.next().asResource().getURI
    }

    val mappedDocumentType = if (allTypes.contains((Namespace.ApiContract + "Extension").iri())) {
      defaultIri(ExtensionModel)
    } else if (allTypes.contains((Namespace.ApiContract + "Overlay").iri())) {
      defaultIri(OverlayModel)
    } else if (allTypes.contains((Namespace.Document + "Document").iri())) {
      defaultIri(DocumentModel)
    } else if (allTypes.contains((Namespace.Document + "Module").iri())) {
      defaultIri(ModuleModel)
    } else if (allTypes.contains((Namespace.Document + "ExternalFragment").iri())) {
      defaultIri(ExternalFragmentModel)
    } else {
      val cleanTypes = allTypes.filter { t =>
        t != (Namespace.Document + "Unit").iri() &&
        t != (Namespace.Document + "Document").iri() &&
        t != (Namespace.Document + "Fragment").iri()
      }

      cleanTypes.headOption match {
        case Some(cleanType) => cleanType
        case None            =>
          // Should never have to reach this point. May be reached if AMF model is updated to have another Document/Unit/Fragment type.
          throw UnknownDocumentTypeMappingException("Couldn't find document type for unit")
      }
    }

    dialect.declares.find { nodeMapping =>
      nodeMapping.id.split("#").last.split("/").last == mappedDocumentType.split("#").last
    } match {
      case Some(nodeMapping) =>
        nativeModel.remove(
          unitResource,
          nativeModel.createProperty((Namespace.Rdf + "type").iri()),
          nativeModel.createResource(nodeMapping.id)
        )
      case _ =>
        // TODO: should this println be here or should it be part of a logger or even an exception?
        println(s"Couldn't find node mapping for $mappedDocumentType")
    }

    // now is a dialect domain element
    nativeModel.add(
      unitResource,
      nativeModel.createProperty((Namespace.Rdf + "type").iri()),
      nativeModel.createResource((Namespace.Meta + "DialectDomainElement").iri())
    )
    // still a domain element, TODO: do we need this one?
    nativeModel.add(
      unitResource,
      nativeModel.createProperty((Namespace.Rdf + "type").iri()),
      nativeModel.createResource((Namespace.Document + "DomainElement").iri())
    )

    // remove the old types
    allTypes.foreach { t =>
      nativeModel.remove(
        unitResource,
        nativeModel.createProperty((Namespace.Rdf + "type").iri()),
        nativeModel.createResource(t)
      )
    }
    // ad the new asset type property
    nativeModel.add(
      unitResource,
      nativeModel.createProperty((Namespace.Rdf + "type").iri()),
      nativeModel.createResource(mappedDocumentType)
    )

    // add the new asset location property
    nativeModel.add(
      unitResource,
      nativeModel.createProperty(REPO_ASSET_LOCATION),
      nativeModel.createLiteral(unit)
    )

    // add the extendedFrom property to track baseunit node extension
    dialect.declares.find(_.id.endsWith("ParsedUnit")) match {
      case Some(parsedUnitNode) =>
        nativeModel.add(
          unitResource,
          nativeModel.createProperty(NodeMappingModel.ResolvedExtends.value.iri()),
          nativeModel.createLiteral(parsedUnitNode.id)
        )
      case _ => // ignore
    }
  }

  /**
    * Transforms the doc:Units in the graph into the domain elements for assets in the canonical web api spec dialect
    */
  def preProcessUnits(nativeModel: Model): String = {
    // we need the dialect ID in this particular instance of AMF
    val dialect = findWebAPIDialect.get

    // we save the top level document we will not transform into a domain level asset
    val topLevelUnitUri = queryTopLevelDocument(nativeModel)

    // get all the document units
    val unitUris = querySubjectsWith(nativeModel, Namespace.Rdf + "type", Namespace.Document + "Unit")
      .toList
      .asScala
      .map(_.getURI)

    // for each document unit that is not the root one, we transform that into the canonical webpi spec asset fragment node
    // defined in the SpecDocument node mapping schema
    val (units, recursives) = unitUris.partition(!isRecursiveBaseUnit(_))
    units.foreach { unit =>
      val unitResource = nativeModel.createResource(unit)

      // Let's manipulate the @type of the unit to match the dialect expectations
      mapBaseUnits(unit, dialect, nativeModel)
    }

    if (recursives.nonEmpty)
      throw RecursiveUnitsPresentException(s"Recursive units are not supported in Canonical Transform")

    introduceNewTopLevelDocument(nativeModel, topLevelUnitUri)
    topLevelUnitUri
  }

  private def introduceNewTopLevelDocument(nativeModel: Model, topLevelUnitUri: String) = {
    // we rename the old top level document (now a domain element)
    val encodedElement = getUriNotPresentInModel(topLevelUnitUri)
    // rename so we can reuse the old main root URI
    renameResource(topLevelUnitUri, encodedElement, nativeModel)
    val nextTopLevelUnit = createNextTopLevelUnit(nativeModel, topLevelUnitUri)
    // connect the new root with the old root encoded as a domain element
    nativeModel.add(
      nextTopLevelUnit,
      nativeModel.createProperty((Namespace.Document + "encodes").iri()),
      nativeModel.createResource(encodedElement)
    )
  }

  private def getUriNotPresentInModel(topLevelUnit: DomainElementUri) = topLevelUnit + "#/rootAsset"

  private def createNextTopLevelUnit(nativeModel: Model, topLevelUnit: DomainElementUri) = {
    val topLevel = nativeModel.createResource(topLevelUnit)
    nativeModel.add(
      topLevel,
      nativeModel.createProperty((Namespace.Rdf + "type").iri()),
      nativeModel.createResource((Namespace.Meta + "DialectInstance").iri())
    )
    nativeModel.add(
      topLevel,
      nativeModel.createProperty((Namespace.Rdf + "type").iri()),
      nativeModel.createResource((Namespace.Document + "Document").iri())
    )
    nativeModel.add(
      topLevel,
      nativeModel.createProperty((Namespace.Rdf + "type").iri()),
      nativeModel.createResource((Namespace.Document + "Fragment").iri())
    )
    nativeModel.add(
      topLevel,
      nativeModel.createProperty((Namespace.Rdf + "type").iri()),
      nativeModel.createResource((Namespace.Document + "Module").iri())
    )
    nativeModel.add(
      topLevel,
      nativeModel.createProperty((Namespace.Rdf + "type").iri()),
      nativeModel.createResource((Namespace.Document + "Unit").iri())
    )
    topLevel
  }

  private def queryObjectsWith(nativeModel: Model, subjectUri: DomainElementUri, property: ValueType) = {
    nativeModel.listObjectsOfProperty(
      nativeModel.createResource(subjectUri),
      nativeModel.createProperty(property.iri())
    )
  }

  private def querySubjectsWith(nativeModel: Model, property: ValueType, resource: ValueType) = {
    nativeModel.listSubjectsWithProperty(
      nativeModel.createProperty(property.iri()),
      nativeModel.createResource(resource.iri())
    )
  }

  private def querySubjectsWith(nativeModel: Model, property: ValueType, resourceUri: String) = {
    nativeModel.listSubjectsWithProperty(
      nativeModel.createProperty(property.iri()),
      nativeModel.createResource(resourceUri)
    )
  }

  private def queryTopLevelDocument(nativeModel: Model) = {
    nativeModel
      .listSubjectsWithProperty(
        nativeModel.createProperty((Namespace.Rdf + "type").iri()),
        nativeModel.createResource((Namespace.Document + "Document").iri())
      )
      .next()
      .getURI
  }

  private def isRecursiveBaseUnit(uri: String) = uri.endsWith("recursive")

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

  def domainElementsFrom(nativeModel: Model): Seq[DomainElementUri] = {
    val domainElements = querySubjectsWith(nativeModel, Namespace.Rdf + "type", Namespace.Document + "DomainElement").toList.asScala
    val shapes = querySubjectsWith(nativeModel, Namespace.Rdf + "type", Namespace.Shapes + "Shape").toList.asScala

    domainElements ++ shapes map { _.getURI }
  }

  /**
    * Cleans the input WebAPI model adding the required information to the
    * underlying RDF graph and uses it to build the canonical WebAPI dialect
    * instance as output
    *
    * @param unit a AMF WebAPI model parsed from RAML / OAS
    * @return Equivalent Canonical WebAPI AML dialect instance
    */
  protected def cleanAMFModel(unit: BaseUnit): BaseUnit = {
    val typeMapping = buildCanonicalClassMapping
    val model       = unit.toNativeRdfModel()

    val nativeModel = model.native().asInstanceOf[Model]

    val baseUnitId = preProcessUnits(nativeModel)

    // First update document to DialectInstance document
    findWebAPIDialect match {
      case Some(dialect) =>
        nativeModel.add(
          nativeModel.createResource(baseUnitId),
          nativeModel.createProperty((Namespace.Meta + "definedBy").iri()),
          nativeModel.createResource(dialect.id)
        )
      case None => // ignore
    }

    // transform dynamic data nodes to list the properties
    transformDataNodes(typeMapping, nativeModel)

    transformDomainElements(typeMapping, nativeModel)

    val plugins = PluginContext(
      blacklist = Seq(CorePlugin, WebAPIDomainPlugin, DataShapesDomainPlugin, AMFGraphPlugin, Raml10Plugin))

    RdfModelParser(errorHandler = UnhandledParserErrorHandler, plugins = plugins)
      .parse(model, baseUnitId)
  }

  private def transformDomainElements(typeMapping: Map[TypeUri, DialectNode], nativeModel: Model): Unit = {
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

  def transformType(nativeModel: Model, domainElement: DomainElementUri, mapping: Map[TypeUri, DialectNode]): Unit = {
    val typesIterator = queryObjectsWith(nativeModel, domainElement, Namespace.Rdf + "type")

    // We need to deal with node shape inheritance
    // These flags allow us to track if we found any shape or shape in case
    // we cannot find a more specific shape
    var foundShape: Option[String]      = None
    var foundAnyShape: Option[String]   = None
    var foundArrayShape: Option[String] = None
    var found                           = false
    var mappedDialectNode               = ""
    while (typesIterator.hasNext) {
      val nextType = typesIterator.next().asResource().getURI
      mapping.get(nextType) match {
        case Some(dialectNode) =>
          // dealing with inheritance here
          if (!dialectNode.endsWith("#/declarations/Shape") && !dialectNode.endsWith("#/declarations/AnyShape") && !dialectNode
                .endsWith("#/declarations/DataNode") && !dialectNode.endsWith("#/declarations/ArrayShape")) {
            found = true
            mappedDialectNode = dialectNode
          } else if (dialectNode.endsWith("#/declarations/Shape")) {
            foundShape = Some(dialectNode)
          } else if (dialectNode.endsWith("#/declarations/AnyShape")) {
            foundAnyShape = Some(dialectNode)
          } else if (dialectNode.endsWith("#/declarations/ArrayShape")) {
            foundArrayShape = Some(dialectNode)
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

  protected def transformLink(nativeModel: Model, domainElement: DomainElementUri): Unit = {
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

  protected def transformAnnotations(nativeModel: Model,
                                     typeMapping: Map[TypeUri, DialectNode],
                                     domainElement: DomainElementUri) {
    val annotsIt = queryObjectsWith(nativeModel, domainElement, DomainElementModel.CustomDomainProperties.value)
    var c = 0
    while (annotsIt.hasNext) {
      val nextAnnotation = annotsIt.next()
      nativeModel.remove(
        nativeModel.createResource(domainElement),
        nativeModel.createProperty(DomainElementModel.CustomDomainProperties.value.iri()),
        nextAnnotation
      )
      // now we find the value of the annotation, the annotation property links to this value
      // directly in the annotated node
      val nextAnnotationValueIt = nativeModel.listObjectsOfProperty(
        nativeModel.createResource(domainElement),
        nativeModel.createProperty(nextAnnotation.asResource().getURI)
      )
      val nextAnnotationValue = nextAnnotationValueIt.next()

      // autogen URI for the reified annotation
      val reifiedAnnotationUri = nativeModel.createResource(domainElement + s"_annotations_$c")
      c += 1

      nativeModel.add(
        nativeModel.createResource(domainElement),
        nativeModel.createProperty(DesignAnnotationField.value.iri()),
        reifiedAnnotationUri
      )
      nativeModel.add(
        reifiedAnnotationUri,
        nativeModel.createProperty((Namespace.Rdf + "type").iri()),
        nativeModel.createResource((Namespace.Meta + "DialectDomainElement").iri())
      )
      nativeModel.add(
        reifiedAnnotationUri,
        nativeModel.createProperty((Namespace.Rdf + "type").iri()),
        nativeModel.createResource(typeMapping(defaultIri(DomainExtensionModel)))
      )
      // the extension
      nativeModel.add(
        reifiedAnnotationUri,
        nativeModel.createProperty(DomainExtensionModel.Extension.value.iri()),
        nextAnnotationValue
      )

      // the link back to the annotation definition
      val annotationLink = nativeModel.createResource(reifiedAnnotationUri.getURI + "_link")
      nativeModel.add(
        annotationLink,
        nativeModel.createProperty((Namespace.Rdf + "type").iri()),
        nativeModel.createResource((Namespace.Meta + "DialectDomainElement").iri())
      )
      nativeModel.add(
        annotationLink,
        nativeModel.createProperty((Namespace.Rdf + "type").iri()),
        nativeModel.createResource(typeMapping(defaultIri(CustomDomainPropertyModel)))
      )
      nativeModel.add(
        annotationLink,
        nativeModel.createProperty(DesignLinkTargetField.value.iri()),
        nextAnnotation
      )

      // We try to also add the name of the annotation to the annotation link
      val maybeAnnotationNameIt = nativeModel.listObjectsOfProperty(
        nextAnnotation.asResource(),
        nativeModel.createProperty(CustomDomainPropertyModel.Name.value.iri())
      )
      if (maybeAnnotationNameIt.hasNext) {
        nativeModel.add(
          annotationLink,
          nativeModel.createProperty(CustomDomainPropertyModel.Name.value.iri()),
          maybeAnnotationNameIt.next().asLiteral()
        )
      }

      // we get definedBy to the linked annotation
      nativeModel.add(
        reifiedAnnotationUri,
        nativeModel.createProperty(DomainExtensionModel.DefinedBy.value.iri()),
        annotationLink
      )

    }

  }

  protected def defaultIri(metadata: Obj): String = metadata.`type`.head.iri()

  /**
    * Transforms a WebAPI model parsed by AMF from a RAML/OAS document into a canonical WebAPI model compatible with the canonical WebAPI AML dialect
    */
  def transform(unit: BaseUnit): Future[BaseUnit] = unit match {
    case _: Document => Future.successful(cleanAMFModel(unit))
    case _           => throw DocumentExpectedException("Expected Document for CanonicalWebAPISpecTransformation")
  }
}

case class DocumentExpectedException(message: String) extends RuntimeException(message)

case class RecursiveUnitsPresentException(message: String) extends RuntimeException(message)

case class CanonicalDialectNotFoundException(message: String) extends RuntimeException(message)

case class UnknownDocumentTypeMappingException(message: String) extends RuntimeException(message)