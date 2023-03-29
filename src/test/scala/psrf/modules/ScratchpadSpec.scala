package psrf.modules

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ScratchpadSpecHelper(dut: Scratchpad) {
  def write(addr: Long, data: Long): Unit = {
    while (dut.io.write.req.ready.peek() == false.B) dut.clock.step ()
    dut.io.write.en.poke(true)
    dut.io.write.req.bits.addr.poke(addr)
    dut.io.write.req.bits.data.poke(data)
    dut.io.write.req.valid.poke(true)
    dut.io.write.resp.ready.poke(true)

    dut.clock.step()
    dut.io.write.en.poke(false)
    dut.io.write.resp.valid.expect(true)
    dut.io.write.req.valid.poke(false)
    dut.io.write.resp.ready.poke(false)

    dut.clock.step()
    //dut.io.write.resp.valid.expect(false)
  }

  def read(addr: Long): UInt = {
    // TODO: Fix this
    while (dut.io.read.req.ready.peek() == false.B) dut.clock.step ()
    dut.io.read.req.bits.addr.poke (addr)
    dut.io.read.req.valid.poke (true)
    dut.io.read.resp.ready.poke (true)

    dut.clock.step()
    dut.io.read.resp.valid.expect (true)
    dut.io.read.req.valid.poke (false)
    dut.io.read.resp.ready.poke (false)
    val data = dut.io.read.resp.bits.data

    dut.clock.step ()
    //dut.io.read.resp.valid.expect (false)
    data
  }
}

class ScratchpadSpec extends AnyFlatSpec with ChiselScalatestTester {
  it should "be able to write and read" in {
    test(new Scratchpad(1024, 64))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val ramhelper = new ScratchpadSpecHelper(dut)

        ramhelper.write(100, 100)
        ramhelper.write(101, 101)
        ramhelper.write(102, 102)
        ramhelper.write(103, 103)
        ramhelper.write(104, 104)

        val res = ramhelper.read(103)
        res.expect(103.U)

        ramhelper.read(104)
        dut.clock.step(2)
      }
  }
}
