package amf.transform.internal.canonical

import amf.aml.client.scala.AMLConfiguration
import amf.aml.client.scala.model.document.Dialect
import amf.aml.client.scala.model.domain.NodeMapping
import amf.aml.internal.entities.AMLEntities
import amf.aml.internal.parse.plugin.AMLDialectInstanceParsingPlugin
import amf.core.client.scala.AMFGraphConfiguration
import amf.core.client.scala.model.document.BaseUnit
import amf.core.client.scala.rdf.RdfUnitConverter.toNativeRdfModel
import amf.core.client.scala.rdf.{RdfModel, RdfUnitConverter}
import amf.core.client.scala.vocabulary.Namespace
import amf.core.client.scala.vocabulary.Namespace.XsdTypes
import amf.core.internal.metamodel.document.BaseUnitModel
import amf.core.internal.unsafe.PlatformSecrets
import org.apache.jena.rdf.model.{Model, Statement}

import scala.collection.JavaConverters._
import scala.collection.mutable

private[amf] object CanonicalWebAPISpecTransformer extends PlatformSecrets with DataNodeTransform with DomainElementTransform with BaseUnitTransform {
  type DomainElementUri = String
  type TypeUri          = String
  type DialectNode      = String

  val CANONICAL_WEBAPI_DIALECT_NAME = "WebAPI Spec"
  val CANONICAL_WEBAPI_DIALECT_VERSION = "1.0"

  /**
   * Transforms a WebAPI model parsed by AMF from a RAML/OAS document into a canonical WebAPI model compatible with the canonical WebAPI AML dialect
   */
  def transform(unit: BaseUnit, config: AMLConfiguration): BaseUnit = cleanAMFModel(unit, config)

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
    val model       = toNativeRdfModel(unit)

    val nativeModel = model.native().asInstanceOf[Model]

    val baseUnitId = preProcessUnits(nativeModel, webApiDialect)

    linkModelToDialect(nativeModel, baseUnitId, webApiDialect)

    // transform dynamic data nodes to list the properties
    transformDataNodes(typeMapping, nativeModel)

    transformDomainElements(typeMapping, nativeModel)

    parseRdfToInstance(model, baseUnitId, webApiDialect)
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
      mapBaseUnits(unit, webApiDialect, nativeModel)
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


  private def parseRdfToInstance(model: RdfModel, baseUnitId: String, webApiDialect: Dialect): BaseUnit = {
    val withEntities = AMLConfiguration.empty().withEntities(AMLEntities.entities).withDialect(webApiDialect)
    RdfUnitConverter.fromNativeRdfModel(baseUnitId, model, withEntities)
  }
}

case class DocumentExpectedException(message: String) extends RuntimeException(message)

case class RecursiveUnitsPresentException(message: String) extends RuntimeException(message)

case class CanonicalDialectNotFoundException(message: String) extends RuntimeException(message)

case class UnknownDocumentTypeMappingException(message: String) extends RuntimeException(message)
