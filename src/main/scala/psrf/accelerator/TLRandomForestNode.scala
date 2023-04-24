package psrf.accelerator

import chisel3._
import chipsalliance.rocketchip.config.Parameters
import chisel3.experimental.FixedPoint
import chisel3.util.{Enum, log2Ceil}
import freechips.rocketchip.diplomacy.{AddressSet, IdRange, LazyModule, LazyModuleImp}
import psrf.modules.{RandomForestNodeModule, TreeIO, TreeNode}
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
    val rfNode = Module(new RandomForestNodeModule(address.base, address.mask, beatBytesShift)(p))

    rfNode.io <> io

    rfNode.busReq.ready := mem.a.ready
    mem.a.bits := edge.Get(
      fromSource = 0.U,
      toAddress = rfNode.busReq.bits,
      lgSize = log2Ceil(beatBytes).U)._2
    mem.a.valid := rfNode.busReq.valid

    mem.d.ready := rfNode.busResp.ready
    rfNode.busResp.bits := mem.d.bits.data
    rfNode.busResp.valid := mem.d.valid

    rfNode.busReqDone := edge.done(mem.a)
  }
}
