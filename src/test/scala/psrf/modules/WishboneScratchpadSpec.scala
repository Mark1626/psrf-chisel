package psrf.modules

import chisel3._
import chipsalliance.rocketchip.config.{Config, Parameters}
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import psrf.params.{BusWidth, RAMSize}

class WishboneRAMHelper(dut: WishboneScratchpad) {
  def wishboneWrite(addr: Long, data: Long): Unit = {
    dut.io.bus.addr.poke(addr)
    dut.io.bus.data_wr.poke(data)
    dut.io.bus.we.poke(true)
    dut.io.bus.cyc.poke(true)
    dut.io.bus.stb.poke(true)
    dut.clock.step()

    dut.io.bus.stb.poke(false)
    while (!dut.io.bus.ack.peekBoolean()) dut.clock.step()

    dut.io.bus.cyc.poke(false)
    dut.clock.step()

    dut.io.bus.ack.expect(false.B)
  }

  def wishboneRead(addr: Long): UInt = {
    dut.io.bus.addr.poke(addr)
    dut.io.bus.we.poke(false)
    dut.io.bus.cyc.poke(true)
    dut.io.bus.stb.poke(true)
    dut.clock.step()

    dut.io.bus.stb.poke(false)
    while (!dut.io.bus.ack.peekBoolean()) dut.clock.step()

    dut.io.bus.cyc.poke(false)
    dut.io.bus.ack.expect(true.B)
    val data_out = dut.io.bus.data_rd
    dut.clock.step()

    dut.io.bus.ack.expect(false.B)

    data_out
  }
}

class WishboneScratchpadSpec extends AnyFlatSpec with ChiselScalatestTester {
  val p: Parameters = new Config((site, here, up) => {
    case RAMSize => 1024
    case BusWidth => 64
  })

  it should "be able to write and read" in {
    test(new WishboneScratchpad()(p))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        dut.clock.step()

        val ramhelper = new WishboneRAMHelper(dut)
        ramhelper.wishboneWrite(100, 100)
        ramhelper.wishboneWrite(101, 101)
        ramhelper.wishboneWrite(102, 102)
        ramhelper.wishboneWrite(103, 103)
        ramhelper.wishboneWrite(104, 104)

        val res = ramhelper.wishboneRead(103)
        res.expect(103.U)

        ramhelper.wishboneRead(104)
        dut.clock.step(2)

      }
  }
}

