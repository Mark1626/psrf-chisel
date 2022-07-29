package psrf

import chisel3._
import chisel3.experimental.VecLiterals._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import psrf.RandomForestClassifierParams
import psrf.RandomForestClassifierTestHarness

class RandomForestClassifierTestHarnessSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  def testHarnessTest(
    p:                         RandomForestClassifierParams,
    testCandidates:            List[List[Double]],
    expectedClassifications:   List[Int],
    expectedPass:              List[Boolean],
    expectedNoClearMajorities: List[Boolean]
  ): Unit = {
    val annos = Seq(WriteVcdAnnotation)

    test(new RandomForestClassifierTestHarness(p, testCandidates, expectedClassifications)).withAnnotations(annos) {
      dut =>
        var caseIndex = 0
        dut.io.start.poke(true.B)
        dut.io.out.ready.poke(true.B)
        while (dut.io.done.peek().litValue == 0) {
          dut.clock.step()
          while (dut.io.out.valid.peek().litValue == 0) dut.clock.step()
          dut.io.out.bits.pass.expect(expectedPass(caseIndex).B)
          dut.io.out.bits.noClearMajority.expect(expectedNoClearMajorities(caseIndex).B)
          caseIndex += 1
        }
    }
  }

  it should "give correct test results for two trees of same depth" in {
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

    val testCandidates            = List(List(0.5, 0.5), List(1, 2.5), List(2, 2.5))
    val expectedClassifications   = List(0, 0, 0)
    val expectedPass              = List(false, true, true)
    val expectedNoClearMajorities = List(false, true, false)
    testHarnessTest(p, testCandidates, expectedClassifications, expectedPass, expectedNoClearMajorities)
  }
}
