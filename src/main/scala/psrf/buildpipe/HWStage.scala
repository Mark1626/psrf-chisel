package psrf.buildpipe

import java.io.File
import java.io.PrintWriter
import io.circe.parser
import io.circe.HCursor
import io.circe.Decoder
import io.circe.Json
import io.circe.DecodingFailure
import psrf.config.{Config, Parameters}
import psrf._
import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import firrtl.AnnotationSeq
import firrtl.options.TargetDirAnnotation
import firrtl.stage.OutputFileAnnotation

case class HWStageConfig(
  classLabels:               List[Int],
  numFeatures:               Int,
  numClasses:                Int,
  numTrees:                  Int,
  treeLiterals:              List[List[DecisionTreeNodeLit]],
  fixedPointWidth:           Int,
  fixedPointBinaryPoint:     Int,
  majorityVoterType:         String,
  buildType:                 String,
  buildTarget:               String,
  testCandidates:            Option[List[List[Double]]],
  swRelativeClassifications: Option[List[Int]],
  targetClassifications:     Option[List[Int]]) {

  def getCDEConfig(): Parameters = {
    new Config((site, here, up) => {
      case NumFeatures           => numFeatures
      case NumClasses            => numClasses
      case NumTrees              => numTrees
      case FixedPointWidth       => fixedPointWidth
      case FixedPointBinaryPoint => fixedPointBinaryPoint
      case TreeLiterals          => treeLiterals
      case TestHarnessKey =>
        if (buildType == "test")
          TestHarnessParams(testCandidates.get, swRelativeClassifications.get, targetClassifications.get)
    })
  }

}

object HWStageConfig {
  def apply(jsonString: String): Either[io.circe.Error, HWStageConfig] = {
    implicit val decisionTreeNodeLitDecoder: Decoder[List[DecisionTreeNodeLit]] =
      (hCursor: HCursor) => {
        for {
          isLeafNodes   <- hCursor.get[List[Int]]("is_leaf")
          features      <- hCursor.get[List[Int]]("features")
          classes       <- hCursor.get[List[Int]]("classes")
          thresholds    <- hCursor.get[List[Double]]("threshold")
          childrenLeft  <- hCursor.get[List[Int]]("children_left")
          childrenRight <- hCursor.get[List[Int]]("children_right")
        } yield {
          isLeafNodes.zip(features).zip(classes).zip(thresholds).zip(childrenLeft).zip(childrenRight).map {
            case (((((il, f), c), t), cl), cr) =>
              DecisionTreeNodeLit(
                isLeafNode = il == 1,
                featureClassIndex = { if (il == 1) c else f },
                threshold = t,
                rightNode = cr,
                leftNode = cl
              )
          }
        }
      }
    implicit val hWStageConfigDecoder: Decoder[HWStageConfig] =
      (hCursor: HCursor) => {
        for {
          classLabels           <- hCursor.get[List[Int]]("class_labels")
          numFeatures           <- hCursor.get[Int]("num_features")
          numClasses            <- hCursor.get[Int]("num_classes")
          numTrees              <- hCursor.get[Int]("num_trees")
          treeLiterals          <- hCursor.get[List[List[DecisionTreeNodeLit]]]("trees")
          fixedPointWidth       <- hCursor.get[Int]("fixed_point_width")
          fixedPointBinaryPoint <- hCursor.get[Int]("fixed_point_bp")
          majorityVoterType     <- hCursor.get[String]("opt_majority_voter")
          buildType             <- hCursor.get[String]("build_type")
          buildTarget           <- hCursor.get[String]("build_target")
          testCandidates <- buildType match {
            case "test" => {
              hCursor.get[List[List[Double]]]("test_candidates") match {
                case Right(ec) => Right(Some(ec))
                case Left(_) =>
                  Left(DecodingFailure("Test candidates not found for test build", hCursor.history))
              }
            }
            case _ => Right(None)
          }
          swRelativeClassifications <- buildType match {
            case "test" => {
              hCursor.get[List[Int]]("sw_relative_classifications") match {
                case Right(ec) => Right(Some(ec))
                case Left(_) =>
                  Left(DecodingFailure("Software relative classifications not found for test build", hCursor.history))
              }
            }
            case _ => Right(None)
          }
          targetClassifications <- buildType match {
            case "test" => {
              hCursor.get[List[Int]]("target_classifications") match {
                case Right(ec) => Right(Some(ec))
                case Left(_) =>
                  Left(DecodingFailure("Target classfications not found for test build", hCursor.history))
              }
            }
            case _ => Right(None)
          }
        } yield {
          HWStageConfig(
            classLabels,
            numFeatures,
            numClasses,
            numTrees,
            treeLiterals,
            fixedPointWidth,
            fixedPointBinaryPoint,
            majorityVoterType,
            buildType,
            buildTarget,
            testCandidates,
            swRelativeClassifications,
            targetClassifications
          )
        }
      }
    parser.decode[HWStageConfig](jsonString)
  }
}

