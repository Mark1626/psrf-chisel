package psrf

import chisel3._
import chisel3.experimental.FixedPoint

class Node(FPWidth: Int, FPBinaryPoint: Int, featureWidth: Int, nodeAddrWidth: Int) extends Bundle {
  val threshold = FixedPoint(FPWidth.W, FPBinaryPoint.BP)
  val feature   = UInt(featureWidth.W)
  val rightNode = UInt(nodeAddressWidth.W)
  val leftNode  = UInt(nodeAddressWidth.W)
}
