package psrf.modules

import chisel3._
import chisel3.util._
import chisel3.experimental.BaseModule
import chipsalliance.rocketchip.config.{Config, Field, Parameters}
import psrf.params.RAMBankParams

class Indexed[+T <: Data](gen: T, w: Int) extends Bundle {
  val data = Output(gen)
  val idx = Output(UInt(w.W))
}

object Indexed {
  /** Wrap some [[Data]] in a indexed interface
   *  @tparam T the type of data to wrap
   *  @param gen the data to wrap
   *  @param w the bitwidth of the index
   *  @return the wrapped input data
   */
  def apply[T <: Data](gen: T, w: Int): Indexed[T] = new Indexed(gen, w)
}

class MemReq(val dataWidth: Int, addrWidth: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
  val wrena = Bool()
  val data = UInt(dataWidth.W)
}

class MemRes(val w: Int) extends Bundle {
  val data = UInt(w.W)
}

/**
 * For write requests idx is DontCare
 * For read requests req.data is DontCare
 */
class RAMBankIndexed(val idx_w: Int)(implicit val p: Parameters) extends Module with RAMBankParams {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(Indexed(new MemReq(dataWidth, addrWidth), idx_w)))
    val res = Decoupled(Indexed(new MemRes(dataWidth), idx_w))
  })

  val (read, write) = {
    val mem = SyncReadMem(ramSize, UInt(dataWidth.W))
    def read(addr: UInt, ren: Bool): Data = mem.read(addr, ren)
    def write(addr: UInt, wdata: UInt) = mem.write(addr, wdata)
    (read _, write _)
  }

  val ren = io.req.fire && !io.req.bits.data.wrena
  val wen = io.req.fire && io.req.bits.data.wrena

  val addr = io.req.bits.data.addr
  val curr_idx = io.req.bits.idx
  val rdata = read(addr, ren)

  when (wen) {
    write(addr, io.req.bits.data.data)
  }

  val q = Module(new Queue(Indexed(new MemRes(dataWidth), idx_w), readBankSize))
  q.io.enq.valid := RegNext(ren)
  q.io.enq.bits.data.data := rdata
  q.io.enq.bits.idx := curr_idx

  q.io.deq.ready := io.res.ready
  io.res.bits <> q.io.deq.bits
  io.res.valid := q.io.deq.fire

  io.req.ready := q.io.enq.ready
}
