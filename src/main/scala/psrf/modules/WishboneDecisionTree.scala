package psrf.modules

import chipsalliance.rocketchip.config.{Field, Parameters}
import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.util._
import psrf.bus.{BusParams, WishboneMaster}
import psrf.params.{HasDecisionTreeParams, HasFixedPointParams}

case class DecisionTreeConfig(
  maxFeatures: Int,
  maxNodes: Int,
  maxClasses: Int,
  maxDepth: Int
)

case object DecisionTreeConfigKey extends Field[DecisionTreeConfig]

trait HasVariableDecisionTreeParams extends HasFixedPointParams {
  implicit val p: Parameters
  val config = p(DecisionTreeConfigKey)
  val maxFeatures = config.maxFeatures
  val maxNodes = config.maxNodes
  val maxClasses = config.maxClasses
  val maxDepth = config.maxDepth

  val featureIndexWidth = log2Ceil(maxFeatures)
  val classIndexWidth = log2Ceil(maxClasses)

  def featureClassIndexWidth = math.max(featureIndexWidth, classIndexWidth)

  assert(fixedPointWidth == 32)
  assert(featureClassIndexWidth <= 11)
}

// TODO: The width of in interface should be reduced, we are assuming that
//  our features are going to be less. This potentially can be a Wishbone Slave
//
class TreeIO()(implicit val p: Parameters) extends Bundle with HasVariableDecisionTreeParams {
  val in = Flipped(Decoupled(Vec(maxFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP))))
  val out = Decoupled(UInt(9.W))
  val busy = Output(Bool())
}

// Widths are hardcode so they fit the 64 bit bus line, if it exceeds then we raise an exception
// TODO: Review the bus sizes
class TreeNode()(implicit val p: Parameters) extends Bundle with HasVariableDecisionTreeParams {
  val isLeafNode = Bool()
  val featureClassIndex = UInt(9.W)
  val threshold = FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)
  val leftNode = UInt(11.W)
  val rightNode = UInt(11.W)
}

class WishboneDecisionTree(val offset: Int)(implicit val p: Parameters) extends Module
  with HasVariableDecisionTreeParams
  with BusParams {

  // TODO: Add assertion to check if we don't exceed maxFeatures

  // We use a 64 bit bus, can the address width of the bus be reduced to 32 bit?
  assert(busWidth == 64) // Only works on a 64 bit bus

  val io = IO(new Bundle {
    val up = new TreeIO()(p)
    val down = new WishboneMaster(busWidth)
  })

  val idle :: bus_req :: bus_wait :: busy :: done :: Nil = Enum(5)

  val state = RegInit(idle)
  val candidate = Reg(Vec(maxFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)))

  // TODO: Init this to 0
  val node_rd = Reg(new TreeNode()(p))
  val nodeAddr = RegInit(offset.U(busWidth.W))
  val decision = WireDefault(false.B)

  // Bus info
  val activeTrn = RegInit(false.B)

  io.down.bus.stb := activeTrn
  io.down.bus.cyc := activeTrn
  io.down.bus.sel := DontCare // This may be useful in the future for granularity
  io.down.bus.we := false.B
  io.down.bus.data_wr := DontCare
  io.down.bus.addr := nodeAddr

  // Top IO Handling
  io.up.in.ready := state === idle

  io.up.out.valid := state === done
  io.up.out.bits := node_rd.featureClassIndex

  // TODO: The FSM has to go to idle and set err when number of iterations exceeds maxDepth

  // FSM
  when (state === idle && io.up.in.fire) {
    // Decision Tree init
    candidate := io.up.in.bits
    nodeAddr := offset.U // This will change to be an offset for each tree

    state := bus_req
  } .elsewhen (state === bus_req) {

    activeTrn         := true.B
    // TODO: Add handling for io.down.bus.err
    state := bus_wait
  } .elsewhen (state === bus_wait && io.down.bus.ack) {
    val node = io.down.bus.data_rd.asTypeOf(node_rd)
    node_rd   := node
    val featureIndex = node.featureClassIndex
    val featureValue = candidate(featureIndex) // TODO: Can this result in an exception
    val threshold = node.threshold

    when (node.isLeafNode) {
      state := done
    } .otherwise {
      val jumpOffset: UInt = Mux(featureValue <= threshold, node.leftNode, node.rightNode)
      nodeAddr := offset.U + jumpOffset.asUInt
      state := bus_req
    }

    activeTrn := false.B
  } .elsewhen (state === done) {
    activeTrn := false.B
    state := idle
  }

  io.up.busy := state =/= idle
}
