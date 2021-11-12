package amf.io

import amf.apicontract.client.scala.{AMFConfiguration, APIConfiguration, AsyncAPIConfiguration, WebAPIConfiguration}
import amf.core.client.scala.AMFGraphConfiguration
import amf.core.client.scala.config.RenderOptions
import amf.core.client.scala.errorhandling.{AMFErrorHandler, IgnoringErrorHandler}
import amf.core.client.scala.model.document.BaseUnit
import amf.core.internal.remote.Syntax.Syntax
import amf.core.internal.remote.{Hint, Spec}
import org.scalatest.Assertion
import org.scalatest.funsuite.AsyncFunSuite

import scala.concurrent.{ExecutionContext, Future}

/**
  * Cycle tests using temporary file and directory creator
  */
abstract class FunSuiteCycleTests extends AsyncFunSuite with BuildCycleTests {
  override implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
}

trait BuildCycleTestCommon extends FileAssertionTest {

  protected implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  def basePath: String

  case class CycleConfig(source: String,
                         golden: String,
                         hint: Hint,
                         target: Spec,
                         directory: String,
                         syntax: String,
                         pipeline: Option[String],
                         transformWith: Option[Spec] = None) {
    val sourcePath: String = directory + source
    val goldenPath: String = directory + golden
  }

  /** Method to parse unit. Override if necessary. */
  def build(config: CycleConfig, amfConfig: AMFGraphConfiguration): Future[BaseUnit] = {
    amfConfig
      .withParsingOptions(amfConfig.options.parsingOptions.withBaseUnitUrl("file://" + config.goldenPath))
      .baseUnitClient()
      .parse(s"file://${config.sourcePath}")
      .map(_.baseUnit)
  }

  /** Method to render parsed unit. Override if necessary. */
  def render(unit: BaseUnit, config: CycleConfig, amfConfig: AMFConfiguration): String = {
    amfConfig.baseUnitClient().render(unit, config.syntax)
  }
  def renderOptions(): RenderOptions = RenderOptions()

  protected def buildConfig(options: Option[RenderOptions], eh: Option[AMFErrorHandler]): AMFConfiguration = {
    val amfConfig: AMFConfiguration = APIConfiguration.API()
    val renderedConfig: AMFConfiguration = options.fold(amfConfig.withRenderOptions(renderOptions()))(r => {
      amfConfig.withRenderOptions(r)
    })
    eh.fold(renderedConfig.withErrorHandlerProvider(() => IgnoringErrorHandler))(e =>
      renderedConfig.withErrorHandlerProvider(() => e))
  }

}

trait BuildCycleTests extends BuildCycleTestCommon {

  /** Compile source with specified hint. Dump to target and assert against same source file. */
  def cycle(source: String, hint: Hint, syntax: String): Future[Assertion] =
    cycle(source, hint, basePath, syntax)

  /** Compile source with specified hint. Dump to target and assert against same source file. */
  def cycle(source: String, hint: Hint, directory: String, syntax: String): Future[Assertion] =
    cycle(source, source, hint, hint.spec, directory, syntax = syntax, eh = None)

  /** Compile source with specified hint. Render to temporary file and assert against golden. */
  final def cycle(source: String,
                  golden: String,
                  hint: Hint,
                  target: Spec,
                  directory: String = basePath,
                  renderOptions: Option[RenderOptions] = None,
                  syntax: String,
                  pipeline: Option[String] = None,
                  transformWith: Option[Spec] = None,
                  eh: Option[AMFErrorHandler] = None): Future[Assertion] = {

    val config    = CycleConfig(source, golden, hint, target, directory, syntax, pipeline, transformWith)
    val amfConfig = buildConfig(renderOptions, eh)

    for {
      parsed       <- build(config, amfConfig)
      resolved     <- Future.successful(transform(parsed, config, amfConfig))
      actualString <- Future.successful(render(resolved, config, amfConfig))
      actualFile   <- writeTemporaryFile(golden)(actualString)
      assertion    <- assertDifferences(actualFile, config.goldenPath)
    } yield {
      assertion
    }
  }

  /** Method for transforming parsed unit. Override if necessary. */
  def transform(unit: BaseUnit, config: CycleConfig, amfConfig: AMFConfiguration): BaseUnit = unit
}
