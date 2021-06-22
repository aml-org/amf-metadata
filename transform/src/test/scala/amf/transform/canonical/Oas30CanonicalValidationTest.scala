package amf.transform.canonical

import amf.core.internal.remote.{Hint, Oas30JsonHint}

class Oas30CanonicalValidationTest extends CanonicalSpecValidationTest {

  override val basePath = "file://transform/src/test/resources/specs/oas30/"
  override val apiPaths = Set(
    "basic-callbacks.json",
    "basic-content.json",
    "basic-encoding.json",
    "basic-headers-response.json",
    "basic-links.json",
    "basic-oas-patch-corrected.json",
    "basic-oas-patch.json",
    "basic-operation.json",
    "basic-paths-object-with-server.json",
    "basic-paths-object-with-servers.json",
    "basic-request-body.json",
    "basic-security-types.json",
    "basic-servers-2.json.json",
    "basic-servers.json",
    "basic-servers.raml.json",
    "complex-servers.json",
    "deprecated-field.json",
    "invalid-email-address.json",
    "one-subscription-multiple-callbacks.json",
    "overriding-server-object-resolved.json",
    "overriding-server-object.json",
    "reference-response-declaration-resolved.json",
    "reference-response-declaration.json",
    "request-body-and-discriminator-required-fields.json",
    "response-code-wildcards.json",
    "response-no-description.json",
    "schema-definitions.json",
    "several-security-schemes-of-same-type.json",
    "unique-name-for-tags.json",
    "basic-parameters/basic-parameters.json",
    "basic-parameters/overriding-parameters.json",
    "basic-parameters/parameter-multiple-content-entries.json",
    "basic-parameters/parameter-schema-and-content.json",
    "components/basic-components.json",
    "discriminator-object/discriminator-object.json",
    "parameter-payload-resolution/parameter-payload-examples.json",
    "summary-description-in-path/description-applied-to-operations.json"
  )
  override val hint: Hint = Oas30JsonHint

  runValidations()
}
