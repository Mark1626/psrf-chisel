package psrf.test

import chisel3._
import chisel3.experimental.VecLiterals._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import psrf.DecisionTreeArrayParams
import psrf.DecisionTreeArraySimple

class DecisionTreeArraySimpleSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  def decisionTreeArraySimpleSingleTest(
    p:                   DecisionTreeArrayParams,
    treesLit:            Seq[Seq[DecisionTreeNodeLit]],
    inCandidate:         Seq[Double],
    expectedDecisionLit: Seq[Boolean]
  ): Unit = {
    val annos = Seq(WriteVcdAnnotation)

    val trees =
      treesLit.zip(p.decisionTreeParams).map { case (tree, param) =>
        tree.map(node => decisionTreeNodeLitToChiselType(node, param))
      }
    val candidate        = inCandidate.asFixedPointVecLit(p.fixedPointWidth.W, p.fixedPointBinaryPoint.BP)
    val expectedDecision = Vec.Lit(expectedDecisionLit.map(_.B): _*)

    test(new DecisionTreeArraySimple(p, trees)).withAnnotations(annos) { dut =>
      dut.io.in.valid.poke(false.B)
      dut.io.in.ready.expect(true.B)
      dut.clock.step()
      dut.io.in.bits.poke(candidate)
      dut.io.in.valid.poke(true.B)
      dut.io.out.ready.poke(true.B)
      while (dut.io.out.valid.peek().litValue == 0) dut.clock.step()
      dut.io.out.bits.expect(expectedDecision)
    }
  }

  it should "give correct decisions for two trees of same depth" in {
    val p = DecisionTreeArrayParams(
      numTrees = 2,
      numNodes = Seq(3, 3),
      numFeatures = 2,
      fixedPointWidth = 5,
      fixedPointBinaryPoint = 2
    )

    val trees = Seq(
      Seq(
        DecisionTreeNodeLit(threshold = 1, featureIndex = 0, rightNode = 2, leftNode = 1), // Root node
        DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0), // Left node
        DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0)  // Right node
      ),
      Seq(
        DecisionTreeNodeLit(threshold = 1, featureIndex = 1, rightNode = 2, leftNode = 1), // Root node
        DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0), // Left node
        DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0)  // Right node
      )
    )

    val inCandidate      = Seq(0.5, 2)
    val expectedDecision = Seq(true, false)
    decisionTreeArraySimpleSingleTest(p, trees, inCandidate, expectedDecision)
  }

  it should "give correct decisions for two trees of depths two and three" in {
    val p = DecisionTreeArrayParams(
      numTrees = 2,
      numNodes = Seq(3, 5),
      numFeatures = 2,
      fixedPointWidth = 5,
      fixedPointBinaryPoint = 2
    )

    val trees = Seq(
      Seq(
        DecisionTreeNodeLit(threshold = 1, featureIndex = 0, rightNode = 2, leftNode = 1), // Root node
        DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0), // Left node
        DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0)  // Right node
      ),
      Seq(
        DecisionTreeNodeLit(threshold = 1, featureIndex = 0, rightNode = 2, leftNode = 1), // Root node
        DecisionTreeNodeLit(threshold = 2, featureIndex = 1, rightNode = 4, leftNode = 3), // Left node
        DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0), // Right node
        DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0), // Left Left node
        DecisionTreeNodeLit(threshold = 2, featureIndex = 2, rightNode = 0, leftNode = 0)  // Left Right node
      )
    )

    val inCandidate      = Seq(0.5, 2.5)
    val expectedDecision = Seq(true, false)
    decisionTreeArraySimpleSingleTest(p, trees, inCandidate, expectedDecision)
  }
}
