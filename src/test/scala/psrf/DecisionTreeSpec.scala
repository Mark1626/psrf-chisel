package psrf

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._
import chisel3.experimental.VecLiterals._
import chiseltest._
import chiseltest.simulator.{VerilatorBackendAnnotation, WriteVcdAnnotation}
import org.scalatest._
import chisel3.experimental.FixedPoint

class DecisionTreeSpec extends FlatSpec with ChiselScalatestTester with Matchers {
  case class DecisionTreeNodeLit(threshold: Double, featureIndex: Int, rightNode: Int, leftNode: Int)

  def DecisionTreeTest(
    p:                DecisionTreeParams,
    litTree:          Seq[DecisionTreeNodeLit],
    inCandidate:      Vec[FixedPoint],
    expectedDecision: Boolean
  ): Unit = {
    val annos = Seq(WriteVcdAnnotation)
    val tree = litTree.map(n =>
      (new DecisionTreeNode(p)).Lit(
        _.featureIndex -> n.featureIndex.U(p.featureIndexWidth.W),
        _.threshold    -> FixedPoint.fromDouble(n.threshold, p.fixedPointWidth.W, p.fixedPointBinaryPoint.BP),
        _.rightNode    -> n.rightNode.U(p.nodeAddrWidth.W),
        _.leftNode     -> n.leftNode.U(p.nodeAddrWidth.W)
      )
    )
    test(new DecisionTree(tree, p)).withAnnotations(annos) { dut =>
      dut.io.in.valid.poke(false.B)
      dut.io.in.ready.expect(true.B)
      dut.clock.step()
      dut.io.in.bits.poke(inCandidate)
      dut.io.in.valid.poke(true.B)
      dut.io.out.ready.poke(true.B)
      while (dut.io.out.valid.peek().litValue == 0) dut.clock.step()
      dut.io.out.bits.expect(expectedDecision.B)
    }
  }

  it should "got to left node and give correct decision" in {
    val p = DecisionTreeParams(numFeatures = 2, numNodes = 3, fixedPointWidth = 5, fixedPointBinaryPoint = 2)
    val litTree = Seq(
      DecisionTreeNodeLit(threshold = 1, featureIndex = 0, rightNode = 2, leftNode = 1), // Root node
      DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0), // Left node
      DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0)  // Right node
    )
    val inCandidate = Vec.Lit(
      FixedPoint.fromDouble(0.5, p.fixedPointWidth.W, p.fixedPointBinaryPoint.BP),
      FixedPoint.fromDouble(2, p.fixedPointWidth.W, p.fixedPointBinaryPoint.BP)
    )
    val expectedDecision = true
    DecisionTreeTest(p, litTree, inCandidate, expectedDecision)
  }

  it should "got to right node and give correct decision" in {
    val p = DecisionTreeParams(numFeatures = 2, numNodes = 3, fixedPointWidth = 5, fixedPointBinaryPoint = 2)
    val litTree = Seq(
      DecisionTreeNodeLit(threshold = 1, featureIndex = 0, rightNode = 2, leftNode = 1), // Root node
      DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0), // Left node
      DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0)  // Right node
    )
    val inCandidate = Vec.Lit(
      FixedPoint.fromDouble(0.5, p.fixedPointWidth.W, p.fixedPointBinaryPoint.BP),
      FixedPoint.fromDouble(2, p.fixedPointWidth.W, p.fixedPointBinaryPoint.BP)
    )
    val expectedDecision = false
    DecisionTreeTest(p, litTree, inCandidate, expectedDecision)
  }
}
