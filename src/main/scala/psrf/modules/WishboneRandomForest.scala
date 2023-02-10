package psrf.modules

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import chisel3.experimental.FixedPoint
import psrf.bus.WishboneSlave
import psrf.params.{BusParams, RAMParams}

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
  * 0x20 =
  *
  * Error and Exceptions
  * // TODO: Add Error Registerss
  *
  */
class WishboneDecisionTreeTile()(implicit val p: Parameters) extends Module
  with HasVariableDecisionTreeParams
  with BusParams
  with RAMParams {
  val io = IO(new WishboneSlave(busWidth))

  val ack = RegInit(false.B)
  val data_rd = RegInit(UInt(busWidth.W))

  val weWeights :: operational :: Nil = Enum(2)
  val mode = RegInit(operational)

  val idle :: busy :: done :: Nil = Enum(3)
  val state = RegInit(idle)

  //val decisionTree = new WishboneDecisionTree()(p)

  val candidate = Reg(Vec(maxFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)))
  val decision = Reg(UInt(32.W))

  io.bus.data_rd := data_rd
  io.bus.ack := ack
  // TODO: Write the error scenario
  io.bus.err := false.B

  val sel = io.bus.addr(35, 32)
  when (io.bus.stb && io.bus.cyc && !io.bus.ack) {
    data_rd := 0.U(busWidth.W)
    switch (sel) {
      is (0x0.U) { data_rd := Cat(0.U(61.W), mode, false.B, false.B) }
      is (0x14.U) { data_rd := Cat(0.U(32.W), decision) }
    }

    when (io.bus.we) {
      switch (sel) {
        is (0x04.U) { mode := Mux(io.bus.data_wr(32) === 0.U, weWeights, operational) }
        is (0x10.U) {
          // TODO: Should I restrict this
          val candidateId = io.bus.data_wr(63, 32)
          val candidateValue = io.bus.data_wr(31, 0)
          candidate(candidateId) := candidateValue
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
