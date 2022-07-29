import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.experimental.FixedPoint
import chisel3.experimental.VecLiterals._
import chisel3.internal.firrtl.BinaryPoint
import chisel3.internal.firrtl.Width

package object psrf {
  case class DecisionTreeNodeLit(
    isLeafNode:        Boolean,
    featureClassIndex: Int,
    threshold:         Double,
    rightNode:         Int,
    leftNode:          Int)

  def decisionTreeNodeLitToChiselType(n: DecisionTreeNodeLit, p: DecisionTreeParams): DecisionTreeNode = {
    (new DecisionTreeNode(p)).Lit(
      _.isLeafNode        -> n.isLeafNode.B,
      _.featureClassIndex -> n.featureClassIndex.U(p.featureIndexWidth.W),
      _.threshold         -> FixedPoint.fromDouble(n.threshold, p.fixedPointWidth.W, p.fixedPointBinaryPoint.BP),
      _.rightNode         -> n.rightNode.U(p.nodeAddrWidth.W),
      _.leftNode          -> n.leftNode.U(p.nodeAddrWidth.W)
    )
  }
}
