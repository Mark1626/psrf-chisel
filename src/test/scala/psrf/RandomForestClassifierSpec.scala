package psrf.test

import chisel3._
import chisel3.experimental.VecLiterals._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import psrf.RandomForestClassifierParams
import psrf.RandomForestClassifier
import psrf.DecisionTreeNodeLit

class RandomForestSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  def randomForestSingleTest(
    p:                       RandomForestClassifierParams,
    inCandidate:             Seq[Double],
    expectedClassification:  Int,
    expectedNoClearMajority: Option[Boolean] = None
  ): Unit = {
    val annos = Seq(WriteVcdAnnotation)

    val candidate = inCandidate.asFixedPointVecLit(p.fixedPointWidth.W, p.fixedPointBinaryPoint.BP)

    test(new RandomForestClassifier(p)).withAnnotations(annos) { dut =>
      dut.io.in.valid.poke(false.B)
      dut.io.in.ready.expect(true.B)
      dut.clock.step()
      dut.io.in.bits.poke(candidate)
      dut.io.in.valid.poke(true.B)
      dut.io.out.ready.poke(true.B)
      while (dut.io.out.valid.peek().litValue == 0) dut.clock.step()
      dut.io.out.bits.classification.expect(expectedClassification.U)
      if (expectedNoClearMajority.isDefined) {
        dut.io.out.bits.noClearMajority.expect(expectedNoClearMajority.get.B)
      }
    }
  }

  it should "give correct classification for two trees of same depth" in {
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

    val p = RandomForestClassifierParams(
      numTrees = 2,
      numNodes = Seq(3, 3),
      numClasses = 2,
      numFeatures = 2,
      fixedPointWidth = 5,
      fixedPointBinaryPoint = 2,
      treesLit = trees
    )

    val inCandidate             = Seq(0.5, 2)
    val expectedClassification  = 0
    val expectedNoClearMajority = true
    randomForestSingleTest(p, inCandidate, expectedClassification, Some(expectedNoClearMajority))
  }
}
