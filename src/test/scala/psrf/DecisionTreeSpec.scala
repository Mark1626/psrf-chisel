package psrf

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._
import chisel3.experimental.VecLiterals._
import chiseltest._
import chiseltest.simulator.{VerilatorBackendAnnotation, WriteVcdAnnotation}
import org.scalatest._
import chisel3.experimental.FixedPoint
import chisel3.internal.firrtl.Width
import chisel3.internal.firrtl.BinaryPoint

class DecisionTreeSpec extends FlatSpec with ChiselScalatestTester with Matchers {
  case class DecisionTreeNodeLit(threshold: Double, featureIndex: Int, rightNode: Int, leftNode: Int)

  def decisionTreeNodeLitToChiselType(n: DecisionTreeNodeLit, p: DecisionTreeParams): DecisionTreeNode = {
    (new DecisionTreeNode(p)).Lit(
      _.featureIndex -> n.featureIndex.U(p.featureIndexWidth.W),
      _.threshold    -> FixedPoint.fromDouble(n.threshold, p.fixedPointWidth.W, p.fixedPointBinaryPoint.BP),
      _.rightNode    -> n.rightNode.U(p.nodeAddrWidth.W),
      _.leftNode     -> n.leftNode.U(p.nodeAddrWidth.W)
    )
  }

  implicit class fromSeqDoubleToLiteral(s: Seq[Double]) {
    def asFixedPointVecLit(width: Width, binaryPoint: BinaryPoint): Vec[FixedPoint] =
      Vec.Lit(s.map(d => d.F(width, binaryPoint)): _*)
  }

  def decisionTreeSingleTest(
    p:                DecisionTreeParams,
    inTree:           Seq[DecisionTreeNodeLit],
    inCandidate:      Seq[Double],
    expectedDecision: Boolean
  ): Unit = {
    val annos     = Seq(WriteVcdAnnotation)
    val tree      = inTree.map(n => decisionTreeNodeLitToChiselType(n, p))
    val candidate = inCandidate.asFixedPointVecLit(p.fixedPointWidth.W, p.fixedPointBinaryPoint.BP)

    test(new DecisionTree(tree, p)).withAnnotations(annos) { dut =>
      dut.io.in.valid.poke(false.B)
      dut.io.in.ready.expect(true.B)
      dut.clock.step()
      dut.io.in.bits.poke(candidate)
      dut.io.in.valid.poke(true.B)
      dut.io.out.ready.poke(true.B)
      while (dut.io.out.valid.peek().litValue == 0) dut.clock.step()
      dut.io.out.bits.expect(expectedDecision.B)
    }
  }

  def decisionTreeSeqTest(
    p:                 DecisionTreeParams,
    inTree:            Seq[DecisionTreeNodeLit],
    inCandidates:      Seq[Seq[Double]],
    expectedDecisions: Seq[Boolean]
  ): Unit = {
    val annos      = Seq(WriteVcdAnnotation)
    val tree       = inTree.map(n => decisionTreeNodeLitToChiselType(n, p))
    val candidates = inCandidates.map(x => x.asFixedPointVecLit(p.fixedPointWidth.W, p.fixedPointBinaryPoint.BP))

    test(new DecisionTree(tree, p)).withAnnotations(annos) { dut =>
      dut.io.in.initSource()
      dut.io.in.setSourceClock(dut.clock)
      dut.io.out.initSink()
      dut.io.out.setSinkClock(dut.clock)
      fork {
        dut.io.in.enqueueSeq(candidates)
      }.fork {
        dut.io.out.expectDequeueSeq(expectedDecisions.map(_.asBool))
      }.join()
    }
  }

  it should "got to left node and give correct decision" in {
    val p = DecisionTreeParams(numFeatures = 2, numNodes = 3, fixedPointWidth = 5, fixedPointBinaryPoint = 2)
    val inTree = Seq(
      DecisionTreeNodeLit(threshold = 1, featureIndex = 0, rightNode = 2, leftNode = 1), // Root node
      DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0), // Left node
      DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0)  // Right node
    )
    val inCandidate      = Seq(0.5, 2)
    val expectedDecision = true
    decisionTreeSingleTest(p, inTree, inCandidate, expectedDecision)
  }

  it should "got to right node and give correct decision" in {
    val p = DecisionTreeParams(numFeatures = 2, numNodes = 3, fixedPointWidth = 5, fixedPointBinaryPoint = 2)
    val inTree = Seq(
      DecisionTreeNodeLit(threshold = 1, featureIndex = 1, rightNode = 2, leftNode = 1), // Root node
      DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0), // Left node
      DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0)  // Right node
    )
    val inCandidate      = Seq(0.5, 2)
    val expectedDecision = false
    decisionTreeSingleTest(p, inTree, inCandidate, expectedDecision)
  }

  it should "be able to traverse a tree with five nodes and give correct decision" in {
    val p = DecisionTreeParams(numFeatures = 2, numNodes = 5, fixedPointWidth = 5, fixedPointBinaryPoint = 2)
    val inTree = Seq(
      DecisionTreeNodeLit(threshold = 1, featureIndex = 0, rightNode = 2, leftNode = 1), // Root node
      DecisionTreeNodeLit(threshold = 2, featureIndex = 1, rightNode = 4, leftNode = 3), // Left node
      DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0), // Right node
      DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0), // Left Left node
      DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0)  // Left Right node
    )
    val inCandidate      = Seq(0.5, 2.5)
    val expectedDecision = false
    decisionTreeSingleTest(p, inTree, inCandidate, expectedDecision)
  }

  it should "give correct decision for multiple candidates" in {
    val p = DecisionTreeParams(numFeatures = 2, numNodes = 3, fixedPointWidth = 5, fixedPointBinaryPoint = 2)
    val inTree = Seq(
      DecisionTreeNodeLit(threshold = 1, featureIndex = 0, rightNode = 2, leftNode = 1), // Root node
      DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0), // Left node
      DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0)  // Right node
    )

    val inCandidates      = Seq(Seq(0.5, 2), Seq(2, 0.5), Seq(1, 0.5), Seq(1.5, 1))
    val expectedDecisions = Seq(true, false, true, false)
    decisionTreeSeqTest(p, inTree, inCandidates, expectedDecisions)
  }
}
