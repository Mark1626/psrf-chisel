package psrf.modules

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import psrf.params.RAMParams

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

    when (!io.operational) {
      io.up <> scratchpad.io
    } .otherwise {
      io.up <> DontCare
      scratchpad.io <> DontCare
    }
}
