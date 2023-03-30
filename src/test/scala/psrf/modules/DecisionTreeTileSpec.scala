package psrf.modules

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chipsalliance.rocketchip.config.{Config, Parameters}
import psrf.params.{DataWidth, DecisionTreeConfig, DecisionTreeConfigKey, FixedPointBinaryPoint, FixedPointWidth, RAMSize}

class AXIDecisionTreeTileSpecHelper(val dut: DecisionTreeTile) {
  def createCandidate(value: Double, id: Long, last: Long = 0L): Long = {
    Helper.toFixedPoint(value, Constants.bpWidth) + (id << 32) + (last << 50)
  }

  def write(addr: Long, data: BigInt): Unit = {
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

  def write(addr: Long, node: TreeNodeLit): Unit = {
    write(addr, node.toBinary)
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

class DecisionTreeTileSpec extends AnyFlatSpec with ChiselScalatestTester {
  val p: Parameters = new Config((site, here, up) => {
    case FixedPointWidth => 32
    case FixedPointBinaryPoint => 16
    case DecisionTreeConfigKey => DecisionTreeConfig(
      maxFeatures = 2,
      maxNodes = 10,
      maxClasses = 10,
      maxDepth = 10
    )
    case RAMSize => 1024
    case DataWidth => 64
  })

  it should "function as a passthrough to storage when tile is not in operational mode" in {
    test(new DecisionTreeTile()(p))
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

  it should "behave as a decision tree when in operational mode" in {
    test(new DecisionTreeTile()(p))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        val inCandidates = Seq(0.5, 2)
        val treeNode0 = TreeNodeLit(0, 0, Helper.toFixedPoint(0.5, Constants.bpWidth), 1, 2)
        val treeNode1 = TreeNodeLit(1, 2, Helper.toFixedPoint(0, Constants.bpWidth), -1, -1)

        val helper = new AXIDecisionTreeTileSpecHelper(dut)

        dut.io.operational.poke(false.B)
        helper.write(0, treeNode0)
        helper.write(1, treeNode1)

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

        while (!dut.io.tree.out.valid.peekBoolean()) {
          dut.clock.step()
        }
        dut.io.tree.out.bits.expect(2)
      }
  }

}
