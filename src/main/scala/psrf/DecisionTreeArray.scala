package psrf

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint

case class DecisionTreeArrayParams(
  numTrees:              Int,
  numNodes:              Seq[Int],
  numFeatures:           Int,
  fixedPointWidth:       Int,
  fixedPointBinaryPoint: Int,
  trees:                 Seq[Seq[DecisionTreeNode]]) {
  require(numNodes.length == numTrees, "Number of numNodes provided do not match number of trees")
  require(trees.length == numTrees, "Number of tree ROMs provided do not match number of trees")

  val decisionTreeParams = numNodes.map(n => DecisionTreeParams(numFeatures, n, fixedPointWidth, fixedPointBinaryPoint))
}

class DecisionTreeArraySimple(p: DecisionTreeArrayParams) extends Module {
  import p._
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(Vec(numFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP))))
    val out = Irrevocable(Vec(numTrees, Bool()))
  })

  val decisionTrees = (0 until numTrees).map(i => DecisionTree(trees(i), decisionTreeParams(i)))

  io.in.ready  := decisionTrees.foldLeft(true.B) { (r, tree) => WireDefault(r & tree.io.in.ready) }
  io.out.valid := decisionTrees.foldLeft(true.B) { (v, tree) => WireDefault(v & tree.io.out.valid) }

  decisionTrees
    .zip(io.out.bits)
    .foreach {
      case (t, o) => {
        t.io.in.valid  := io.in.valid
        t.io.out.ready := io.out.ready
        t.io.in.bits   := io.in.bits
        o              := t.io.out.bits
      }
    }
}
