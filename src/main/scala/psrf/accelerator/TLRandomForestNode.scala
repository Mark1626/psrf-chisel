package psrf.accelerator

import chisel3._
import chipsalliance.rocketchip.config.Parameters
import chisel3.experimental.FixedPoint
import chisel3.util.{Enum, log2Ceil}
import freechips.rocketchip.diplomacy.{AddressSet, IdRange, LazyModule, LazyModuleImp}
import psrf.modules.{TreeIO, TreeNode}
import psrf.params.HasDecisionTreeParams
import testchipip.TLHelper

class TLRandomForestNode(val address: AddressSet,
  id: IdRange = IdRange(0, 1),
  beatBytes: Int = 4,
  aligned: Boolean = false,
)(implicit p: Parameters) extends LazyModule with HasDecisionTreeParams {
  val psrfMaster = TLHelper.makeClientNode(name=name, sourceId = id)

  lazy val module = new LazyModuleImp(this) {
    val (mem, edge) = psrfMaster.out.head

    val beatBytesShift = log2Ceil(beatBytes)

    val io = IO(new TreeIO()(p))

    val idle :: bus_req_wait :: bus_req :: bus_resp_wait :: done :: Nil = Enum(5)
    val state = RegInit(idle)
    val candidate = Reg(Vec(maxFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)))

    val node_rd = Reg(new TreeNode()(p))
    val nodeAddr = RegInit(address.base.U(32.W))

    val error = RegInit(0.U(2.W))

    //val offset = Reg(UInt(32.W))

    val readRootNode = RegInit(false.B)

    io.in.ready := state === idle
    io.out.valid := state === done
    io.out.bits.classes := node_rd.featureClassIndex

    io.out.bits.error := error

    // TODO: Add a condition to make sure nodeAddress does not exceed scratchpad size
    val scratchpadLimit = address.base + address.mask
     when (nodeAddr >= scratchpadLimit.U) {
       state := done
       error := 1.U
     }

    //mem.a.ready
    mem.a.valid := state === bus_req
    mem.a.bits := edge.Get(
      fromSource = 0.U,
      toAddress = nodeAddr,
      lgSize = log2Ceil(beatBytes).U)._2


    mem.d.ready := state === bus_resp_wait

    when (state === idle && io.in.fire) {
      candidate := io.in.bits.candidates
      //offset := address.base.U(32.W) + (io.in.bits.offset << beatBytesShift)
      nodeAddr := address.base.U(32.W) + (io.in.bits.offset << beatBytesShift)
      state := bus_req_wait
      readRootNode := true.B
      error := 0.U
    }

    when (state === bus_req_wait && mem.a.ready) {
      state := bus_req
    }

    when(state === bus_req && edge.done(mem.a)) {
      state := bus_resp_wait
    }

    when(state === bus_resp_wait && mem.d.fire) {
      // First time we get tree's root node address
      when (readRootNode) {
        nodeAddr := mem.d.bits.data
        readRootNode := false.B
        state := bus_req_wait
      } .otherwise {
        // Rest of the time it's nodes for the tree
        val node = mem.d.bits.data.asTypeOf(node_rd)
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

    io.busy := state =/=  idle
  }
}