case class HWStageParameters(
  val runDir:            File,
  val buildDir:          File,
  val stdOutStream:      Option[PrintWriter],
  val stdErrStream:      Option[PrintWriter],
  val verbose:           Boolean,
  val hwStageConfigFile: File)
    extends BuildPipelineStageParameters

class HWStage(val p: HWStageParameters) extends BuildPipelineStage {

  def generateVerilog(dut: () => RawModule, targetDir: String): AnnotationSeq = (new chisel3.stage.ChiselStage).execute(
    Array("-X", "verilog"),
    Seq(
      TargetDirAnnotation(targetDir),
      ChiselGeneratorAnnotation(dut)
    )
  )

  override protected def executeUnsafe(): Option[RunStageParameters] = {
    // Read hardware stage configuration generated by software stage
    printToConsoleAndStream(s"Reading hardware stage configuration file: ${p.hwStageConfigFile.getAbsolutePath()}...")
    val hwStageConfigSource = scala.io.Source.fromFile(p.hwStageConfigFile)
    val hwStageConfigLines =
      try hwStageConfigSource.mkString
      finally hwStageConfigSource.close()
    val hwStageConfigParseResult = HWStageConfig(hwStageConfigLines) match {
      case Right(hwStageConfig) => {
        printToConsoleAndStream("Read hardware stage configuration file successfully!")
        hwStageConfig
      }
      case Left(error) => sys.error(error.getMessage())
    }

    // Get CDE configuration for chisel circuit generation
    val chiselCDEConfig = hwStageConfigParseResult.getCDEConfig()

    // Instantiate chisel module based on build type
    val dut = hwStageConfigParseResult.buildType match {
      case "test" => {
        printToConsoleAndStream("Test build type chosen")
        () => new RandomForestClassifierTestHarness()(chiselCDEConfig)
      }
      case "prod" => sys.error("Production build type is not currently supported")
      case u      => sys.error("Unknown build type: " + u)
    }

    // Compiling Chisel to Verilog
    printToConsoleAndStream("Compiling Chisel to Verilog...")
    val annos       = generateVerilog(dut, buildDirAbsolutePath)
    val buildPrefix = annos.collectFirst { case OutputFileAnnotation(f) => f }.get
    val outputVerilogFileName =
      new File(s"${buildDirAbsolutePath}/${buildPrefix}.v")
    printToConsoleAndStream(s"Generated Verilog file: ${outputVerilogFileName.getAbsolutePath()}")
    Some(
      RunStageParameters(
        p.runDir,
        p.buildDir,
        p.stdOutStream,
        p.stdErrStream,
        p.verbose,
        buildPrefix,
        outputVerilogFileName,
        hwStageConfigParseResult.buildTarget
      )
    )
  }
}

object HWStage {
  def apply(p: HWStageParameters) = {
    (new HWStage(p)).execute().map(_.asInstanceOf[RunStageParameters])
  }
}
