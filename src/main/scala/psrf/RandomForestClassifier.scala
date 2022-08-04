package psrf

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import config.{Field, Parameters}

case object NumFeatures           extends Field[Int]
case object NumClasses            extends Field[Int]
case object NumTrees              extends Field[Int]
case object FixedPointWidth       extends Field[Int]
case object FixedPointBinaryPoint extends Field[Int]

trait HasFixedPointParameters {
  implicit val p: Parameters
  val fixedPointWidth       = p(FixedPointWidth)
  val fixedPointBinaryPoint = p(FixedPointBinaryPoint)
}
trait HasRandomForestParameters extends HasFixedPointParameters {
  val numTrees        = p(NumTrees)
  val numClasses      = p(NumClasses)
  val numFeatures     = p(NumFeatures)
  val classIndexWidth = log2Ceil(numClasses)
}

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
