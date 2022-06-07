package psrf

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint

case class DecisionTreeParams(
  numFeatures:           Int,
  numNodes:              Int,
  fixedPointWidth:       Int,
  fixedPointBinaryPoint: Int) {
  val nodeAddrWidth     = log2Ceil(numNodes)
  val featureIndexWidth = log2Ceil(numFeatures) + 1
}

class DecisionTreeNode(p: DecisionTreeParams) extends Bundle {
  import p._
  val threshold    = FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)
  val featureIndex = UInt(featureIndexWidth.W)
  val rightNode    = UInt(nodeAddrWidth.W)
  val leftNode     = UInt(nodeAddrWidth.W)
}

class DecisionTree(tree: Seq[DecisionTreeNode], p: DecisionTreeParams) extends Module {
  import p._
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(Vec(numFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP))))
    val out = Irrevocable(Bool())
  })

  val decisionTreeRom = VecInit(tree)

  val idle :: busy :: done :: Nil = Enum(3)

  val state = RegInit(idle)
  val start = io.in.valid & io.in.ready
  val rest  = io.out.valid & io.out.ready

  val candidate    = Reg(Vec(numFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)))
  val node         = Reg(new DecisionTreeNode(p))
  val nodeAddr     = WireDefault(0.U(nodeAddrWidth.W))
  val decision     = WireDefault(false.B)
  val prevDecision = RegInit(false.B)

  io.in.ready  := false.B
  io.out.valid := false.B
  io.out.bits  := false.B

  // FSM
  when(state === idle) {
    io.in.ready := true.B
    when(start) {
      state     := busy
      candidate := io.in.bits
      node      := decisionTreeRom(0)
    }
  }.elsewhen(state === busy) {
    val featureIndex   = node.featureIndex
    val featureValue   = candidate(featureIndex)
    val thresholdValue = node.threshold
    val isLeafNode     = featureIndex(featureIndexWidth - 1)

    when(isLeafNode) {
      state := done
    }.otherwise {
      decision     := featureValue <= thresholdValue
      prevDecision := decision
      // TODO Check if extra delay needs to be added for ROM access
      node := Mux(decision, decisionTreeRom(node.leftNode), decisionTreeRom(node.rightNode))
    }
  }.elsewhen(state === done) {
    io.out.valid := true.B
    io.out.bits  := prevDecision
    when(rest) {
      state := idle
    }
  }
}

object DecisionTree {
  def apply(tree: Seq[DecisionTreeNode], p: DecisionTreeParams): DecisionTree = {
    Module(new DecisionTree(tree, p))
  }

  // TODO Add factory method to construct DecisionTree where numNodes parameter is based on the length of the input tree sequence
}
