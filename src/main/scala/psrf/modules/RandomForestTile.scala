package psrf.modules

import chipsalliance.rocketchip.config.Parameters
import chisel3.util._
import chisel3._
import chisel3.experimental.FixedPoint
import psrf.params.{HasVariableDecisionTreeParams, RAMParams}

class Candidate()(implicit val p: Parameters) extends Bundle with HasVariableDecisionTreeParams {
  val data = FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)
}

/**
 *
 * MMIO Layout
 *
 * CSR
 * 0x00  = R = CSR            = {61'b0, mode, ready, valid}
 * 0x08  = W = Change Mode    = {offset, size, mode}
 *
 * Data
 * 0x10  = W = Candidate In   = {last, idx, candidate}
 * 0x18  = R = Decision       = Final Decision
 * 0x20  = W = Weights In     = {1'threshold, 9'featureClass, 32'threshold, 11'leftNode, 11'rightNode}
 * 0x28  = R = Weights Out    = {1'threshold, 9'featureClass, 32'threshold, 11'leftNode, 11'rightNode}
 *
 * Error and Exceptions
 * // TODO: Add Error Registerss
 *
 */
class RandomForestTile()(implicit val p: Parameters) extends Module
  with HasVariableDecisionTreeParams
  with RAMParams {

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

  val io = IO(new ReadWriteIO(ramSize, dataWidth))

  val weWeights :: operational :: Nil = Enum(2)
  val mode = RegInit(weWeights)

  val idle :: busy :: done :: Nil = Enum(3)
  val state = RegInit(idle)

  val candidates = Reg(Vec(maxFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)))
  val decision = RegInit(0.U(32.W))
  val decisionValid = RegInit(false.B)

  val rfull = RegInit(false.B)
  val rdata = RegInit(0.U)

  when(io.read.resp.fire) {
    rfull := false.B
  }

  when (io.read.req.fire) {
    rfull := true.B
  }

  val raddr_sel = io.read.req.bits.addr
  when (io.read.req.fire) {
    switch (raddr_sel) {
      is (MMIO_ADDR.CSR.U) { rdata := Cat(0.U(61.W), mode, state === idle, decisionValid) }
      is (MMIO_ADDR.DECISION.U) { rdata :=  Cat(0.U(32.W), decision) }
    }
  }

  io.read.req.ready := !rfull
  io.read.resp.bits.data := rdata
  io.read.resp.valid := rfull

  val wfull = RegInit(false.B)
  //val wen = io.write.en && io.write.req.fire
  val wen = io.write.en

  when (io.write.resp.fire) {
    wfull := false.B
  }

  when(io.write.req.fire) {
    wfull := true.B
  }

  val waddr_sel = io.write.req.bits.addr
  when (wen) {
    switch (waddr_sel) {
      is (MMIO_ADDR.CHANGE_MODE.U) { mode := Mux(io.write.req.bits.data(0) === 0.U, weWeights, operational) }
      is (MMIO_ADDR.CANDIDATE_IN.U) {
        when(state === idle) {
          val last = io.write.req.bits.data(50)
          val candidateId = io.write.req.bits.data(49, 32)
          val candidateValue = io.write.req.bits.data(31, 0)
          candidates(candidateId) := candidateValue.asTypeOf(new Candidate()(p).data)

//          io.write.resp.bits.resp := true.B
//          io.write.resp.valid := true.B
          // TODO: Revisit this
          state := Mux(last === true.B, busy, idle)
        }
      }
    }

    // Do write
  }

  io.write.resp.bits.resp := wfull
  io.write.resp.valid := wfull
  io.write.req.ready := io.write.resp.ready && !wfull

}
