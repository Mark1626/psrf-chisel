package psrf.modules

import chipsalliance.rocketchip.config.Parameters
import chisel3.util._
import chisel3._
import psrf.params.RAMParams

class RandomForestTile()(implicit val p: Parameters) extends Module with RAMParams {
  val dataSize = 64
  val addrSize = ramSize

  def read(addr: UInt, req_valid: Bool, resp_ready: Bool): (UInt, Bool, Bool) = {
    io.read.req.valid := req_valid
    io.read.req.bits.addr := addr
    io.read.resp.ready := resp_ready

    (io.read.resp.bits.data, io.read.req.ready, io.read.resp.valid)
  }

  def write(addr: UInt, data: UInt, req_valid: Bool, resp_ready: Bool): (Bool, Bool, Bool) = {
    io.write.req.valid := req_valid
    io.write.req.bits.addr := addr
    io.write.req.bits.data := data
    io.write.en := req_valid

    io.write.resp.ready := resp_ready

    (io.write.resp.bits.resp, io.write.req.ready, io.write.resp.valid)
  }

  val io = IO(new ReadWriteIO(addrSize, dataSize))

  val weWeights :: operational :: Nil = Enum(2)
  val mode = RegInit(weWeights)

  val idle :: busy :: done :: Nil = Enum(3)
  val state = RegInit(idle)

  val decision = RegInit(0.U(32.W))
  val decisionValid = RegInit(false.B)

  val rfull = RegInit(false.B)
  val rdata = RegInit(0.U)

  when (io.read.req.fire) {
    rfull := true.B
  }

  when (io.read.resp.fire) {
    rfull := false.B
  }

  val addr_sel = io.read.req.bits.addr
  when (io.read.req.fire) {
    switch (addr_sel) {
      is (MMIO_ADDR.CSR.U) { rdata := Cat(0.U(61.W), mode, state === idle, decisionValid) }
      is (MMIO_ADDR.DECISION.U) { rdata :=  Cat(0.U(32.W), decision) }
    }
  }

  io.read.req.ready := io.read.resp.ready && !rfull
  io.read.resp.bits.data := rdata
  io.read.resp.valid := rfull

  val wfull = RegInit(false.B)
  val wen = io.write.en && io.write.req.fire

  when (wen) {
    wfull := true.B
  }

  when (io.write.resp.fire) {
    wfull := false.B
  }

  when (wen) {
    // Do write
  }

  io.write.resp.bits.resp := wfull
  io.write.resp.valid := wfull
  io.write.req.ready := !wfull

}
