package amf.helpers

import amf.client.AMF
import amf.client.convert.NativeOpsFromJvm
import amf.core.parser.errorhandler.UnhandledParserErrorHandler
import amf.core.remote.VocabularyYamlHint
import amf.core.unsafe.PlatformSecrets
import amf.facades.AMFCompiler
import amf.helpers.Assertions._
import amf.plugins.document.vocabularies.model.document.Vocabulary
import amf.plugins.document.vocabularies.model.domain.{ClassTerm, PropertyTerm}
import org.scalatest.Assertion

import scala.concurrent.{ExecutionContext, Future}

trait VocabularyTest extends NativeOpsFromJvm with PlatformSecrets {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  def testVocabulary(file: String, numClasses: Int, numProperties: Int): Future[Assertion] = {
    for {
      _ <- AMF.init().asFuture
      unit <- AMFCompiler(s"file://${file}", platform, VocabularyYamlHint, eh = UnhandledParserErrorHandler).build()
    } yield {
      val declarations = unit.asInstanceOf[Vocabulary].declares

      val classes    = declarations.collect { case term: ClassTerm    => term }
      val properties = declarations.collect { case prop: PropertyTerm => prop }

      assert(classes.size == numClasses)
      assert(properties.size == numProperties)
    }
  }
}
