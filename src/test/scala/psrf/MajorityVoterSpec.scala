package psrf.test

import chisel3._
import chisel3.experimental.VecLiterals._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import psrf.MajorityVoterArea

class MajorityVoterSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  def majorityVoterSingleTest(
    numTrees:               Int,
    numClasses:             Int,
    inDecisions:            Seq[Int],
    expectedClassification: Int
  ): Unit = {
    val annos = Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)

    val decisions = Vec.Lit(inDecisions.map(_.U): _*)

    test(new MajorityVoterArea(numTrees, numClasses)).withAnnotations(annos) { dut =>
      dut.io.in.valid.poke(false.B)
      dut.io.in.ready.expect(true.B)
      dut.clock.step()
      dut.io.in.bits.poke(decisions)
      dut.io.in.valid.poke(true.B)
      dut.io.out.ready.poke(true.B)
      while (dut.io.out.valid.peek().litValue == 0) dut.clock.step()
      dut.io.out.bits.classification.expect(expectedClassification.U)
    }
  }

  def majorityVoterSeqTest(
    numTrees:                Int,
    numClasses:              Int,
    inDecisions:             Seq[Seq[Int]],
    expectedClassifications: Seq[Int]
  ): Unit = {
    val annos = Seq(WriteVcdAnnotation)

    val decisions = inDecisions.map(d => Vec.Lit(d.map(_.B): _*))

    test(new MajorityVoterArea(numTrees, numClasses)).withAnnotations(annos) { dut =>
      dut.io.in.initSource()
      dut.io.in.setSourceClock(dut.clock)
      dut.io.out.initSink()
      dut.io.out.setSinkClock(dut.clock)
      dut.io.out.setSinkClock(dut.clock)
      fork {
        dut.io.in.enqueueSeq(decisions)
      }.fork {
        dut.io.out.expectDequeueSeq(expectedClassifications.map(_.B))
      }.join()
    }
  }

  it should "give correct classification for three input trees" in {
    val numTrees               = 3
    val inDecisions            = Seq(true, true, false)
    val expectedClassification = true

    majorityVoterSingleTest(numTrees, inDecisions, expectedClassification)
  }

  it should "give correct classifications for five input trees in any order" in {
    val numTrees       = 5
    val countThreshold = math.ceil(numTrees.toDouble / 2).toInt
    def intAsBooleanSeq(bits: Int, n: Int): Seq[Boolean] = {
      val masks = (0 until bits).map(1 << _)
      masks.map(m => (n & m) == m)
    }

    val inDecisions             = (0 until math.pow(2, numTrees).toInt).map(intAsBooleanSeq(numTrees, _))
    val expectedClassifications = inDecisions.map(d => d.count(b => b) >= countThreshold)

    majorityVoterSeqTest(numTrees, inDecisions, expectedClassifications)
  }

  it should "keep output classification valid until it is consumed" in {
    val numTrees               = 5
    val inDecisions            = Seq(true, true, false, true, false)
    val expectedClassification = true

    val annos     = Seq(WriteVcdAnnotation)
    val decisions = Vec.Lit(inDecisions.map(_.B): _*)

    test(new MajorityVoter(numTrees)).withAnnotations(annos) { dut =>
      dut.io.in.valid.poke(false.B)
      dut.io.in.ready.expect(true.B)
      dut.clock.step()
      dut.io.in.bits.poke(decisions)
      dut.io.in.valid.poke(true.B)
      dut.io.out.ready.poke(false.B)
      // Wait until output becomes valid
      while (dut.io.out.valid.peek().litValue == 0) {
        dut.clock.step()
        dut.io.in.ready.expect(false.B)
      }
      dut.io.out.bits.expect(expectedClassification.B)
      dut.io.in.valid.poke(false.B)
      // Check if output stays latched for 10 cycles
      for (i <- 0 until 10) {
        dut.clock.step()
        dut.io.in.ready.expect(false.B)
        dut.io.out.valid.expect(true.B)
        dut.io.out.bits.expect(expectedClassification.B)
      }
      // Check if module goes back to initial idle state when output is consumed
      dut.io.out.ready.poke(true.B)
      dut.clock.step()
      dut.io.in.ready.expect(true.B)
      dut.io.out.valid.expect(false.B)
    }
  }
}
