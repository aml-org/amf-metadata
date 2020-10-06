package amf.transform.canonical

import amf.core.metamodel.document.BaseUnitModel
import amf.core.model.document.{BaseUnit, Document}
import amf.core.parser.errorhandler.UnhandledParserErrorHandler
import amf.core.plugin.{CorePlugin, PluginContext}
import amf.core.rdf.{RdfModel, RdfModelParser}
import amf.core.unsafe.PlatformSecrets
import amf.core.vocabulary.Namespace.XsdTypes
import amf.core.vocabulary.{Namespace, ValueType}
import amf.plugins.document.graph.AMFGraphPlugin
import amf.plugins.document.vocabularies.AMLPlugin
import amf.plugins.document.vocabularies.model.document.Dialect
import amf.plugins.document.vocabularies.model.domain.NodeMapping
import amf.plugins.document.webapi.Raml10Plugin
import amf.plugins.domain.shapes.DataShapesDomainPlugin
import amf.plugins.domain.webapi.WebAPIDomainPlugin
import org.apache.jena.rdf.model.{Model, Statement}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.Future

private[amf] object CanonicalWebAPISpecTransformer extends PlatformSecrets with DataNodeTransform with DomainElementTransform with BaseUnitTransform {
  type DomainElementUri = String
  type TypeUri          = String
  type DialectNode      = String

  val CANONICAL_WEBAPI_NAME = "WebAPI Spec 1.0"

  /**
   * Transforms a WebAPI model parsed by AMF from a RAML/OAS document into a canonical WebAPI model compatible with the canonical WebAPI AML dialect
   */
  def transform(unit: BaseUnit): Future[BaseUnit] = Future.successful { cleanAMFModel(unit) }

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

    findWebAPIDialect match {
      case Some(dialect) => updateToDialectInstance(nativeModel, baseUnitId, dialect)
      case None => // ignore
    }

    // transform dynamic data nodes to list the properties
    transformDataNodes(typeMapping, nativeModel)

    transformDomainElements(typeMapping, nativeModel)

    parseRdfToInstance(model, baseUnitId)
  }

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


  protected def findWebAPIDialect: Option[Dialect] =
    AMLPlugin().registry.allDialects().find(_.nameAndVersion() == CANONICAL_WEBAPI_NAME)

  private def updateToDialectInstance(nativeModel: Model, baseUnitId: String, dialect: Dialect) = {
    nativeModel.add(
      nativeModel.createResource(baseUnitId),
      nativeModel.createProperty((Namespace.Meta + "definedBy").iri()),
      nativeModel.createResource(dialect.id)
    )
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


  private def parseRdfToInstance(model: RdfModel, baseUnitId: String): BaseUnit = {
    val plugins = PluginContext(
      blacklist = Seq(CorePlugin, WebAPIDomainPlugin, DataShapesDomainPlugin, AMFGraphPlugin, Raml10Plugin))

    RdfModelParser(errorHandler = UnhandledParserErrorHandler, plugins = plugins)
      .parse(model, baseUnitId)
  }
}

case class DocumentExpectedException(message: String) extends RuntimeException(message)

case class RecursiveUnitsPresentException(message: String) extends RuntimeException(message)

case class CanonicalDialectNotFoundException(message: String) extends RuntimeException(message)

case class UnknownDocumentTypeMappingException(message: String) extends RuntimeException(message)