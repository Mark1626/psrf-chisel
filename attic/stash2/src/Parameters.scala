package psrf.params

import chipsalliance.rocketchip.config.{Field, Parameters}
import chisel3._
import chisel3.util._

case object NumFeatures           extends Field[Int]
case object NumClasses            extends Field[Int]
case object NumTrees              extends Field[Int]

trait HasDecisionTreeParams extends HasFixedPointParams {
  implicit val p: Parameters
  val numClasses = p(NumClasses)
  val numFeatures = p(NumFeatures)
  val classIndexWidth = log2Ceil(numClasses)
  val featureIndexWidth = log2Ceil(numFeatures)

  def featureClassIndexWidth = math.max(featureIndexWidth, classIndexWidth)
}

trait HasRandomForestParams extends HasDecisionTreeParams {
  implicit val p: Parameters
  val numTrees        = p(NumTrees)
}

case class DecisionTreeParams()(implicit val p: Parameters) extends HasDecisionTreeParams

