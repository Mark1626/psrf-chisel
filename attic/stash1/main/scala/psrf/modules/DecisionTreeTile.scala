package psrf.modules

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.util._
import psrf.bus.{WishboneSlave}
import psrf.params.{BusParams, RAMParams}

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
