package psrf.modules

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.{Config, Parameters}
import chiseltest._
import chiseltest.ChiselScalatestTester
import org.scalatest.flatspec.AnyFlatSpec
import psrf.params.{BusWidth, FixedPointBinaryPoint, FixedPointWidth, RAMSize}

object Constants {
  val fpWidth = 32
  val bpWidth = 16
  val CSR_ADDR: Long = 0x00L
  val MODE_CHANGE: Long = 0x04L << 32
  val CANDIDATE_IN: Long = 0x10L << 32
  val OPERATIONAL_STATE = 1;
  val WE_WEIGHTS_STATE = 0;
}

class WishboneRandomForestSpecHelper(val dut: WishboneDecisionTreeTile) {

  def toFixedPoint(x: Double, scale: Long): Long = {
    val BP_SCALE = 1L << scale
    val xv = x * BP_SCALE
    if (xv < 0.0) {
      (xv - 0.5).toLong
    } else {
      (xv + 0.5).toLong
    }
  }

  def parseCSR(resp: BigInt): (BigInt, BigInt, BigInt) = {
    val mode = (resp & 0x04) >> 2
    val ready = (resp & 0x2) >> 1
    val valid = resp & 1
    (mode, ready, valid)
  }

  def createCandidate(value: Double, id: Long, last: Long = 0L): Long = {
    toFixedPoint(value, Constants.bpWidth) + (id << 32) +  (last << 50)
  }

  def wishboneWrite(addr: Long, data: Long): Unit = {
    dut.io.bus.addr.poke(addr)
    dut.io.bus.data_wr.poke(data)
    dut.io.bus.we.poke(true.B)
    dut.io.bus.cyc.poke(true.B)
    dut.io.bus.stb.poke(true.B)
    while (!dut.io.bus.ack.peekBoolean()) dut.clock.step()
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
    while (!dut.io.bus.ack.peekBoolean()) dut.clock.step()
    dut.io.bus.cyc.poke(false.B)
    dut.io.bus.stb.poke(false.B)
    val data_out = dut.io.bus.data_rd
    dut.clock.step()
    dut.io.bus.ack.expect(false.B)
    data_out
  }
}


class WishboneRandomForestSpec extends AnyFlatSpec with ChiselScalatestTester {
  val params = new Config((site, here, up) => {
    case RAMSize => 16384
    case FixedPointWidth => Constants.fpWidth
    case FixedPointBinaryPoint => Constants.bpWidth
    case BusWidth => 64
    case DecisionTreeConfigKey => DecisionTreeConfig(
      maxFeatures = 2,
      maxNodes = 10,
      maxClasses = 10,
      maxDepth = 10
    )
  })

  it should "be able in weWeights state when begin" in {
    test(new WishboneDecisionTreeTile()(params))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new WishboneRandomForestSpecHelper(dut)

        val resp = helper.wishboneRead(Constants.CSR_ADDR).peekInt()
        assert(helper.parseCSR(resp)._1 == Constants.WE_WEIGHTS_STATE)
      }
  }

  it should "move to operational state when change mode is triggered" in {
    test(new WishboneDecisionTreeTile()(params))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new WishboneRandomForestSpecHelper(dut)

        helper.wishboneWrite(Constants.MODE_CHANGE, Constants.OPERATIONAL_STATE)
        val resp = helper.wishboneRead(Constants.CSR_ADDR).peekInt()

        assert(helper.parseCSR(resp)._1 == Constants.OPERATIONAL_STATE)
      }
  }

  // TODO: We do not
  it should "be able to store candidates" in {
    test(new WishboneDecisionTreeTile()(params))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new WishboneRandomForestSpecHelper(dut)

        helper.wishboneWrite(Constants.MODE_CHANGE, Constants.OPERATIONAL_STATE)

        // Check if it's operational
        var resp = helper.wishboneRead(Constants.CSR_ADDR).peekInt()
        var states = helper.parseCSR(resp)
        assert(states._1 == Constants.OPERATIONAL_STATE)
        assert(states._2 == 1)

        val candidate1 = 0.5
        val candidate2 = 1.0

        // Write candidates into the accelerator
        helper.wishboneWrite(Constants.CANDIDATE_IN, helper.createCandidate(candidate1, 0L))
        helper.wishboneWrite(Constants.CANDIDATE_IN, helper.createCandidate(candidate2, 1L, last = 1L))

        // After candidate CSR should be busy
        resp = helper.wishboneRead(Constants.CSR_ADDR).peekInt()
        states = helper.parseCSR(resp)
        assert(states._1 == Constants.OPERATIONAL_STATE)
        assert(states._2 == 0)

    }
  }

  // TODO: Add test should not accept candidates when in busy state
}
