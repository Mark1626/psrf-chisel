package psrf

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import chisel3.experimental.BundleLiterals._
import config.{Field, Parameters}

case object TreeLiteral extends Field[List[DecisionTreeNodeLit]](Nil)

trait HasDecisionTreeParameters extends HasRandomForestParameters {
  val decisionTreeNodeLiterals = p(TreeLiteral)
  val numNodes                 = decisionTreeNodeLiterals.length
  val nodeAddrWidth            = log2Ceil(numNodes)
  val featureIndexWidth        = log2Ceil(numFeatures)

  def featureClassIndexWidth = math.max(featureIndexWidth, classIndexWidth)
  def decisionTreeNodes      = decisionTreeNodeLiterals.map(DecisionTreeNode(_, p))
}

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
  val featureClassIndex = UInt()
  val threshold         = FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)
  val rightNode         = UInt(nodeAddrWidth.W)
  val leftNode          = UInt(nodeAddrWidth.W)
}

object DecisionTreeNode {

  /** Converts a literal decision tree node [[psrf.DecisionTreeNodeLit]] to chisel bundle [[psrf.DecisionTreeNode]].
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

/** Decision tree module which performs classification of an input candidate by traversing an internal ROM of
  * [[psrf.DecisionTreeNode]].
  */
class DecisionTree(implicit val p: Parameters) extends Module with HasDecisionTreeParameters {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(Vec(numFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP))))
    val out = Irrevocable(UInt(classIndexWidth.W))
  })

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
