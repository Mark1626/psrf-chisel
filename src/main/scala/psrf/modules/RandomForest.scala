package psrf.modules

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import chisel3.experimental.FixedPoint
import psrf.bus.WishboneSlave
import psrf.params.{BusParams, RAMParams}

//object MMIO_ADDR {
//  val CSR = 0x00
//  val CHANGE_MODE = 0x04
//  val CANDIDATE_IN = 0x10
//  val DECISION = 0x14
//  val WEIGHTS_IN = 0x1C
//  val WEIGHTS_OUT = 0x20
//}

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

class RandomForest()(implicit val p: Parameters) extends Module
  with BusParams
  with HasVariableDecisionTreeParams {
  val io = IO(new Bundle {
    val addr = Input(UInt(busWidth.W))
    val data_wr = Input(UInt(busWidth.W))
    val data_rd = Output(UInt(busWidth.W))
    val req_ready = Output(Bool())
    val req_valid = Input(Bool())
    val res_ready = Input(Bool())
    val res_valid = Output(Bool())
  })
}
