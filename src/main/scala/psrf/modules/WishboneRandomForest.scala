package psrf.modules

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import chisel3.experimental.FixedPoint
import psrf.bus.WishboneSlave
import psrf.params.{BusParams, RAMParams}

class Candidate()(implicit val p: Parameters) extends Bundle with HasVariableDecisionTreeParams {
  val data = FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)
}

object MMIO_ADDR {
  val CSR = 0x00
  val CHANGE_MODE = 0x04
  val CANDIDATE_IN = 0x10
  val DECISION = 0x14
  val WEIGHTS_IN = 0x1C
  val WEIGHTS_OUT = 0x20
}

// Note: When state is busy and candidates in is set value is ignored
/**
  *
  * MMIO Layout
  *
  * CSR
  * 0x00  = R = CSR            = {61'b0, mode, ready, valid}
  * 0x04  = W = Change Mode    = {mode}
  *
  * Data
  * 0x10  = W = Candidate In   = {last, idx, candidate}
  * 0x14  = R = Decision       = Final Decision
  * 0x1C  = W = Weights In     = {1'threshold, 9'featureClass, 32'threshold, 11'leftNode, 11'rightNode}
  * 0x20  = R = Weights Out    = {1'threshold, 9'featureClass, 32'threshold, 11'leftNode, 11'rightNode}
  *
  * Error and Exceptions
  * // TODO: Add Error Registerss
  *
  */
class WishboneRandomForest()(implicit val p: Parameters) extends Module
  with HasVariableDecisionTreeParams
  with BusParams
  with RAMParams {
  val io = IO(new WishboneSlave(busWidth))

  val ack = RegInit(false.B)
  val data_rd = RegInit(0.U(busWidth.W))

  val weWeights :: operational :: Nil = Enum(2)
  val mode = RegInit(weWeights)

  val idle :: busy :: done :: Nil = Enum(3)
  val state = RegInit(idle)

//  val decisionTree = Module(new WishboneDecisionTree()(p))

  val candidate = Reg(Vec(maxFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)))
  val decision = Reg(UInt(32.W))

  io.bus.data_rd := data_rd
  io.bus.ack := ack
  // TODO: Write the error scenario
  io.bus.err := false.B
  io.bus.stall := false.B

  val sel = io.bus.addr(40, 32)
  ack := false.B
  when (io.bus.stb && io.bus.cyc && !io.bus.ack) {
    data_rd := 0.U(busWidth.W)
    switch (sel) {
      is (MMIO_ADDR.CSR.U) { data_rd := Cat(0.U(61.W), mode, state === idle, false.B) }
      is (MMIO_ADDR.DECISION.U) { data_rd := Cat(0.U(32.W), decision) }
//      is (MMIO_ADDR.WEIGHTS_OUT.U) { data_rd :=  }
    }

    when (io.bus.we) {
      switch (sel) {
        is (MMIO_ADDR.CHANGE_MODE.U) { mode := Mux(io.bus.data_wr(0) === 0.U, weWeights, operational) }
        is (MMIO_ADDR.CANDIDATE_IN.U) {
          when (state === idle) {
            val last = io.bus.data_wr(50)
            val candidateId = io.bus.data_wr(49, 32)
            val candidateValue = io.bus.data_wr(31, 0)
            candidate(candidateId) := candidateValue.asTypeOf(new Candidate()(p).data)
            state := Mux(last === true.B, busy, idle)
          }
        }
        // TODO: Ack should be delay in DMA
//        is (0x1C) {
//
//        }
      }
    }

    ack := true.B
  }

}
