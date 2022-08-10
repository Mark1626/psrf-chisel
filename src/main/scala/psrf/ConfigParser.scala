package psrf

import io.circe.parser
import io.circe.HCursor
import io.circe.Decoder
import io.circe.Json
import io.circe.DecodingFailure
import config.{Config, Parameters}

case class HWStageConfig(
  classLabels:             List[Int],
  numFeatures:             Int,
  numClasses:              Int,
  numTrees:                Int,
  treeLiterals:            List[List[DecisionTreeNodeLit]],
  fixedPointWidth:         Int,
  fixedPointBinaryPoint:   Int,
  majorityVoterType:       String,
  buildType:               String,
  buildTarget:             String,
  testCandidates:          Option[List[List[Double]]],
  expectedClassifications: Option[List[Int]]) {

  def getCDEConfig(): Parameters = {
    new Config((site, here, up) => {
      case NumFeatures           => numFeatures
      case NumClasses            => numClasses
      case NumTrees              => numTrees
      case FixedPointWidth       => fixedPointWidth
      case FixedPointBinaryPoint => fixedPointBinaryPoint
      case TreeLiterals          => treeLiterals
      case TestHarnessKey =>
        if (buildType == "test") TestHarnessParams(testCandidates.get, expectedClassifications.get)
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
          expectedClassifications <- buildType match {
            case "test" => {
              hCursor.get[List[Int]]("expected_classifications") match {
                case Right(ec) => Right(Some(ec))
                case Left(_) =>
                  Left(DecodingFailure("Expected classfications not found for test build", hCursor.history))
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
            expectedClassifications
          )
        }
      }
    parser.decode[HWStageConfig](jsonString)
  }
}
