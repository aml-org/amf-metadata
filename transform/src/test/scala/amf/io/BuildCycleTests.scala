package amf.io

import amf.client.parse.DefaultParserErrorHandler
import amf.core.client.ParsingOptions
import amf.core.emitter.RenderOptions
import amf.core.model.document.BaseUnit
import amf.core.parser.errorhandler.{ParserErrorHandler, UnhandledParserErrorHandler}
import amf.core.remote.Syntax.Syntax
import amf.core.remote.{Hint, Vendor}
import amf.facades.{AMFCompiler, Validation}
import amf.helpers.AMFRenderer
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
  }

  /** Method to parse unit. Override if necessary. */
  def build(config: CycleConfig,
            eh: Option[ParserErrorHandler],
            useAmfJsonldSerialisation: Boolean): Future[BaseUnit] = {
    Validation(platform).flatMap { _ =>
      var options =
        if (!useAmfJsonldSerialisation) ParsingOptions().withoutAmfJsonLdSerialization
        else ParsingOptions().withAmfJsonLdSerialization

      options = options.withBaseUnitUrl("file://" + config.goldenPath)

      AMFCompiler(s"file://${config.sourcePath}",
                  platform,
                  config.hint,
                  eh = eh.getOrElse(UnhandledParserErrorHandler),
                  parsingOptions = options).build()
    }
  }

  /** Method to render parsed unit. Override if necessary. */
  def render(unit: BaseUnit, config: CycleConfig, useAmfJsonldSerialization: Boolean): Future[String] = {
    val target  = config.target
    var options = RenderOptions().withSourceMaps.withPrettyPrint
    options =
      if (!useAmfJsonldSerialization) options.withoutAmfJsonLdSerialization else options.withAmfJsonLdSerialization
    new AMFRenderer(unit, target, options, config.syntax).renderToString
  }

  /** Method to render parsed unit. Override if necessary. */
  def render(unit: BaseUnit, config: CycleConfig, options: RenderOptions): Future[String] = {
    val target = config.target
    new AMFRenderer(unit, target, options, config.syntax).renderToString
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
                  useAmfJsonldSerialization: Boolean = true,
                  syntax: Option[Syntax] = None,
                  pipeline: Option[String] = None,
                  transformWith: Option[Vendor] = None,
                  eh: Option[ParserErrorHandler] = None): Future[Assertion] = {

    val config                 = CycleConfig(source, golden, hint, target, directory, syntax, pipeline, transformWith)
    val amfJsonLdSerialization = renderOptions.map(_.isAmfJsonLdSerilization).getOrElse(useAmfJsonldSerialization)

    build(config, eh.orElse(Some(DefaultParserErrorHandler.withRun())), amfJsonLdSerialization)
      .map(transform(_, config))
      .flatMap {
        renderOptions match {
          case Some(options) => render(_, config, options)
          case None          => render(_, config, useAmfJsonldSerialization)
        }
      }
      .flatMap(writeTemporaryFile(golden))
      .flatMap(assertDifferences(_, config.goldenPath))
  }

  /** Method for transforming parsed unit. Override if necessary. */
  def transform(unit: BaseUnit, config: CycleConfig): BaseUnit = unit
}