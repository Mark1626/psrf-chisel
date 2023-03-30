package psrf.params

import chipsalliance.rocketchip.config.{Field, Parameters}
import chisel3._
import chisel3.util._

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
  val featureIndexWidth = log2Ceil(numFeatures)

  def featureClassIndexWidth = math.max(featureIndexWidth, classIndexWidth)
}

case object TreeLiteral extends Field[List[DecisionTreeNodeLit]](Nil)

/** Represent a literal node in a decision tree with Scala datatypes. */
case class DecisionTreeNodeLit(
      isLeafNode:        Boolean,
      featureClassIndex: Int,
      threshold:         Double,
      rightNode:         Int,
      leftNode:          Int)

trait HasRandomForestParams extends HasDecisionTreeParams {
  implicit val p: Parameters
  val numTrees        = p(NumTrees)
}

case class DecisionTreeParams()(implicit val p: Parameters) extends HasDecisionTreeParams

case class DecisionTreeConfig(
                               maxFeatures: Int,
                               maxNodes: Int,
                               maxClasses: Int,
                               maxDepth: Int
                             )

case object DecisionTreeConfigKey extends Field[DecisionTreeConfig]

trait HasVariableDecisionTreeParams extends HasFixedPointParams {
  implicit val p: Parameters
  val config = p(DecisionTreeConfigKey)
  val maxFeatures = config.maxFeatures
  val maxNodes = config.maxNodes
  val maxClasses = config.maxClasses
  val maxDepth = config.maxDepth

  val featureIndexWidth = log2Ceil(maxFeatures)
  val classIndexWidth = log2Ceil(maxClasses)

  def featureClassIndexWidth = math.max(featureIndexWidth, classIndexWidth)

  assert(fixedPointWidth == 32)
  assert(featureClassIndexWidth <= 11)
}
