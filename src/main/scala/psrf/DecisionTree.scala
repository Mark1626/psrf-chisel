package psrf

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint

class DecisionTreeNode(FPWidth: Int, FPBinaryPoint: Int, featureWidth: Int, nodeAddrWidth: Int) extends Bundle {
  val threshold = FixedPoint(FPWidth.W, FPBinaryPoint.BP)
  val feature   = UInt(featureWidth.W)
  val rightNode = UInt(nodeAddrWidth.W)
  val leftNode  = UInt(nodeAddrWidth.W)
}

class DecisionTree(numFeatures: Int, numNodes: Int, FPWidth: Int, FPBinaryPoint: Int) extends Module {
  val nodeAddrWidth = log2Ceil(numNodes)

  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(Vec(numFeatures, FixedPoint(FPWidth.W, FPBinaryPoint.BP))))
    val out = Irrevocable(Bool())
  })

  def getDecisionTreeRom(): Vec[DecisionTreeNode] = ???
  val decisionTreeRom = getDecisionTreeRom()

  val idle :: busy :: Nil = Enum(2)

  val state = RegInit(idle)
  val start = io.in.valid & io.in.ready
  val done  = WireInit(false.B)
  val rest  = io.out.valid & io.out.ready

  val out      = Reg(Bool())
  val outValid = Reg(Bool())

  // FSM
  when(state === idle) {
    io.in.ready := true.B
    outValid    := false.B
    when(start) {
      state := busy
    }
  }.elsewhen(state === busy) {
    when(rest) {
      state := idle
    }
  }
}
