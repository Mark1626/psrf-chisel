package psrf.modules


import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import psrf.params.HasRandomForestParams

class RandomForestNodeModule(
  val address_base: BigInt,
  val address_mask: BigInt,
  val beatBytesShift: Int
)(implicit val p: Parameters) extends Module
  with HasRandomForestParams {

  val io = IO(new TreeIO()(p))

  val busReq = IO(Decoupled(UInt(32.W)))
  val busReqDone = IO(Input(Bool()))
  val busResp = IO(Flipped(Decoupled(UInt(64.W))))

  val idle :: bus_req_wait :: bus_req :: bus_resp_wait :: done :: Nil = Enum(5)
  val state = RegInit(idle)
  val candidate = Reg(Vec(maxFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)))

  // -1.995848273771531
  val node_rd = Reg(new TreeNode()(p))
  val nodeAddr = RegInit(address_base.U)

  val error = RegInit(0.U(2.W))

  //val offset = Reg(UInt(32.W))

  val readRootNode = RegInit(false.B)

  io.in.ready := state === idle
  io.out.valid := state === done
  io.out.bits.classes := node_rd.featureClassIndex

  io.out.bits.error := error

  // TODO: Add a condition to make sure nodeAddress does not exceed scratchpad size
  val scratchpadLimit = address_base + address_mask
  when(nodeAddr >= scratchpadLimit.U) {
    state := done
    error := 1.U
  }

  busReq.valid := state === bus_req
  busReq.bits := nodeAddr

  busResp.ready := state === bus_resp_wait

  when(state === idle && io.in.fire) {
    candidate := io.in.bits.candidates
    //offset := address.base.U(32.W) + (io.in.bits.offset << beatBytesShift)
    nodeAddr := address_base.U + (io.in.bits.offset << beatBytesShift)
    state := bus_req_wait
    readRootNode := true.B
    error := 0.U
  }

  when(state === bus_req_wait && busReq.ready) {
    state := bus_req
  }

  when(state === bus_req && busReqDone) {
    state := bus_resp_wait
  }

  when(state === bus_resp_wait && busResp.fire) {
    // First time we get tree's root node address
    when(readRootNode) {
      // TODO: Move the constant 128 outside
      nodeAddr := address_base.U + ((128.U + busResp.bits) << beatBytesShift)
      readRootNode := false.B
      state := bus_req_wait
    }.otherwise {
      // Rest of the time it's nodes for the tree
      val node = busResp.bits.asTypeOf(node_rd)
      node_rd := node

      val featureIndex = node.featureClassIndex
      val featureValue = candidate(featureIndex) // TODO: Can this result in an exception

      when(node.isLeafNode) {
        state := done
      }.otherwise {
        val jumpOffset: UInt = Mux(featureValue <= node.threshold, node.leftNode, node.rightNode)
        nodeAddr := nodeAddr + (jumpOffset.asUInt << beatBytesShift) // TODO: This could be replaced as nodeAddr := nodeAddr + jumpOffset
        state := bus_req_wait
      }
    }
  }

  when(state === done && io.out.ready) {
    state := idle
  }

  io.busy := state =/= idle
}
