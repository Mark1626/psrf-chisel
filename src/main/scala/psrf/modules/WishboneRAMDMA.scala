package psrf.modules

import chisel3._
import chipsalliance.rocketchip.config.Parameters
import psrf.bus.{WishboneMaster, WishboneSlave}
import psrf.params.BusParams

// TODO: Do I need this, or can I just make this part of the interconnnect
class WishboneRAMDMA()(implicit val p: Parameters) extends Module
  with BusParams {
  val io = IO(new Bundle {
    val up = new WishboneSlave(busWidth)
    val down = new WishboneMaster(busWidth)
  })

  io.down <> io.up
}
