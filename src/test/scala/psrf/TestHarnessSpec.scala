package psrf

import chisel3._
import chisel3.experimental.VecLiterals._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import psrf._
import psrf.config.{Config, Parameters}

class RandomForestClassifierTestHarnessSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  def testHarnessTest(
    p:                         Parameters,
    expectedPass:              List[Boolean],
    expectedNoClearMajorities: List[Boolean]
  ): Unit = {
    val annos = Seq(WriteVcdAnnotation)

    test(new RandomForestClassifierTestHarness()(p)).withAnnotations(annos) { dut =>
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
    val p = new Config((site, here, up) => {
      case NumFeatures           => 2
      case NumClasses            => 2
      case NumTrees              => 2
      case FixedPointWidth       => 5
      case FixedPointBinaryPoint => 2
      case TreeLiterals =>
        List(
          List(
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
          List(
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
      case TestHarnessKey =>
        TestHarnessParams(
          testCandidates = List(List(0.5, 0.5), List(1, 2.5), List(2, 2.5)),
          expectedClassifications = List(0, 0, 0)
        )
    })

    val expectedPass              = List(false, true, true)
    val expectedNoClearMajorities = List(false, true, false)
    testHarnessTest(p, expectedPass, expectedNoClearMajorities)
  }
}
