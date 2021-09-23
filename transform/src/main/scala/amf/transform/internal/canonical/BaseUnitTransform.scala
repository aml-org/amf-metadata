package amf.transform.internal.canonical

import amf.aml.client.scala.model.document.Dialect
import amf.aml.client.scala.model.domain.NodeMapping
import amf.aml.internal.metamodel.domain.NodeMappingModel
import amf.apicontract.internal.metamodel.document.{ExtensionModel, OverlayModel}
import amf.core.client.scala.vocabulary.Namespace
import amf.core.client.scala.vocabulary.Namespace.XsdTypes
import amf.core.internal.metamodel.document.{BaseUnitModel, DocumentModel, ExternalFragmentModel, ModuleModel}
import org.apache.jena.rdf.model.Model

import scala.collection.mutable

trait BaseUnitTransform extends TransformHelpers {
  protected def mapBaseUnits(unit: String, dialect: Dialect, nativeModel: Model) = {
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

    // This somehow adds some 'ordering' to what type is chosen from the list of types from the most specific to the most abstract. Ex: Document and Module
    // The last filter is to avoid using abstract models, not sure why 'Document' is there though.
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

    dialect.declares.find {
      case nodeMapping: NodeMapping =>
        val optionalClassTerm = Option(nodeMapping.nodetypeMapping.value())
        optionalClassTerm.exists { classTerm =>
          classTerm.split("#").last.split("/").last == mappedDocumentType.split("#").last
        }
      case _ => false
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
      nativeModel.createProperty(BaseUnitModel.Location.value.iri()),
      nativeModel.createLiteral(unit)
    )

    nativeModel.add(
      unitResource,
      nativeModel.createProperty(BaseUnitModel.Root.value.iri()),
      nativeModel.createTypedLiteral("false", XsdTypes.xsdBoolean.iri())
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
}
