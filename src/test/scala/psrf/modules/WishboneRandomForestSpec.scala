package psrf.modules

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.{Config, Parameters}
import chiseltest._
import chiseltest.ChiselScalatestTester
import org.scalatest.flatspec.AnyFlatSpec
import psrf.params.{BusWidth, DataWidth, FixedPointBinaryPoint, FixedPointWidth, RAMSize}

class WishboneRandomForestSpecHelper(val dut: WishboneRandomForest) {

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

  def wishboneWrite(addr: Long, data: BigInt): Unit = {
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

  def wishboneWrite(addr: Long, node: TreeNodeLit): Unit = {
    wishboneWrite(addr, node.toBinary)
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
    case DataWidth => 64
    case DecisionTreeConfigKey => DecisionTreeConfig(
      maxFeatures = 2,
      maxNodes = 10,
      maxClasses = 10,
      maxDepth = 10
    )
  })

  it should "be able in weWeights state when begin" in {
    test(new WishboneRandomForest()(params))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new WishboneRandomForestSpecHelper(dut)

        val resp = helper.wishboneRead(Constants.CSR_ADDR).peekInt()
        assert(helper.parseCSR(resp)._1 == Constants.WE_WEIGHTS_STATE)
      }
  }

  it should "move to operational state when change mode is triggered" in {
    test(new WishboneRandomForest()(params))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new WishboneRandomForestSpecHelper(dut)

        helper.wishboneWrite(Constants.MODE_CHANGE, Constants.OPERATIONAL_STATE)
        val resp = helper.wishboneRead(Constants.CSR_ADDR).peekInt()

        assert(helper.parseCSR(resp)._1 == Constants.OPERATIONAL_STATE)
      }
  }

  it should "be able to store candidates" in {
    test(new WishboneRandomForest()(params))
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

  // TODO: Revisit if we need to send err from the wishbone bus when unknown bus is accessed
//  it should "return wishbone err when unknown address is accessed" in {
//
//  }

  it should "be able to write/read weights into the RAM" in {
    test(new WishboneRandomForest()(params))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new WishboneRandomForestSpecHelper(dut)

        val weightAddr = 10

        helper.wishboneWrite(Constants.WEIGHTS_IN + weightAddr, 10)

        val res = helper.wishboneRead(Constants.WEIGHTS_OUT + weightAddr)
        res.expect(10)
      }
  }

  it should "be able to store weights and run decision tree" in {
    test(new WishboneRandomForest()(params))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new WishboneRandomForestSpecHelper(dut)

        val inCandidates = Seq(0.5, 2)
        val treeNode0 = TreeNodeLit(0, 0, Helper.toFixedPoint(0.5, Constants.bpWidth), 1, 2)
        val treeNode1 = TreeNodeLit(1, 2, Helper.toFixedPoint(0, Constants.bpWidth), -1, -1)

        helper.wishboneWrite(Constants.WEIGHTS_IN, treeNode0)
        helper.wishboneWrite(Constants.WEIGHTS_IN + 1, treeNode1)

        helper.wishboneWrite(Constants.MODE_CHANGE, Constants.OPERATIONAL_STATE)

        // Check if it's operational
        var resp = helper.wishboneRead(Constants.CSR_ADDR).peekInt()
        var states = helper.parseCSR(resp)
        assert(states._1 == Constants.OPERATIONAL_STATE)
        assert(states._2 == 1)

        // Write candidates into the accelerator
        helper.wishboneWrite(Constants.CANDIDATE_IN, helper.createCandidate(inCandidates(0), 0L))
        helper.wishboneWrite(Constants.CANDIDATE_IN, helper.createCandidate(inCandidates(1), 1L, last = 1L))

        // After candidate CSR should be busy
        resp = helper.wishboneRead(Constants.CSR_ADDR).peekInt()
        states = helper.parseCSR(resp)
        assert(states._1 == Constants.OPERATIONAL_STATE)
        assert(states._2 == 0)


        dut.clock.step(10)

        resp = helper.wishboneRead(Constants.CSR_ADDR).peekInt()
        states = helper.parseCSR(resp)
        assert(states._1 == Constants.OPERATIONAL_STATE)
        assert(states._2 == 1)
        assert(states._3 == 1)

        val res = helper.wishboneRead(Constants.DECISION_ADDR)
        res.expect(2)

      }
  }
}
