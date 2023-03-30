package psrf.modules

import chisel3._
import chipsalliance.rocketchip.config.{Config, Parameters}
import chiseltest.{ChiselScalatestTester, _}
import org.scalatest.flatspec.AnyFlatSpec
import psrf.params.{DataWidth, DecisionTreeConfig, DecisionTreeConfigKey, FixedPointBinaryPoint, FixedPointWidth, RAMSize}

class AXIDecisionTreeHelper(dut: DecisionTree) {
  def handleReq(expectedAddr: Int, node: TreeNodeLit): Unit = {
    dut.io.down.req.valid.expect(true.B)
    dut.io.down.req.bits.addr.expect(expectedAddr)
    dut.io.down.resp.ready.expect(true.B)

    dut.clock.step()

    dut.io.down.resp.bits.data.poke(node.toBinary.U(64.W))
    dut.io.down.resp.valid.poke(true.B)
  }
}

class DecisionTreeSpec extends AnyFlatSpec with ChiselScalatestTester  {
  val p: Parameters = new Config((site, here, up) => {
    case FixedPointWidth => 32
    case FixedPointBinaryPoint => 16
    case DataWidth => 64
    case RAMSize => 1024
    case DecisionTreeConfigKey => DecisionTreeConfig(
      maxFeatures = 2,
      maxNodes = 10,
      maxClasses = 10,
      maxDepth = 10
    )
  })

  it should "return candidate when at leaf node" in {
    test(new DecisionTree()(p))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new AXIDecisionTreeHelper(dut)

        val inCandidates = Seq(0.5, 2)
        val treeNode = TreeNodeLit(1, 2, Helper.toFixedPoint(3.75, Constants.bpWidth), 0, 0)

        // TODO: This only works when size of Vec is equal to maxFeatures
        val candidate = inCandidates.asFixedPointVecLit(
          p(FixedPointWidth).W,
          p(FixedPointBinaryPoint).BP)

        dut.io.up.in.ready.expect(true.B)

        dut.io.down.req.ready.poke(true.B)

        dut.io.up.in.bits.offset.poke(0.U)
        dut.io.up.in.bits.candidates.poke(candidate)
        dut.io.up.in.valid.poke(true.B)
        dut.clock.step()

        dut.io.up.in.valid.poke(false.B)

        helper.handleReq(0, treeNode)
        dut.io.up.out.ready.poke(true.B)

        while (!dut.io.up.out.valid.peekBoolean()) {
          dut.clock.step()
        }
        dut.io.up.out.bits.expect(2)
      }
  }

  it should "go to left node and give correct decision" in {
    test(new DecisionTree()(p))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new AXIDecisionTreeHelper(dut)

        val inCandidates = Seq(0.5, 2)
        val treeNode0 = TreeNodeLit(0, 0, Helper.toFixedPoint(0.5, Constants.bpWidth), 1, 2)
        val treeNode1 = TreeNodeLit(1, 2, Helper.toFixedPoint(0, Constants.bpWidth), -1, -1)

        val candidate = inCandidates.asFixedPointVecLit(
          p(FixedPointWidth).W,
          p(FixedPointBinaryPoint).BP)

        dut.io.up.in.initSource()
        dut.io.up.out.ready.poke(true.B)
        dut.io.down.req.ready.poke(true.B)

        dut.io.up.in.bits.offset.poke(0.U)
        dut.io.up.in.bits.candidates.poke(candidate)
        dut.io.up.in.valid.poke(true.B)
        dut.clock.step()

        dut.io.up.in.valid.poke(false.B)

        helper.handleReq(0, treeNode0)
        dut.clock.step()

        helper.handleReq(1, treeNode1)

        while (!dut.io.up.out.valid.peekBoolean()) {
          dut.clock.step()
        }
        dut.io.up.out.bits.expect(2)
      }
  }

  it should "go to right node and give correct decision" in {
    test(new DecisionTree()(p))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new AXIDecisionTreeHelper(dut)

        val inCandidates = Seq(1.5, 2)
        val treeNode0 = TreeNodeLit(0, 0, Helper.toFixedPoint(0.5, Constants.bpWidth), 1, 2)
        val treeNode2 = TreeNodeLit(1, 3, Helper.toFixedPoint(0, Constants.bpWidth), -1, -1)

        val candidate = inCandidates.asFixedPointVecLit(
          p(FixedPointWidth).W,
          p(FixedPointBinaryPoint).BP)

        dut.io.up.in.initSource()
        dut.io.up.out.ready.poke(true.B)

        dut.io.down.req.ready.poke(true.B)

        dut.io.up.in.bits.offset.poke(0.U)
        dut.io.up.in.bits.candidates.poke(candidate)
        dut.io.up.in.valid.poke(true.B)
        dut.clock.step()

        dut.io.up.in.valid.poke(false.B)

        helper.handleReq(0, treeNode0)
        dut.clock.step()

        helper.handleReq(2, treeNode2)

        while (!dut.io.up.out.valid.peekBoolean()) {
          dut.clock.step()
        }
        dut.io.up.out.bits.expect(3)
      }
  }
}
