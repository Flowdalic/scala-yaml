package org.virtuslab.yaml

import org.virtuslab.yaml.internal.load.parse.ParserImpl

import scala.util.{Failure, Success, Try}

import org.virtuslab.yaml
import org.virtuslab.yaml.internal.load.parse.EventKind
import org.virtuslab.yaml.internal.load.parse.Anchor
import org.virtuslab.yaml.internal.load.parse.EventKind.*
import org.virtuslab.yaml.internal.load.parse.ParserImpl
import org.virtuslab.yaml.internal.load.reader.Scanner
import org.virtuslab.yaml.internal.load.reader.token.ScalarStyle
import org.virtuslab.yaml.internal.load.reader.token.ScalarStyle._
import org.virtuslab.yaml.internal.load.reader.token.Token._
import scala.annotation.tailrec
import scala.collection.mutable

trait TestRunner():
  def inYaml: String
  def expectedEvents: String

  def run(): RunnerResult =
    val reader = Scanner(inYaml)
    val parser = ParserImpl(reader)
    val acc    = new mutable.ArrayDeque[EventKind]()

    @tailrec
    def loop(): RunnerResult = {
      parser.getNextEvent() match
        case Right(event) =>
          acc.append(event.kind)
          if event.kind != EventKind.StreamEnd then loop()
          else RunnerResult(acc.toList, expectedEvents)
        case Left(error) =>
          RunnerResult(acc.toList, expectedEvents, error)
    }
    loop()
  end run

end TestRunner

object TestRunnerUtils:

  extension (anchor: Option[Anchor]) def asString: String = anchor.map(a => s" &$a").getOrElse("")

  def convertEventToYamlTestSuiteFormat(events: Seq[EventKind]): String =
    events
      .map {
        case StreamStart                 => "+STR"
        case StreamEnd                   => "-STR"
        case DocumentStart(explicit)     => if (explicit) "+DOC ---" else "+DOC"
        case DocumentEnd(explicit)       => if (explicit) "-DOC ..." else "-DOC"
        case SequenceStart(data)         => s"+SEQ${data.anchor.asString}"
        case SequenceEnd                 => "-SEQ"
        case MappingStart(data)          => s"+MAP${data.anchor.asString}"
        case FlowMappingStart(data)      => s"+MAP${data.anchor.asString}"
        case MappingEnd | FlowMappingEnd => "-MAP"
        case Alias(alias)                => s"=ALI *$alias"
        case Scalar(value, style, data) =>
          style match {
            case ScalarStyle.Plain        => s"=VAL${data.anchor.asString} :$value"
            case ScalarStyle.DoubleQuoted => s"""=VAL${data.anchor.asString} "$value"""
            case ScalarStyle.SingleQuoted => s"=VAL${data.anchor.asString} '$value"
            case ScalarStyle.Folded       => s"=VAL${data.anchor.asString} >$value"
            case ScalarStyle.Literal      => s"=VAL${data.anchor.asString} |$value"
          }
      }
      .mkString("\n")

end TestRunnerUtils

case class K8sYamlTestRunner(yamlPath: os.Path, libYaml: os.Path) extends TestRunner:
  override val inYaml = os.read(yamlPath)
  override val expectedEvents = os
    .proc(libYaml, yamlPath)
    .call(cwd = os.pwd)
    .out
    .text()
    .trim

  override def run(): RunnerResult =
    println(yamlPath)
    super.run()

end K8sYamlTestRunner

case class YamlSuiteTestRunner(testYamlML: os.Path) extends TestRunner:
  private val testMl = TestMlEntry.from(testYamlML)

  override val inYaml         = testMl.inYaml
  override val expectedEvents = testMl.seqEvent

end YamlSuiteTestRunner
