package amf.helpers

import amf.aml.client.scala.AMLConfiguration
import amf.aml.client.scala.model.document.Vocabulary
import amf.aml.client.scala.model.domain.{ClassTerm, PropertyTerm}
import amf.apicontract.client.scala.APIConfiguration
import amf.core.client.scala.errorhandling.UnhandledErrorHandler
import amf.core.internal.convert.NativeOpsFromJvm
import amf.core.internal.unsafe.PlatformSecrets
import amf.helpers.Assertions._
import org.scalatest.Assertion

import scala.concurrent.{ExecutionContext, Future}

trait VocabularyTest extends NativeOpsFromJvm with PlatformSecrets {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  def testVocabulary(file: String, numClasses: Int, numProperties: Int): Future[Assertion] = {
    val config = AMLConfiguration.predefined().withErrorHandlerProvider(() => UnhandledErrorHandler)
    for {
      unit <- config.baseUnitClient().parse(s"file://${file}").map(_.baseUnit)
    } yield {
      val declarations = unit.asInstanceOf[Vocabulary].declares

      val classes    = declarations.collect { case term: ClassTerm    => term }
      val properties = declarations.collect { case prop: PropertyTerm => prop }

      assert(classes.size == numClasses)
      assert(properties.size == numProperties)
    }
  }
}
