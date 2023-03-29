package psrf.modules

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chipsalliance.rocketchip.config.{Config, Parameters}
import psrf.params.RAMSize

class AXIDecisionTreeTileSpecHelper(val dut: AXIDecisionTreeTile) {
  def createCandidate(value: Double, id: Long, last: Long = 0L): Long = {
    Helper.toFixedPoint(value, Constants.bpWidth) + (id << 32) + (last << 50)
  }

  def write(addr: Long, data: Long): Unit = {
    while (dut.io.up.write.req.ready.peek() == false.B) dut.clock.step()
    dut.io.up.write.en.poke(true)
    dut.io.up.write.req.bits.addr.poke(addr)
    dut.io.up.write.req.bits.data.poke(data)
    dut.io.up.write.req.valid.poke(true)
    dut.io.up.write.resp.ready.poke(true)

    dut.clock.step()
    dut.io.up.write.en.poke(false)
    dut.io.up.write.resp.valid.expect(true)
    dut.io.up.write.req.valid.poke(false)
    dut.io.up.write.resp.ready.poke(false)

    dut.clock.step()
    //dut.io.up.write.resp.valid.expect(false)
  }

  def read(addr: Long): UInt = {
    // TODO: Fix this
    while (dut.io.up.read.req.ready.peek() == false.B) dut.clock.step()
    dut.io.up.read.req.bits.addr.poke(addr)
    dut.io.up.read.req.valid.poke(true)
    dut.io.up.read.resp.ready.poke(true)

    dut.clock.step()
    dut.io.up.read.resp.valid.expect(true)
    dut.io.up.read.req.valid.poke(false)
    dut.io.up.read.resp.ready.poke(false)
    val data = dut.io.up.read.resp.bits.data

    dut.clock.step()
    //dut.io.up.read.resp.valid.expect (false)
    data
  }
}

class AXIDecisionTreeTileSpec extends AnyFlatSpec with ChiselScalatestTester {
  val p: Parameters = new Config((site, here, up) => {
    case RAMSize => 1024
  })

  it should "function as a passthrough to storage when tile is not in operational mode" in {
    test(new AXIDecisionTreeTile()(p))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new AXIDecisionTreeTileSpecHelper(dut)

        dut.io.operational.poke(false.B)

        helper.write(100, 101)
        helper.write(200, 201)
        helper.write(300, 301)

        helper.read(100).expect(101)
        helper.read(300).expect(301)
      }
  }

}
