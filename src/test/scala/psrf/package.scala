package psrf

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.experimental.FixedPoint
import chisel3.experimental.VecLiterals._
import chisel3.internal.firrtl.BinaryPoint
import chisel3.internal.firrtl.Width

package object test {
  case class DecisionTreeNodeLit(threshold: Double, featureIndex: Int, rightNode: Int, leftNode: Int)

  def decisionTreeNodeLitToChiselType(n: DecisionTreeNodeLit, p: DecisionTreeParams): DecisionTreeNode = {
    (new DecisionTreeNode(p)).Lit(
      _.featureIndex -> n.featureIndex.U(p.featureIndexWidth.W),
      _.threshold    -> FixedPoint.fromDouble(n.threshold, p.fixedPointWidth.W, p.fixedPointBinaryPoint.BP),
      _.rightNode    -> n.rightNode.U(p.nodeAddrWidth.W),
      _.leftNode     -> n.leftNode.U(p.nodeAddrWidth.W)
    )
  }

  implicit class fromSeqDoubleToLiteral(s: Seq[Double]) {
    def asFixedPointVecLit(width: Width, binaryPoint: BinaryPoint): Vec[FixedPoint] =
      Vec.Lit(s.map(d => d.F(width, binaryPoint)): _*)
  }

}
