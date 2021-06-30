package amf.transform.internal.canonical

import amf.core.internal.remote.{Hint, Oas20JsonHint}


class Oas20CanonicalValidationTest extends CanonicalSpecValidationTest {

  override val basePath: String = "file://transform/src/test/resources/specs/oas20/"

  override val ignoredApis = Set(
    "ref-to-main-file.json"
  )
  override val apiPaths = Set(
    "additional-properties-refs.json",
    "additional-property-references.json",
    "api-ext-recursive-ref.json",
    "api-with-extension-in-parameter.json",
    "api-with-parameter-declarations.json",
    "api-with-response-declarations.json",
    "apikey-settings.json",
    "capitalize-schemes.json",
    "correct-uri-parameter-parse.json",
    "custom-annotation-declaration.json",
    "default-response-code.json",
    "documentation-in-operation.json",
    "documentation-title-mandatory.json",
    "enum-annotations-api.json",
    "format-adjuster.json",
    "incorrect-example.json",
    "invalid-custom-type-name.json",
    "json-path-references-param-payload.json",
    "json-path-references.json",
    "mandatory-annotation-type.json",
    "mandatory-creative-work-content.json",
    "missing-payload-media-type.json",
    "oauth2-settings.json",
    "path-parameters.json",
    "ref-to-main-file.json",
    "response-examples.json",
    "response-references.json",
    "scalar-invalid-facets.json",
    "security-definitions.json",
    "simple-recursion.json",
    "tuple-type.json",
    "type-names-with-dots.json",
    "type-names-with-special-chars-brackets.json",
    "type-names-with-special-chars-spaces.json",
    "unions.json",
    "url-to-title-documentation.json",
    "with-external-ref.json",
    "with-nested-external-ref-array.json",
    "with-nested-external-ref-object.json"
  )
  override val hint: Hint = Oas20JsonHint

  runValidations()
}
