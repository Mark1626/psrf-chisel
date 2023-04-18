package psrf.modules

import chisel3._
import chisel3.util.{Decoupled, log2Ceil, Queue}
import chisel3.SyncReadMem

class Scratchpad(val sp_bank_entries: Int, val sp_width: Int) extends Module {
  val io = IO(new ReadWriteIO(sp_bank_entries, sp_width))

  val (read, write) = {
    val mem = SyncReadMem(sp_bank_entries, UInt(sp_width.W))
    def read(addr: UInt, ren: Bool): Data = mem.read(addr, ren)
    def write(addr: UInt, wdata: UInt): Unit = mem.write(addr, wdata)
    (read _, write _)
  }

  val singleport_busy_with_write = io.write.en
  val wen = io.write.en && io.write.req.fire

  when (wen) {
    write(io.write.req.bits.addr, io.write.req.bits.data)
  }

  val qw = Module(new Queue(new WriteResp(), 1, true, true))
  qw.io.enq.valid := RegNext(wen)
  qw.io.enq.bits.resp := true.B

  val qw_will_be_empty = (qw.io.count +& qw.io.enq.fire) - qw.io.deq.fire === 0.U
  io.write.req.ready := qw_will_be_empty

  io.write.resp <> qw.io.deq

  // Read
  val ren = io.read.req.fire
  val raddr = io.read.req.bits.addr
  val rdata = read(raddr, ren && !io.write.en)

  val qr = Module(new Queue(new ReadResp(sp_width), 1, true, true))
  qr.io.enq.valid := RegNext(ren)
  qr.io.enq.bits.data := rdata

  val qr_will_be_empty = (qr.io.count +& qr.io.enq.fire) - qr.io.deq.fire === 0.U
  io.read.req.ready := qr_will_be_empty && !singleport_busy_with_write

  io.read.resp <> qr.io.deq
}
