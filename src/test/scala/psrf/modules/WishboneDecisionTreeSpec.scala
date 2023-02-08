package psrf.modules

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.{Config, Parameters}
import chisel3.experimental.FixedPoint
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.freespec.AnyFreeSpec
import psrf.bus.BusWidth
import psrf.params.{FixedPointBinaryPoint, FixedPointWidth}

// TODO: Should this be in src
case class TreeNodeLit(
  leaf: Int,
  featureIndex: Int,
  threshold: Long,
  leftNode: Int,
  rightNode: Int
) {

  def toBinary: BigInt = {
    def twosCompliment(x: Int): Int = { if (x < 0) ((1<<11 - 1) - x) else x }

    val left = twosCompliment(leftNode)
    val right = twosCompliment(rightNode)

    val rawbin = "%1s%9s%32s%11s%11s".format(
      leaf.toBinaryString,
      featureIndex.toBinaryString,
      threshold.toBinaryString,
      left.toBinaryString,
      right.toBinaryString
    )

    BigInt(rawbin.replace(' ', '0'), 2)
  }
}

class WishboneDecisionTreeHelper(dut: WishboneDecisionTree) {
  def handleReq(expectedAddr: Int, node: TreeNodeLit): Unit = {
    dut.io.down.bus.stb.expect(true.B)
    dut.io.down.bus.cyc.expect(true.B)
    dut.io.down.bus.addr.expect(expectedAddr)

    dut.io.down.bus.data_rd.poke(node.toBinary.U(64.W))
    dut.io.down.bus.ack.poke(true.B)
  }
}

class WishboneDecisionTreeSpec extends AnyFlatSpec with ChiselScalatestTester {
  val BP = 16

  def toFixedPoint(x: Double, scale: Long): Long = {
    val BP_SCALE = 1L << scale
    val xv = x * BP_SCALE
    if (xv < 0.0) {
      (xv - 0.5).toLong
    } else {
      (xv + 0.5).toLong
    }
  }

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
  })

  it should "return candidate when at leaf node" in {
    test(new WishboneDecisionTree(0)(p))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new WishboneDecisionTreeHelper(dut)

        val inCandidates = Seq(0.5, 2)
        val treeNode = TreeNodeLit(1, 2, toFixedPoint(3.75, BP), 0, 0)

        // TODO: This only works when size of Vec is equal to maxFeatures
        val candidate = inCandidates.asFixedPointVecLit(
          p(FixedPointWidth).W,
          p(FixedPointBinaryPoint).BP)

        dut.io.up.in.ready.expect(true.B)

        dut.io.up.in.bits.poke(candidate)
        dut.io.up.in.valid.poke(true.B)
        dut.clock.step()

        dut.io.up.in.valid.poke(false.B)
        dut.clock.step()

        helper.handleReq(0, treeNode)
        dut.io.up.out.ready.poke(true.B)

        dut.clock.step()
        dut.io.down.bus.stb.expect(false.B)
        dut.io.down.bus.cyc.expect(false.B)
        dut.io.down.bus.ack.poke(false.B)

        while (dut.io.up.out.valid.peek() == false.B) { dut.clock.step() }
        dut.io.up.out.bits.expect(2)
      }
  }

  it should "go to left node and give correct decision" in {
    test(new WishboneDecisionTree(0)(p))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new WishboneDecisionTreeHelper(dut)

        val inCandidates = Seq(0.5, 2)
        val treeNode0 = TreeNodeLit(0, 0, toFixedPoint(0.5, BP), 1, 2)
        val treeNode1 = TreeNodeLit(1, 2, toFixedPoint(0, BP), -1, -1)

        val candidate = inCandidates.asFixedPointVecLit(
          p(FixedPointWidth).W,
          p(FixedPointBinaryPoint).BP)

        dut.io.up.in.initSource()
        dut.io.up.out.ready.poke(true.B)

        dut.io.up.in.bits.poke(candidate)
        dut.io.up.in.valid.poke(true.B)
        dut.clock.step()

        dut.io.up.in.valid.poke(false.B)
        dut.clock.step()

        helper.handleReq(0, treeNode0)
        dut.clock.step()

        dut.io.down.bus.stb.expect(false.B)
        dut.io.down.bus.cyc.expect(false.B)
        dut.io.down.bus.ack.poke(false.B)
        dut.clock.step()

        helper.handleReq(1, treeNode1)
        dut.clock.step()
        dut.io.down.bus.stb.expect(false.B)
        dut.io.down.bus.cyc.expect(false.B)
        dut.io.down.bus.ack.poke(false.B)

        while (dut.io.up.out.valid.peek() == false.B) {
          dut.clock.step()
        }
        dut.io.up.out.bits.expect(2)
      }
  }

  it should "go to right node and give correct decision" in {
    test(new WishboneDecisionTree(0)(p))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new WishboneDecisionTreeHelper(dut)

        val inCandidates = Seq(1.5, 2)
        val treeNode0 = TreeNodeLit(0, 0, toFixedPoint(0.5, BP), 1, 2)
        val treeNode2 = TreeNodeLit(1, 3, toFixedPoint(0, BP), -1, -1)

        val candidate = inCandidates.asFixedPointVecLit(
          p(FixedPointWidth).W,
          p(FixedPointBinaryPoint).BP)

        dut.io.up.in.initSource()
        dut.io.up.out.ready.poke(true.B)

        dut.io.up.in.bits.poke(candidate)
        dut.io.up.in.valid.poke(true.B)
        dut.clock.step()

        dut.io.up.in.valid.poke(false.B)
        dut.clock.step()

        helper.handleReq(0, treeNode0)
        dut.clock.step()

        dut.io.down.bus.stb.expect(false.B)
        dut.io.down.bus.cyc.expect(false.B)
        dut.io.down.bus.ack.poke(false.B)
        dut.clock.step(2)

        helper.handleReq(2, treeNode2)
        dut.clock.step()
        dut.io.down.bus.stb.expect(false.B)
        dut.io.down.bus.cyc.expect(false.B)
        dut.io.down.bus.ack.poke(false.B)

        while (dut.io.up.out.valid.peek() == false.B) {
          dut.clock.step()
        }
        dut.io.up.out.bits.expect(3)
      }
  }

//  it should "work for 2 level decision tree" in {
//    test(new WishboneDecisionTree(0)(p))
//      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
//        val helper = new WishboneDecisionTreeHelper(dut)
//
//      }
//  }

  // TODO: Add test for assertions
}
