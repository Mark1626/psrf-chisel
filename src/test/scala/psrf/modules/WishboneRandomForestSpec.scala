package psrf.modules

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.{Config, Parameters}
import chiseltest._
import chiseltest.ChiselScalatestTester
import org.scalatest.flatspec.AnyFlatSpec

class WishboneRandomForestSpecHelper(val dut: WishboneDecisionTreeTile) {
  def wishboneWrite(addr: Long, data: Long): Unit = {
    dut.io.bus.addr.poke(addr)
    dut.io.bus.data_wr.poke(data)
    dut.io.bus.we.poke(true.B)
    dut.io.bus.cyc.poke(true.B)
    dut.io.bus.stb.poke(true.B)
    while (dut.io.bus.ack.peek() != true.B) dut.clock.step()
    dut.io.bus.cyc.poke(false.B)
    dut.io.bus.stb.poke(false.B)
    dut.clock.step()
    dut.io.bus.ack.expect(false.B)
  }

  def wishboneRead(addr: Long): UInt = {
    dut.io.bus.addr.poke(addr)
    dut.io.bus.we.poke(false.B)
    dut.io.bus.cyc.poke(true.B)
    dut.io.bus.stb.poke(true.B)
    while (dut.io.bus.ack.peek() != true.B) dut.clock.step()
    dut.io.bus.cyc.poke(false.B)
    dut.io.bus.stb.poke(false.B)
    val data_out = dut.io.bus.data_rd
    dut.clock.step()
    dut.io.bus.ack.poke(false.B)
    return data_out
  }
}

class WishboneRandomForestSpec extends AnyFlatSpec with ChiselScalatestTester {

}
