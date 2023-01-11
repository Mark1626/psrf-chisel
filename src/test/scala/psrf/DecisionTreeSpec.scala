package psrf.test

import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.util._
import chiseltest._
import chiseltest.simulator.VerilatorBackendAnnotation
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import psrf._
import psrf.config.{Config, Parameters}

class DecisionTreeSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  def decisionTreeSingleTest(
    p:                Parameters,
    inCandidate:      Seq[Double],
    expectedDecision: Int
  ): Unit = {
    val annos = Seq(WriteVcdAnnotation)
    val candidate = inCandidate.asFixedPointVecLit(
      p(FixedPointWidth).W,
      p(FixedPointBinaryPoint).BP
    )

    test(new DecisionTreeChiselModule()(p)).withAnnotations(annos) { dut =>
      dut.io.in.valid.poke(false.B)
      dut.io.in.ready.expect(true.B)
      dut.clock.step()
      dut.io.in.bits.poke(candidate)
      dut.io.in.valid.poke(true.B)
      dut.io.out.ready.poke(true.B)
      while (dut.io.out.valid.peek().litValue == 0) dut.clock.step()
      dut.io.out.bits.expect(expectedDecision.U)
    }
  }

  def decisionTreeSeqTest(
    p:                 Parameters,
    inCandidates:      Seq[Seq[Double]],
    expectedDecisions: Seq[Int]
  ): Unit = {
    val annos      = Seq(WriteVcdAnnotation)
    val candidates = inCandidates.map(x => x.asFixedPointVecLit(p(FixedPointWidth).W, p(FixedPointBinaryPoint).BP))

    test(new DecisionTreeChiselModule()(p)).withAnnotations(annos) { dut =>
      dut.io.in.initSource()
      dut.io.in.setSourceClock(dut.clock)
      dut.io.out.initSink()
      dut.io.out.setSinkClock(dut.clock)
      fork {
        dut.io.in.enqueueSeq(candidates)
      }.fork {
        dut.io.out.expectDequeueSeq(expectedDecisions.map(_.U))
      }.join()
    }
  }

  it should "got to left node and give correct decision" in {
    val p = new Config((site, here, up) => {
      case NumFeatures           => 2
      case NumClasses            => 2
      case NumTrees              => 1
      case FixedPointWidth       => 5
      case FixedPointBinaryPoint => 2
      case TreeLiteral =>
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
        )
    })
    val inCandidate      = Seq(0.5, 2)
    val expectedDecision = 1
    decisionTreeSingleTest(p, inCandidate, expectedDecision)
  }

  it should "got to right node and give correct decision" in {
    val p = new Config((site, here, up) => {
      case NumFeatures           => 2
      case NumClasses            => 2
      case NumTrees              => 1
      case FixedPointWidth       => 5
      case FixedPointBinaryPoint => 2
      case TreeLiteral =>
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
    })
    val inCandidate      = Seq(0.5, 2)
    val expectedDecision = 0
    decisionTreeSingleTest(p, inCandidate, expectedDecision)
  }

  it should "be able to traverse a tree with five nodes and give correct decision" in {
    val p = new Config((site, here, up) => {
      case NumFeatures           => 2
      case NumClasses            => 2
      case NumTrees              => 1
      case FixedPointWidth       => 5
      case FixedPointBinaryPoint => 2
      case TreeLiteral =>
        List(
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
    })
    val inCandidate      = Seq(0.5, 2.5)
    val expectedDecision = 0
    decisionTreeSingleTest(p, inCandidate, expectedDecision)
  }

  it should "give correct decision for multiple candidates" in {
    val p = new Config((site, here, up) => {
      case NumFeatures           => 2
      case NumClasses            => 2
      case NumTrees              => 1
      case FixedPointWidth       => 5
      case FixedPointBinaryPoint => 2
      case TreeLiteral =>
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
        )
    })
    val inCandidates      = Seq(Seq(0.5, 2), Seq(2, 0.5), Seq(1, 0.5), Seq(1.5, 1))
    val expectedDecisions = Seq(1, 0, 1, 0)
    decisionTreeSeqTest(p, inCandidates, expectedDecisions)
  }

  it should "keep output decision valid until it is consumed" in {
    val p = new Config((site, here, up) => {
      case NumFeatures           => 2
      case NumClasses            => 2
      case NumTrees              => 1
      case FixedPointWidth       => 5
      case FixedPointBinaryPoint => 2
      case TreeLiteral =>
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
        )
    })
    val inCandidate      = Seq(2d, 3d)
    val expectedDecision = 0
    val annos            = Seq(WriteVcdAnnotation)
    val candidate        = inCandidate.asFixedPointVecLit(p(FixedPointWidth).W, p(FixedPointBinaryPoint).BP)

    test(new DecisionTreeChiselModule()(p)).withAnnotations(annos) { dut =>
      dut.io.in.valid.poke(false.B)
      dut.io.in.ready.expect(true.B)
      dut.clock.step()
      dut.io.in.bits.poke(candidate)
      dut.io.in.valid.poke(true.B)
      dut.io.out.ready.poke(false.B)
      // Wait until output becomes valid
      while (dut.io.out.valid.peek().litValue == 0) {
        dut.clock.step()
        dut.io.in.ready.expect(false.B)
      }
      dut.io.out.bits.expect(expectedDecision.B)
      dut.io.in.valid.poke(false.B)
      // Check if output stays latched for 10 cycles
      for (i <- 0 until 10) {
        dut.clock.step()
        dut.io.in.ready.expect(false.B)
        dut.io.out.valid.expect(true.B)
        dut.io.out.bits.expect(expectedDecision.B)
      }
      // Check if module goes back to initial idle state when output is consumed
      dut.io.out.ready.poke(true.B)
      dut.clock.step()
      dut.io.in.ready.expect(true.B)
      dut.io.out.valid.expect(false.B)
    }
  }
}
