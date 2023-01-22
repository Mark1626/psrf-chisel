package psrf.params

import chipsalliance.rocketchip.config.{Field, Parameters}
import chisel3.util._
import psrf.modules
import psrf.modules.{DecisionTreeNode, TreeLiteral}

case object NumFeatures           extends Field[Int]
case object NumClasses            extends Field[Int]
case object NumTrees              extends Field[Int]
case object FixedPointWidth       extends Field[Int]
case object FixedPointBinaryPoint extends Field[Int]

trait HasFixedPointParams {
  implicit val p: Parameters
  val fixedPointWidth       = p(FixedPointWidth)
  val fixedPointBinaryPoint = p(FixedPointBinaryPoint)
}

trait HasDecisionTreeParams extends HasFixedPointParams {
  implicit val p: Parameters
  val numClasses = p(NumClasses)
  val numFeatures = p(NumFeatures)
  val classIndexWidth = log2Ceil(numClasses)
}

trait HasDecisionTreeParameters extends HasDecisionTreeParams {
  val decisionTreeNodeLiterals = p(TreeLiteral)
  val numNodes                 = decisionTreeNodeLiterals.length
  val nodeAddrWidth            = log2Ceil(numNodes)
  val featureIndexWidth        = log2Ceil(numFeatures)

  def featureClassIndexWidth = math.max(featureIndexWidth, classIndexWidth)
}

/**
 * Decision tree with predefined node during hardware resolution
 */
trait HasDecisionTreeWithNodesParameters extends HasDecisionTreeParameters {
  def decisionTreeNodes      = decisionTreeNodeLiterals.map(modules.DecisionTreeNode(_, p))
}

trait HasRandomForestParams extends HasDecisionTreeParams {
  implicit val p: Parameters
  val numTrees        = p(NumTrees)
}

case class DecisionTreeParams()(implicit val p: Parameters) extends HasDecisionTreeParams

/*
/** Random forest classifier module that performs classification of input candidates */
class RandomForestClassifier(implicit val p: Parameters) extends Module with HasRandomForestParameters {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(Vec(numFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP))))
    val out = Irrevocable(new MajorityVoterOut()(p))
  })

  val decisionTreeArray = Module(new DecisionTreeArraySimple()(p))
  val majorityVoter     = Module(new MajorityVoterArea()(p))

  decisionTreeArray.io.in <> io.in
  majorityVoter.io.in <> decisionTreeArray.io.out
  io.out <> majorityVoter.io.out
}
*/
