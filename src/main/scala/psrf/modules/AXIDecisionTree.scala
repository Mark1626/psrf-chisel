package psrf.modules

import chipsalliance.rocketchip.config.{Parameters}
import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.util._
import psrf.params.{RAMParams}

class AXIDecisionTree()(implicit val p: Parameters) extends Module
  with HasVariableDecisionTreeParams
  with RAMParams {

  val dataSize = dataWidth
  val addrSize = ramSize

  val io = IO(new Bundle {
    val up = new TreeIO()(p)
    val down = Flipped(new ReadIO(addrSize, dataSize))
  })

  val idle :: bus_req :: bus_wait :: busy :: done :: Nil = Enum(5)

  val state = RegInit(idle)
  val candidate = Reg(Vec(maxFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)))

  // TODO: Init this to 0
  val node_rd = Reg(new TreeNode()(p))
  val nodeAddr = RegInit(0.U(dataWidth.W))
  val offset = Reg(UInt(32.W))
  val decision = WireDefault(false.B)

  io.up.in.ready := state === idle
  io.up.out.valid := state === done
  io.up.out.bits := node_rd.featureClassIndex

  val activeTrn = state === bus_req

  io.down.req.valid := state === bus_req
  io.down.req.bits.addr := nodeAddr
  io.down.resp.ready := state === bus_req || state === bus_wait

  // FSM
  when(state === idle && io.up.in.fire) {
    // Decision Tree init
    candidate := io.up.in.bits.candidates
    offset := io.up.in.bits.offset
    nodeAddr := io.up.in.bits.offset

    state := bus_req
  }.elsewhen(state === bus_req && io.down.req.ready) {
    state := bus_wait
  } .elsewhen (state === bus_wait && io.down.resp.valid) {
    val node = io.down.resp.bits.data.asTypeOf(node_rd)
    node_rd := node

    val featureIndex = node.featureClassIndex
    val featureValue = candidate(featureIndex) // TODO: Can this result in an exception

    when(node.isLeafNode) {
      state := done
    }.otherwise {
      val jumpOffset: UInt = Mux(featureValue <= node.threshold, node.leftNode, node.rightNode)
      nodeAddr := offset + jumpOffset.asUInt // TODO: This could be replaced as nodeAddr := nodeAddr + jumpOffset
      state := bus_req
    }
  } .elsewhen(state === done && io.up.out.ready) {
    state := idle
  }
  io.up.busy := state =/= idle
}
