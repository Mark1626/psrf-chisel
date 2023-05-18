package psrf.modules

import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util.{Decoupled, log2Ceil}
import psrf.params.HasDecisionTreeParams
import chisel3.experimental.FixedPoint

class TreeInputBundle()(implicit val p: Parameters) extends Bundle with HasDecisionTreeParams {
  val candidates = Vec(maxFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP))
  val offset = UInt(32.W)
}

class TreeOutputBundle() extends Bundle {
  val classes = UInt(9.W)
  val error = UInt(2.W)
}

class TreeNode()(implicit val p: Parameters) extends Bundle with HasDecisionTreeParams {
  val isLeafNode = Bool()
  val featureClassIndex = UInt(9.W)
  val threshold = FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)
  val leftNode = UInt(11.W)
  val rightNode = UInt(11.W)
}

// TODO: The width of in interface should be reduced, we are assuming that
//  our features are going to be less. This potentially can be a Wishbone Slave
//
class TreeIO()(implicit val p: Parameters) extends Bundle with HasDecisionTreeParams {
  val in = Flipped(Decoupled(new TreeInputBundle()))
  val out = Decoupled(new TreeOutputBundle())
  val busy = Output(Bool())
}
