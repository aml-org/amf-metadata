package amf.exporters

import amf.helpers.VocabularyTest
import org.scalatest.funsuite.AsyncFunSuite

import scala.concurrent.ExecutionContext

class VocabularyPropertiesTest extends AsyncFunSuite with VocabularyTest{

  override implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  private val aml_doc        = "vocabulary/src/main/resources/vocabularies/aml_doc.yaml"
  private val aml_meta       = "vocabulary/src/main/resources/vocabularies/aml_meta.yaml"
  private val api_contract   = "vocabulary/src/main/resources/vocabularies/api_contract.yaml"
  private val core           = "vocabulary/src/main/resources/vocabularies/core.yaml"
  private val data_model     = "vocabulary/src/main/resources/vocabularies/data_model.yaml"
  private val data_shapes    = "vocabulary/src/main/resources/vocabularies/data_shapes.yaml"
  private val security_model = "vocabulary/src/main/resources/vocabularies/security.yaml"

  test("Vocabularies parsing aml_doc") {
    testVocabulary(aml_doc, 14, 30)
  }

  test("Vocabularies parsing aml_meta") {
    testVocabulary(aml_meta, 17, 29)
  }

  test("Vocabularies parsing api_contract") {
    testVocabulary(api_contract, 29, 46)
  }

  test("Vocabularies parsing core") {
    testVocabulary(core, 4, 20)
  }

  test("Vocabularies parsing data_model") {
    testVocabulary(data_model, 6, 3)
  }

  test("Vocabularies parsing data_shapes") {
    testVocabulary(data_shapes, 14, 39)
  }

  test("Vocabularies parsing security_model") {
    testVocabulary(security_model, 13, 19)
  }
}
