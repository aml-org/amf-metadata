package amf.transform.internal.canonical

import amf.aml.client.scala.AMLConfiguration
import amf.aml.client.scala.model.document.{Dialect, DialectInstanceProcessingData}
import amf.aml.client.scala.model.domain.NodeMapping
import amf.aml.internal.entities.AMLEntities
import amf.aml.internal.transform.steps.SemanticExtensionFlatteningStage
import amf.core.client.scala.errorhandling.IgnoringErrorHandler
import amf.core.client.scala.model.document.BaseUnit
import amf.core.client.scala.vocabulary.Namespace
import amf.core.client.scala.vocabulary.Namespace.XsdTypes
import amf.core.internal.metamodel.ModelDefaultBuilder
import amf.core.internal.metamodel.document.{BaseUnitModel, DocumentModel}
import amf.core.internal.remote.AmlDialectSpec
import amf.core.internal.unsafe.PlatformSecrets
import amf.rdf.client.scala.RdfUnitConverter.{fromNativeRdfModel, toNativeRdfModel}
import amf.rdf.client.scala.{RdfModel, RdfUnitConverter}
import org.apache.jena.rdf.model.{Model, Statement}

import scala.collection.JavaConverters._
import scala.collection.{immutable, mutable}

private[amf] object CanonicalWebAPISpecTransformer extends PlatformSecrets with DataNodeTransform with DomainElementTransform with BaseUnitTransform {
  type DomainElementUri = String
  type TypeUri          = String
  type DialectNode      = String

  val CANONICAL_WEBAPI_DIALECT_NAME = "WebAPI Spec"
  val CANONICAL_WEBAPI_DIALECT_VERSION = "1.0"
  val CANONICAL_DIALECT_HEADER = s"$CANONICAL_WEBAPI_DIALECT_NAME $CANONICAL_WEBAPI_DIALECT_VERSION"

  /**
   * Transforms a WebAPI model parsed by AMF from a RAML/OAS document into a canonical WebAPI model compatible with the canonical WebAPI AML dialect
   */
  def transform(unit: BaseUnit, config: AMLConfiguration): BaseUnit = {
    flattenSemanticExtensions(unit, config)
    cleanAMFModel(unit, config)
  }

  private def flattenSemanticExtensions(unit: BaseUnit, config: AMLConfiguration): BaseUnit = {
    new SemanticExtensionFlatteningStage().transform(unit, IgnoringErrorHandler, config)
  }

  /**
   * Cleans the input WebAPI model adding the required information to the
   * underlying RDF graph and uses it to build the canonical WebAPI dialect
   * instance as output
   *
   * @param unit a AMF WebAPI model parsed from RAML / OAS
   * @return Equivalent Canonical WebAPI AML dialect instance
   */
  protected def cleanAMFModel(unit: BaseUnit, config: AMLConfiguration): BaseUnit = {
    val webApiDialect: Dialect = findWebAPIDialect(config)
    val typeMapping = buildCanonicalClassMapping(webApiDialect)
    val model       = toNativeRdfModel(unit, config, config.options.renderOptions)

    val nativeModel = model.getNative().asInstanceOf[Model]

    val baseUnitId = preProcessUnits(nativeModel, webApiDialect)

    linkModelToDialect(nativeModel, baseUnitId, webApiDialect)

    // transform dynamic data nodes to list the properties
    transformDataNodes(typeMapping, nativeModel)

    transformDomainElements(typeMapping, nativeModel)

    parseRdfToInstance(model, baseUnitId, config, webApiDialect)
  }

  /**
    * Creates a map from the node mapping in the canonical web api dialect to the mapped
    * class in the WebAPI vocabulary
    * @return
    */
  protected def buildCanonicalClassMapping(webApiDialect: Dialect): Map[TypeUri, DialectNode] = {
    val nodeMappings = webApiDialect.declares.collect { case mapping: NodeMapping => mapping }
    nodeMappings.foldLeft(Map[String, String]()) {
      case (acc, mapping) =>
        acc + (mapping.nodetypeMapping.value() -> mapping.id)
    }
  }


  protected def findWebAPIDialect(config: AMLConfiguration): Dialect = {
    val dialect = config.configurationState().getDialect(CANONICAL_WEBAPI_DIALECT_NAME, CANONICAL_WEBAPI_DIALECT_VERSION)
    dialect match {
      case Some(r) => r
      case None => throw CanonicalDialectNotFoundException("Cannot find WebAPI 1.0 Dialect in Dialect registry")
    }
  }

  private def linkModelToDialect(nativeModel: Model, baseUnitId: String, dialect: Dialect) = {
    nativeModel.add(
      nativeModel.createResource(baseUnitId),
      nativeModel.createProperty((Namespace.Meta + "definedBy").iri()),
      nativeModel.createResource(dialect.id)
    )
  }

  /**
   * Transforms the doc:Units in the graph into the domain elements for assets in the canonical web api spec dialect
   */
  def preProcessUnits(nativeModel: Model, webApiDialect: Dialect): String = {
    // we save the top level document we will not transform into a domain level asset
    val topLevelUnitUri = queryTopLevelDocument(nativeModel)

    // get all the document units
    val unitUris = getAllDocumentUnits(nativeModel)

    // for each document unit that is not the root one, we transform that into the canonical webpi spec asset fragment node
    // defined in the SpecDocument node mapping schema
    val (units, recursives) = unitUris.partition(!isRecursiveBaseUnit(_))
    units.foreach { unit =>
      val unitResource = nativeModel.createResource(unit)

      // Let's manipulate the @type of the unit to match the dialect expectations
      mapBaseUnits(unit, webApiDialect, nativeModel)
    }

    if (recursives.nonEmpty)
      throw RecursiveUnitsPresentException(s"Recursive units are not supported in Canonical Transform")

    introduceNewTopLevelDocument(nativeModel, topLevelUnitUri)
    topLevelUnitUri
  }

  private def getAllDocumentUnits(nativeModel: Model) = {
    querySubjectsWith(nativeModel, Namespace.Rdf + "type", Namespace.Document + "Unit")
      .toList
      .asScala
      .map(_.getURI)
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
      nativeModel.createProperty(DocumentModel.Encodes.value.iri()),
      nativeModel.createResource(encodedElement)
    )

    nativeModel.add(
      nextTopLevelUnit,
      nativeModel.createProperty(BaseUnitModel.Root.value.iri()),
      nativeModel.createTypedLiteral("true", XsdTypes.xsdBoolean.iri())
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

  private def queryTopLevelDocument(nativeModel: Model) = {
    nativeModel
      .listSubjectsWithProperty(
        nativeModel.createProperty(BaseUnitModel.Root.value.iri()),
        nativeModel.createTypedLiteral("true", XsdTypes.xsdBoolean.iri())
      )
      .next()
      .getURI
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

  private def isRecursiveBaseUnit(uri: String) = uri.endsWith("recursive")


  private def parseRdfToInstance(model: RdfModel, baseUnitId: String, config: AMLConfiguration, dialect: Dialect): BaseUnit = {
    val nextConfig = createConfigWithDialectEntites(config)
    val instance = fromNativeRdfModel(baseUnitId, model, nextConfig)
    withCanonicalDialectProcessingData(dialect, instance)
  }

  private def withCanonicalDialectProcessingData(dialect: Dialect, instance: BaseUnit) = {
    val processingData = DialectInstanceProcessingData().withDefinedBy(dialect.id).withSourceSpec(AmlDialectSpec(CANONICAL_DIALECT_HEADER))
    instance.withProcessingData(processingData)
  }

  private def createConfigWithDialectEntites(config: AMLConfiguration) = {
    val dialects = config.configurationState().getDialects()
    val baseConfig: AMLConfiguration = cleanConfigWithDialectEntities(config, dialects)
    loadConfigWithExtensions(dialects, baseConfig)
  }

  private def cleanConfigWithDialectEntities(config: AMLConfiguration, dialects: immutable.Seq[Dialect]) = {
    val modelIds = dialects.flatMap(_.declares.collect { case n: NodeMapping => n.id })
    val entities = config.registry.getEntitiesRegistry.domainEntities
    val dialectEntities = modelIds.foldLeft(Map[String, ModelDefaultBuilder]()) { (acc, curr) => acc + (curr -> entities(curr)) }
    val baseConfig = AMLConfiguration.empty().withEntities(dialectEntities ++ AMLEntities.entities)
    baseConfig
  }

  private def loadConfigWithExtensions(dialects: Seq[Dialect], baseConfig: AMLConfiguration) = {
    dialects.filter(_.hasExtensions()).foldLeft(baseConfig) { (config, dialect) => config.withExtensions(dialect) }
  }
}

case class DocumentExpectedException(message: String) extends RuntimeException(message)

case class RecursiveUnitsPresentException(message: String) extends RuntimeException(message)

case class CanonicalDialectNotFoundException(message: String) extends RuntimeException(message)

case class UnknownDocumentTypeMappingException(message: String) extends RuntimeException(message)
