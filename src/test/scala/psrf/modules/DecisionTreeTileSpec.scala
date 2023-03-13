package psrf.modules

import chipsalliance.rocketchip.config.{Config, Parameters}
import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import psrf.params.{BusWidth, FixedPointBinaryPoint, FixedPointWidth, RAMSize}

class DecisionTreeTileSpecHelper(val dut: DecisionTreeTile) {

  def createCandidate(value: Double, id: Long, last: Long = 0L): Long = {
    Helper.toFixedPoint(value, Constants.bpWidth) + (id << 32) + (last << 50)
  }

  def wishboneWrite(addr: Long, data: BigInt): Unit = {
    dut.io.up.bus.addr.poke(addr)
    dut.io.up.bus.data_wr.poke(data)
    dut.io.up.bus.we.poke(true.B)
    dut.io.up.bus.cyc.poke(true.B)
    dut.io.up.bus.stb.poke(true.B)
    while (!dut.io.up.bus.ack.peekBoolean()) dut.clock.step()
    dut.io.up.bus.cyc.poke(false.B)
    dut.io.up.bus.stb.poke(false.B)
    dut.clock.step()
    dut.io.up.bus.ack.expect(false.B)
  }

  def wishboneWrite(addr: Long, node: TreeNodeLit): Unit = {
    wishboneWrite(addr, node.toBinary)
  }

  def wishboneRead(addr: Long): UInt = {
    dut.io.up.bus.addr.poke(addr)
    dut.io.up.bus.we.poke(false.B)
    dut.io.up.bus.cyc.poke(true.B)
    dut.io.up.bus.stb.poke(true.B)
    while (!dut.io.up.bus.ack.peekBoolean()) dut.clock.step()
    dut.io.up.bus.cyc.poke(false.B)
    dut.io.up.bus.stb.poke(false.B)
    val data_out = dut.io.up.bus.data_rd
    dut.clock.step()
    dut.io.up.bus.ack.expect(false.B)
    data_out
  }
}

class DecisionTreeTileSpec extends AnyFlatSpec with ChiselScalatestTester {

  val p: Parameters = new Config((site, here, up) => {
    case FixedPointWidth => 32
    case FixedPointBinaryPoint => 16
    case BusWidth => 64
    case DecisionTreeConfigKey => DecisionTreeConfig(
      maxFeatures = 2,
      maxNodes = 10,
      maxClasses = 10,
      maxDepth = 10
    )
    case RAMSize => 1024
  })

  it should "function as a passthrough to storage when tile is not in operational mode" in {
    test(new DecisionTreeTile()(p))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new DecisionTreeTileSpecHelper(dut)
        
        dut.io.operational.poke(false.B)

        helper.wishboneWrite(100, 101)
        helper.wishboneWrite(200, 201)
        helper.wishboneWrite(300, 301)

        helper.wishboneRead(100).expect(101)
        helper.wishboneRead(300).expect(301)
    }
  }

  it should "behave as a decision tree when in operational mode" in {
    test(new DecisionTreeTile()(p))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        val inCandidates = Seq(0.5, 2)
        val treeNode0 = TreeNodeLit(0, 0, Helper.toFixedPoint(0.5, Constants.bpWidth), 1, 2)
        val treeNode1 = TreeNodeLit(1, 2, Helper.toFixedPoint(0, Constants.bpWidth), -1, -1)

        val helper = new DecisionTreeTileSpecHelper(dut)

        dut.io.operational.poke(false.B)
        helper.wishboneWrite(0, treeNode0)
        helper.wishboneWrite(1, treeNode1)

        dut.io.operational.poke(true.B)
        dut.io.tree.out.ready.poke(true.B)
        val candidate = inCandidates.asFixedPointVecLit(
          p(FixedPointWidth).W,
          p(FixedPointBinaryPoint).BP)

        dut.io.tree.in.ready.expect(true.B)

        dut.io.tree.in.bits.offset.poke(0.U)
        dut.io.tree.in.bits.candidates.poke(candidate)
        dut.io.tree.in.valid.poke(true.B)

        dut.clock.step()
        dut.io.tree.in.valid.poke(false.B)

        while (!dut.io.tree.out.valid.peekBoolean()) { dut.clock.step() }
        dut.io.tree.out.bits.expect(2)
    }
  }
}
