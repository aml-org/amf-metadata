package amf.transform.internal.canonical

import amf.core.internal.remote.{Hint, Raml10YamlHint, Spec}

class Raml10CanonicalValidationTest extends CanonicalSpecValidationTest {

  override val basePath: String = "file://transform/src/test/resources/specs/raml10/"

  override val spec: Spec = Spec.RAML10
  override val apiPaths = Set(
    "full-example.raml",
    "base-type-array.raml",
    "matrix-type-array.raml",
    "recursive-type.raml",
    "type-expression-with-inheritance.raml",
    "union-type-array.raml",
    "recursive-array.raml",
    "japanese-full-check.raml",
    "full-example-2.raml",
    "overlay-valid-complex/overlay2.raml",
    "overlay-valid-insertion-annotation/overlay.raml",
    "overlay-with-example-overloading/overlay.raml"
  )

  runValidations()
}
