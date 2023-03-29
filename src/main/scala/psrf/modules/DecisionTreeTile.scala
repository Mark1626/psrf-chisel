package psrf.modules

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.util._
import psrf.bus.{WishboneMaster, WishboneSlave}
import psrf.params.{BusParams, RAMParams}

object AXI_MMIO_ADDR {
  val CSR = 0x00
  val CHANGE_MODE = 0x08
  val CANDIDATE_IN = 0x10
  val DECISION = 0x18
  val WEIGHTS_IN = 0x20
  val WEIGHTS_OUT = 0x28
}

class RFTile()(implicit val p: Parameters) extends Module with RAMParams {
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

  val rfull = RegInit(false.B)
  val rdata = RegInit(0.U)

  when (io.read.req.fire) {
    rfull := true.B
  }

  when (io.read.resp.fire) {
    rfull := false.B
  }

  when (io.read.req.fire) {
    rdata := io.read.req.bits.addr
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

//  val init :: operational :: Nil = Enum(2)
//  val mode = RegInit(init)
//  //  val decision = Reg(UInt(32.W))
//  val decision = RegInit(3.U(32.W))
//
//  val idle :: busy :: done :: Nil = Enum(3)
//  val state = RegInit(idle)
//
//  io.read.req.ready := true.B
//  val raddr = io.read.req.bits.addr
//  val rdata = Wire(UInt(64.W))
//
//  rdata := 0.U
//  switch(raddr) {
//    is(AXI_MMIO_ADDR.CSR.U) {
//      rdata := Cat(0.U(61.W), mode, state === idle, false.B)
//    }
//    is(AXI_MMIO_ADDR.DECISION.U) {
//      rdata := Cat(0.U(32.W), decision)
//    }
//    is(AXI_MMIO_ADDR.WEIGHTS_OUT.U) {
//      //      decisionTreeTile.io.up.bus.stb := true.B
//      //      decisionTreeTile.io.up.bus.cyc := true.B
//      //      ack := decisionTreeTile.io.up.bus.ack
//      //      data_rd := decisionTreeTile.io.up.bus.data_rd
//    }
//  }

}

class AXIDecisionTreeTile()(implicit val p: Parameters) extends Module
  with RAMParams {
  val dataSize = 64
  val addrSize = ramSize

  val io = IO(new Bundle {
    val up = new ReadWriteIO(addrSize, dataSize)
//    val tree = new TreeIO()(p)
    val operational = Input(Bool())
  })

  val scratchpad = Module(new Scratchpad(addrSize, dataSize))

//  when (io.operational) {
    io.up <> scratchpad.io
//  }

}

class DecisionTreeTile()(implicit val p: Parameters) extends Module
  with BusParams
  with RAMParams {
  val io = IO(new Bundle {
    val up = new WishboneSlave(busWidth)
    val tree = new TreeIO()(p)
    val operational = Input(Bool())
  })

  val decisionTree = Module(new WishboneDecisionTree()(p))
  val scratchpad = Module(new WishboneScratchpad()(p))

  when (io.operational) {
    // Connect to decision tree
    io.tree <> decisionTree.io.up
    scratchpad.io <> decisionTree.io.down

    // Wishbone IO to scratchpad is DontCare
    io.up <> DontCare
    io.up.bus.ack := false.B
  } .otherwise {
    decisionTree.io <> DontCare
    decisionTree.io.down.bus.ack := false.B
    io.tree <> DontCare

    // Connect to scratchpad by
    io.up <> scratchpad.io
  }
//  when (!io.operational)
}
