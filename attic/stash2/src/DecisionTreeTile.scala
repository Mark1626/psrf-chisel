package psrf.modules

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import psrf.params.RAMParams

class DecisionTreeTile()(implicit val p: Parameters) extends Module
  with RAMParams {
  val dataSize = dataWidth
  val addrSize = ramSize

  val io = IO(new Bundle {
    val up = new ReadWriteIO(addrSize, dataSize)
    val tree = new TreeIO()(p)
    val operational = Input(Bool())
  })

  val scratchpad = Module(new Scratchpad(addrSize, dataSize))
  val decisionTree = Module(new DecisionTree()(p))

    when (io.operational) {
      io.tree <> decisionTree.io.up
      scratchpad.io.read <> decisionTree.io.down
      scratchpad.io.write <> DontCare

      io.up <> DontCare
    } .otherwise {
      io.tree <> DontCare
      decisionTree.io <> DontCare

      io.up <> scratchpad.io
    }
}
