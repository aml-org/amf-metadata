package amf.io

import amf.apicontract.client.scala.{AMFConfiguration, AsyncAPIConfiguration, WebAPIConfiguration}
import amf.core.client.scala.AMFGraphConfiguration
import amf.core.client.scala.config.RenderOptions
import amf.core.client.scala.errorhandling.{AMFErrorHandler, IgnoringErrorHandler}
import amf.core.client.scala.model.document.BaseUnit
import amf.core.internal.remote.Syntax.Syntax
import amf.core.internal.remote.{Hint, Vendor}
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
                         target: Vendor,
                         directory: String,
                         syntax: Option[Syntax],
                         pipeline: Option[String],
                         transformWith: Option[Vendor] = None) {
    val sourcePath: String = directory + source
    val goldenPath: String = directory + golden

    val sourceMediaType: String = hint.vendor.mediaType + "+" + hint.syntax.extension
    val targetMediaType: String = target.mediaType + syntax.map(s => s"+${s.extension}").getOrElse("")
  }

  /** Method to parse unit. Override if necessary. */
  def build(config: CycleConfig, amfConfig: AMFGraphConfiguration): Future[BaseUnit] = {
    amfConfig
      .withParsingOptions(amfConfig.options.parsingOptions.withBaseUnitUrl("file://" + config.goldenPath))
      .createClient()
      .parse(s"file://${config.sourcePath}")
      .map(_.bu)
  }

  /** Method to render parsed unit. Override if necessary. */
  def render(unit: BaseUnit, config: CycleConfig, amfConfig: AMFConfiguration): String = {
    amfConfig.createClient().render(unit, config.targetMediaType)
  }
  def renderOptions(): RenderOptions = RenderOptions()

  protected def buildConfig(options: Option[RenderOptions], eh: Option[AMFErrorHandler]): AMFConfiguration = {
    val amfConfig: AMFConfiguration = WebAPIConfiguration.WebAPI().merge(AsyncAPIConfiguration.Async20())
    val renderedConfig: AMFConfiguration = options.fold(amfConfig.withRenderOptions(renderOptions()))(r => {
      amfConfig.withRenderOptions(r)
    })
    eh.fold(renderedConfig.withErrorHandlerProvider(() => IgnoringErrorHandler))(e =>
      renderedConfig.withErrorHandlerProvider(() => e))
  }

}

trait BuildCycleTests extends BuildCycleTestCommon {

  /** Compile source with specified hint. Dump to target and assert against same source file. */
  def cycle(source: String, hint: Hint, syntax: Option[Syntax]): Future[Assertion] =
    cycle(source, hint, basePath, syntax)

  /** Compile source with specified hint. Dump to target and assert against same source file. */
  def cycle(source: String, hint: Hint): Future[Assertion] = cycle(source, hint, basePath, None)

  /** Compile source with specified hint. Dump to target and assert against same source file. */
  def cycle(source: String, hint: Hint, directory: String, syntax: Option[Syntax]): Future[Assertion] =
    cycle(source, source, hint, hint.vendor, directory, syntax = syntax, eh = None)

  /** Compile source with specified hint. Dump to target and assert against same source file. */
  def cycle(source: String, hint: Hint, directory: String): Future[Assertion] =
    cycle(source, source, hint, hint.vendor, directory, eh = None)

  /** Compile source with specified hint. Render to temporary file and assert against golden. */
  final def cycle(source: String,
                  golden: String,
                  hint: Hint,
                  target: Vendor,
                  directory: String = basePath,
                  renderOptions: Option[RenderOptions] = None,
                  syntax: Option[Syntax] = None,
                  pipeline: Option[String] = None,
                  transformWith: Option[Vendor] = None,
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
