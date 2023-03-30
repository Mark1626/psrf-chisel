package psrf.modules

import chisel3._
import chiseltest._
import chisel3.util._
import chipsalliance.rocketchip.config.{Config, Parameters}
import chiseltest.ChiselScalatestTester
import org.scalatest.flatspec.AnyFlatSpec
import psrf.params.{DataWidth, DecisionTreeConfig, DecisionTreeConfigKey, FixedPointBinaryPoint, FixedPointWidth, RAMSize}


class RandomForestTileSpecHelper(val dut: RandomForestTile) {

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
    toFixedPoint(value, Constants.bpWidth) + (id << 32) + (last << 50)
  }

  def write(addr: Long, data: BigInt): Unit = {
    while (dut.io.write.req.ready.peek() == false.B) dut.clock.step()
    dut.io.write.en.poke(true)
    dut.io.write.req.bits.addr.poke(addr)
    dut.io.write.req.bits.data.poke(data)
    dut.io.write.req.valid.poke(true)
    dut.io.write.resp.ready.poke(true)

    dut.clock.step()
    dut.io.write.en.poke(false)
    dut.io.write.resp.valid.expect(true)
    dut.io.write.req.valid.poke(false)
    //dut.io.write.resp.ready.poke(false)

    dut.clock.step()
    //dut.io.write.resp.valid.expect(false)
  }

  def write(addr: Long, node: TreeNodeLit): Unit = {
    write(addr, node.toBinary)
  }

  def read(addr: Long): UInt = {
    // TODO: Fix this
    while (dut.io.read.req.ready.peek() == false.B) dut.clock.step()
    dut.io.read.req.bits.addr.poke(addr)
    dut.io.read.req.valid.poke(true)
    dut.io.read.resp.ready.poke(true)

    dut.clock.step()
    dut.io.read.resp.valid.expect(true)
    dut.io.read.req.valid.poke(false)
    //dut.io.read.resp.ready.poke(false)
    val data = dut.io.read.resp.bits.data

    dut.clock.step()
    //dut.io.read.resp.valid.expect (false)
    data
  }
}

class RandomForestTileSpec extends AnyFlatSpec with ChiselScalatestTester {
  val params = new Config((site, here, up) => {
    case RAMSize => 16384
    case FixedPointWidth => Constants.fpWidth
    case FixedPointBinaryPoint => Constants.bpWidth
    case DataWidth => 64
    case DecisionTreeConfigKey => DecisionTreeConfig(
      maxFeatures = 2,
      maxNodes = 10,
      maxClasses = 10,
      maxDepth = 10
    )
  })

  it should "be able in weWeights state when begin" in {
    test(new RandomForestTile()(params))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new RandomForestTileSpecHelper(dut)

        val resp = helper.read(Constants.CSR_ADDR).peekInt()
        assert(helper.parseCSR(resp)._1 == Constants.WE_WEIGHTS_STATE)
      }
  }

  it should "move to operational state when change mode is triggered" in {
    test(new RandomForestTile()(params))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new RandomForestTileSpecHelper(dut)

        helper.write(Constants.MODE_CHANGE, Constants.OPERATIONAL_STATE)
        var resp = helper.read(Constants.CSR_ADDR).peekInt()

        assert(helper.parseCSR(resp)._1 == Constants.OPERATIONAL_STATE)

//        helper.write(Constants.MODE_CHANGE, Constants.OPERATIONAL_STATE)
        resp = helper.read(Constants.CSR_ADDR).peekInt()

        assert(helper.parseCSR(resp)._1 == Constants.OPERATIONAL_STATE)
      }
  }

  it should "be able to store candidates" in {
    test(new RandomForestTile()(params))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new RandomForestTileSpecHelper(dut)

        helper.write(Constants.MODE_CHANGE, Constants.OPERATIONAL_STATE)

        // Check if it's operational
        var resp = helper.read(Constants.CSR_ADDR).peekInt()
        var states = helper.parseCSR(resp)
        assert(states._1 == Constants.OPERATIONAL_STATE)
        assert(states._2 == 1)

        val candidate1 = 0.5
        val candidate2 = 1.0

        // Write candidates into the accelerator
        helper.write(Constants.CANDIDATE_IN, helper.createCandidate(candidate1, 0L))
        helper.write(Constants.CANDIDATE_IN, helper.createCandidate(candidate2, 1L, last = 1L))

        // After candidate CSR should be busy
        resp = helper.read(Constants.CSR_ADDR).peekInt()
        states = helper.parseCSR(resp)
        assert(states._1 == Constants.OPERATIONAL_STATE)
        assert(states._2 == 0)

      }
  }
}
