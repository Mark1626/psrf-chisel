package psrf.test

import chisel3._
import chisel3.experimental.VecLiterals._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import psrf.DecisionTreeArrayParams
import psrf.DecisionTreeArraySimple
import psrf.DecisionTreeNodeLit
import psrf.decisionTreeNodeLitToChiselType

class DecisionTreeArraySimpleSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  def decisionTreeArraySimpleSingleTest(
    p:                   DecisionTreeArrayParams,
    inCandidate:         Seq[Double],
    expectedDecisionLit: Seq[Int]
  ): Unit = {
    val annos = Seq(WriteVcdAnnotation)

    val candidate        = inCandidate.asFixedPointVecLit(p.fixedPointWidth.W, p.fixedPointBinaryPoint.BP)
    val expectedDecision = Vec.Lit(expectedDecisionLit.map(_.U): _*)

    test(new DecisionTreeArraySimple(p)).withAnnotations(annos) { dut =>
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
    val trees = Seq(
      Seq(
        DecisionTreeNodeLit(
          isLeafNode = false,
          featureClassIndex = 0,
          threshold = 1,
          rightNode = 2,
          leftNode = 1
        ), // Root node
        DecisionTreeNodeLit(
          isLeafNode = true,
          featureClassIndex = 1,
          threshold = 2,
          rightNode = 0,
          leftNode = 0
        ), // Left node
        DecisionTreeNodeLit(
          isLeafNode = true,
          featureClassIndex = 0,
          threshold = 2,
          rightNode = 0,
          leftNode = 0
        ) // Right node
      ),
      Seq(
        DecisionTreeNodeLit(
          isLeafNode = false,
          featureClassIndex = 1,
          threshold = 1,
          rightNode = 2,
          leftNode = 1
        ), // Root node
        DecisionTreeNodeLit(
          isLeafNode = true,
          featureClassIndex = 1,
          threshold = 2,
          rightNode = 0,
          leftNode = 0
        ), // Left node
        DecisionTreeNodeLit(
          isLeafNode = true,
          featureClassIndex = 0,
          threshold = 2,
          rightNode = 0,
          leftNode = 0
        ) // Right node
      )
    )

    val p = DecisionTreeArrayParams(
      numTrees = 2,
      numNodes = Seq(3, 3),
      numClasses = 2,
      numFeatures = 2,
      fixedPointWidth = 5,
      fixedPointBinaryPoint = 2,
      treesLit = trees
    )

    val inCandidate      = Seq(0.5, 2)
    val expectedDecision = Seq(1, 0)
    decisionTreeArraySimpleSingleTest(p, inCandidate, expectedDecision)
  }

  it should "give correct decisions for two trees of depths two and three" in {

    val trees = Seq(
      Seq(
        DecisionTreeNodeLit(
          isLeafNode = false,
          featureClassIndex = 0,
          threshold = 1,
          rightNode = 2,
          leftNode = 1
        ), // Root node
        DecisionTreeNodeLit(
          isLeafNode = true,
          featureClassIndex = 1,
          threshold = 2,
          rightNode = 0,
          leftNode = 0
        ), // Left node
        DecisionTreeNodeLit(
          isLeafNode = true,
          featureClassIndex = 0,
          threshold = 2,
          rightNode = 0,
          leftNode = 0
        ) // Right node
      ),
      Seq(
        DecisionTreeNodeLit(
          isLeafNode = false,
          featureClassIndex = 0,
          threshold = 1,
          rightNode = 2,
          leftNode = 1
        ), // Root node
        DecisionTreeNodeLit(
          isLeafNode = false,
          featureClassIndex = 1,
          threshold = 2,
          rightNode = 4,
          leftNode = 3
        ), // Left node
        DecisionTreeNodeLit(
          isLeafNode = true,
          featureClassIndex = 0,
          threshold = 2,
          rightNode = 0,
          leftNode = 0
        ), // Right node
        DecisionTreeNodeLit(
          isLeafNode = true,
          featureClassIndex = 1,
          threshold = 2,
          rightNode = 0,
          leftNode = 0
        ), // Left Left node
        DecisionTreeNodeLit(
          isLeafNode = true,
          featureClassIndex = 0,
          threshold = 2,
          rightNode = 0,
          leftNode = 0
        ) // Left Right node
      )
    )

    val p = DecisionTreeArrayParams(
      numTrees = 2,
      numNodes = Seq(3, 5),
      numClasses = 2,
      numFeatures = 2,
      fixedPointWidth = 5,
      fixedPointBinaryPoint = 2,
      treesLit = trees
    )

    val inCandidate      = Seq(0.5, 2.5)
    val expectedDecision = Seq(1, 0)
    decisionTreeArraySimpleSingleTest(p, inCandidate, expectedDecision)
  }
}
