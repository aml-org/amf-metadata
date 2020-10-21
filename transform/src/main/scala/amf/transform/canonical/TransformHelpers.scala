package amf.transform.canonical

import amf.core.metamodel.Obj
import amf.core.vocabulary.ValueType
import amf.transform.canonical.CanonicalWebAPISpecTransformer.DomainElementUri
import org.apache.jena.rdf.model.Model

trait TransformHelpers {

  protected def queryObjectsWith(nativeModel: Model, subjectUri: DomainElementUri, property: ValueType) = {
    nativeModel.listObjectsOfProperty(
      nativeModel.createResource(subjectUri),
      nativeModel.createProperty(property.iri())
    )
  }

  protected def querySubjectsWith(nativeModel: Model, property: ValueType, resource: ValueType) = {
    nativeModel.listSubjectsWithProperty(
      nativeModel.createProperty(property.iri()),
      nativeModel.createResource(resource.iri())
    )
  }

  protected def querySubjectsWith(nativeModel: Model, property: ValueType, resourceUri: String) = {
    nativeModel.listSubjectsWithProperty(
      nativeModel.createProperty(property.iri()),
      nativeModel.createResource(resourceUri)
    )
  }

  protected def defaultIri(metadata: Obj): String = metadata.`type`.head.iri()
}
