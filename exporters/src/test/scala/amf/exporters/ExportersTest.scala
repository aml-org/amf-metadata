package amf.exporters

import amf.core.metamodel.domain.ModelVocabulary
import amf.helpers.FileAssertionTest
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.{Assertion, Succeeded}

import scala.concurrent.Future

class ExportersTest extends AsyncFunSuite with FileAssertionTest{

  test("Canonical WebApi Spec Export Test") {
    val exporter = new CanonicalWebAPISpecDialectExporter(TestLogger)
    validateExport(exporter.writeToString, "vocabulary/src/main/resources/dialects/canonical_webapi_spec.yaml")
  }

  test("Validation Profile Exporter Test") {
    val exports: Map[ModelVocabulary, String] = VocabularyExporter.getVocabulariesAsString(TestLogger).map(x => (x.vocab, x.exported)).toMap
    val exportableVocabularies = VocabularyExporter.exportableVocabularies

    if (exports.size != exportableVocabularies.size) Future.successful { fail("Exportable vocabularies and exported vocabularies have different size") }
    else {
      val listOfAssertions: Seq[Future[Assertion]] = exportableVocabularies.map { vocab =>
        val path = VocabularyExporter.getVocabularyFileUrl(vocab)
        exports.get(vocab)
          .map(text => validateExport(text, path))
          .getOrElse( Future { fail(s"Couldn't find vocabulary: ${vocab.filename}") })
      }
      Future.sequence(listOfAssertions).map { assertions => assert(assertions.forall(_ == Succeeded)) }
    }
  }

  private def validateExport(exportedText: String, golden: String) = {
    for {
      tmpFile   <- writeTemporaryFile(golden)(exportedText)
      assertion <- assertDifferences(tmpFile, golden)
    } yield {
      assertion
    }
  }
}
