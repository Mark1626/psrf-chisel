package psrf

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint

class DecisionTreeNode(FPWidth: Int, FPBinaryPoint: Int, featureIndexWidth: Int, nodeAddrWidth: Int) extends Bundle {
  val threshold    = FixedPoint(FPWidth.W, FPBinaryPoint.BP)
  val featureIndex = UInt(featureIndexWidth.W)
  val rightNode    = UInt(nodeAddrWidth.W)
  val leftNode     = UInt(nodeAddrWidth.W)
}

class DecisionTree(numFeatures: Int, numNodes: Int, FPWidth: Int, FPBinaryPoint: Int) extends Module {
  val nodeAddrWidth     = log2Ceil(numNodes)
  val featureIndexWidth = log2Ceil(numFeatures) + 1

  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(Vec(numFeatures, FixedPoint(FPWidth.W, FPBinaryPoint.BP))))
    val out = Irrevocable(Bool())
  })

  def getDecisionTreeRom(): Vec[DecisionTreeNode] = ???
  val decisionTreeRom:      Vec[DecisionTreeNode] = getDecisionTreeRom()

  val idle :: busy :: Nil = Enum(2)

  val state = RegInit(idle)
  val start = io.in.valid & io.in.ready
  val done  = RegInit(false.B)
  val rest  = io.out.valid & io.out.ready

  val candidate    = Reg(Vec(numFeatures, FixedPoint(FPWidth.W, FPBinaryPoint.BP)))
  val node         = Reg(new DecisionTreeNode(FPWidth, FPBinaryPoint, featureIndexWidth, nodeAddrWidth))
  val nodeAddr     = WireDefault(0.U(nodeAddrWidth.W))
  val prevDecision = RegInit(false.B)
  val out          = Reg(Bool())

  io.out.valid := done
  io.out.bits  := out

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

    when(!done) {
      when(isLeafNode) {
        out  := prevDecision
        done := true.B
      }.otherwise {
        val decision = featureValue <= thresholdValue
        prevDecision := decision
        node         := Mux(decision, decisionTreeRom(node.leftNode), decisionTreeRom(node.rightNode))
      }
    }

    when(rest) {
      state := idle
      done  := false.B
    }
  }
}
