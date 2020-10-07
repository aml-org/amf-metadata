package amf.transform.canonical

import amf.core.metamodel.Obj
import amf.core.metamodel.domain.DomainElementModel
import amf.core.metamodel.domain.extensions.{CustomDomainPropertyModel, DomainExtensionModel}
import amf.core.vocabulary.{Namespace, ValueType}
import amf.transform.canonical.CanonicalWebAPISpecExtraModel.{DesignAnnotationField, DesignLinkTargetField}
import amf.transform.canonical.CanonicalWebAPISpecTransformer.{DialectNode, DomainElementUri, TypeUri}
import org.apache.jena.rdf.model.Model

trait AnnotationTransform extends TransformHelpers {
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
}
