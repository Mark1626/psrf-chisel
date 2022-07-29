package psrf

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint

case class RandomForestClassifierParams(
  numTrees:              Int,
  numNodes:              Seq[Int],
  numClasses:            Int,
  numFeatures:           Int,
  fixedPointWidth:       Int,
  fixedPointBinaryPoint: Int,
  treesLit:              Seq[Seq[DecisionTreeNodeLit]]) {
  require(numNodes.length == numTrees, "Number of numNodes provided does not match number of trees")
  val classIndexWidth = log2Ceil(numClasses)
  val decisionTreeArrayParams =
    DecisionTreeArrayParams(
      numTrees,
      numNodes,
      numClasses,
      numFeatures,
      fixedPointWidth,
      fixedPointBinaryPoint,
      treesLit
    )
}

class RandomForestClassifier(p: RandomForestClassifierParams) extends Module {
  import p._
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(Vec(numFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP))))
    val out = Irrevocable(new MajorityVoterOut(classIndexWidth))
  })

  val decisionTreeArray = Module(new DecisionTreeArraySimple(decisionTreeArrayParams))
  val majorityVoter     = Module(new MajorityVoterArea(numTrees, numClasses))

  decisionTreeArray.io.in <> io.in
  majorityVoter.io.in <> decisionTreeArray.io.out
  io.out <> majorityVoter.io.out
}
