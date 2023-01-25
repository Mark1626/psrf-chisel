package psrf.modules

import chipsalliance.rocketchip.config.{Field, Parameters}
import chisel3.experimental.BundleLiterals._
import chisel3.experimental.{BaseModule, FixedPoint}
import chisel3.util._
import chisel3.{when, _}
import psrf.params.{HasDecisionTreeParameters, HasDecisionTreeWithNodesParameters}

case object TreeLiteral extends Field[List[DecisionTreeNodeLit]](Nil)

/** Represent a literal node in a decision tree with Scala datatypes. */
case class DecisionTreeNodeLit(
  isLeafNode:        Boolean,
  featureClassIndex: Int,
  threshold:         Double,
  rightNode:         Int,
  leftNode:          Int)

/** Represent a node in a decision tree as Chisel data. */
class DecisionTreeNode(implicit val p: Parameters) extends Bundle with HasDecisionTreeParameters {
  val isLeafNode        = Bool()
  val featureClassIndex = UInt(featureClassIndexWidth.W)
  val threshold         = FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)
  val rightNode         = UInt(nodeAddrWidth.W)
  val leftNode          = UInt(nodeAddrWidth.W)
}

object DecisionTreeNode {

  /** Converts a literal decision tree node [[DecisionTreeNodeLit]] to chisel bundle [[DecisionTreeNode]].
    */
  def apply(n: DecisionTreeNodeLit, p: Parameters): DecisionTreeNode = {
    // TODO Fix this hack
    val dtb = new DecisionTreeNode()(p)
    (new DecisionTreeNode()(p)).Lit(
      _.isLeafNode        -> n.isLeafNode.B,
      _.featureClassIndex -> n.featureClassIndex.U(dtb.featureClassIndexWidth.W),
      _.threshold         -> FixedPoint.fromDouble(n.threshold, dtb.fixedPointWidth.W, dtb.fixedPointBinaryPoint.BP),
      _.rightNode         -> (if (n.rightNode < 0) 0 else n.rightNode).U(dtb.nodeAddrWidth.W),
      _.leftNode          -> (if (n.leftNode < 0) 0 else n.leftNode).U(dtb.nodeAddrWidth.W)
    )
  }
}

class DecisionTreeIO()(implicit val p: Parameters) extends Bundle with HasDecisionTreeParameters {
  val in = Flipped(Decoupled(Vec(numFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP))))
  val out = Decoupled(UInt(classIndexWidth.W))
  val busy = Output(Bool())
}

trait HasDecisionTreeIO extends BaseModule {
  implicit val p: Parameters
  val io = IO(new DecisionTreeIO()(p))
}

/** Decision tree module which performs classification of an input candidate by traversing an internal ROM of
  * [[DecisionTreeNode]].
  */
class DecisionTreeWithNodesChiselModule(implicit val p: Parameters) extends Module
  with HasDecisionTreeWithNodesParameters
  with HasDecisionTreeIO {

  // ROM of decision tree nodes
  val decisionTreeRom = VecInit(decisionTreeNodes)

  val idle :: busy :: done :: Nil = Enum(3)

  val state = RegInit(idle)
  val start = io.in.valid & io.in.ready
  val rest  = io.out.valid & io.out.ready

  val candidate = Reg(Vec(numFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)))
  val node      = Reg(new DecisionTreeNode()(p))
  val nodeAddr  = WireDefault(0.U(nodeAddrWidth.W))
  val decision  = WireDefault(false.B)

  io.in.ready  := state === idle
  io.out.valid := state === done

  io.out.bits  := node.featureClassIndex(classIndexWidth - 1, 0)

  // FSM
  when(state === idle) {
    //io.in.ready := true.B
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
    //io.out.valid := true.B
    state := idle
  }

  io.busy := state =/= idle
}
