package psrf

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint

case class DecisionTreeParams(
  numFeatures:           Int,
  numNodes:              Int,
  numClasses:            Int,
  fixedPointWidth:       Int,
  fixedPointBinaryPoint: Int) {
  val nodeAddrWidth     = log2Ceil(numNodes)
  val featureIndexWidth = log2Ceil(numFeatures)
  // TODO Fix unnecessary recalculation of classIndexWidth
  val classIndexWidth = log2Ceil(numClasses)
}

class DecisionTreeNode(p: DecisionTreeParams) extends Bundle {
  import p._
  val isLeafNode        = Bool()
  val featureClassIndex = UInt(math.max(featureIndexWidth, classIndexWidth).W)
  val threshold         = FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)
  val rightNode         = UInt(nodeAddrWidth.W)
  val leftNode          = UInt(nodeAddrWidth.W)
}

class DecisionTree(tree: Seq[DecisionTreeNode], p: DecisionTreeParams) extends Module {
  import p._
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(Vec(numFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP))))
    val out = Irrevocable(UInt(classIndexWidth.W))
  })

  val decisionTreeRom = VecInit(tree)

  val idle :: busy :: done :: Nil = Enum(3)

  val state = RegInit(idle)
  val start = io.in.valid & io.in.ready
  val rest  = io.out.valid & io.out.ready

  val candidate = Reg(Vec(numFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)))
  val node      = Reg(new DecisionTreeNode(p))
  val nodeAddr  = WireDefault(0.U(nodeAddrWidth.W))
  val decision  = WireDefault(false.B)

  io.in.ready  := false.B
  io.out.valid := false.B
  io.out.bits  := 0.U

  // FSM
  when(state === idle) {
    io.in.ready := true.B
    when(start) {
      state     := busy
      candidate := io.in.bits
      node      := decisionTreeRom(0)
    }
  }.elsewhen(state === busy) {
    val featureIndex   = node.featureClassIndex
    val featureValue   = candidate(featureIndex)
    val thresholdValue = node.threshold

    when(node.isLeafNode) {
      state := done
    }.otherwise {
      decision := featureValue <= thresholdValue
      // TODO Check if extra delay needs to be added for ROM access
      node := Mux(decision, decisionTreeRom(node.leftNode), decisionTreeRom(node.rightNode))
    }
  }.elsewhen(state === done) {
    io.out.valid := true.B
    io.out.bits  := node.featureClassIndex(classIndexWidth - 1, 0)
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
