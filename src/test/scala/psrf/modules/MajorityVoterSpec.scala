package psrf.modules

import chisel3._
import chisel3.experimental.VecLiterals._
import chisel3.experimental.BundleLiterals._
import chisel3.util._
import chiseltest._
import freechips.rocketchip.system.DefaultConfig
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import psrf.params.{DecisionTreeConfig, DecisionTreeConfigKey, FixedPointBinaryPoint, FixedPointWidth, MaxTrees}

class MajorityVoterSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  def majorityVoterSingleTest(
                               numTrees:                Int,
                               numClasses:              Int,
                               inDecisions:             Seq[Int],
                               expectedClassification:  Int,
                               expectedNoClearMajority: Option[Boolean] = None
                             ): Unit = {
    val annos = Seq(WriteVcdAnnotation)
    val classWidth = log2Ceil(10)
    val p = (new DefaultConfig).alterMap(
      Map(
        FixedPointWidth -> 32,
        FixedPointBinaryPoint -> 16,
        MaxTrees   -> 10,
        DecisionTreeConfigKey ->
          DecisionTreeConfig(
            maxFeatures = 2,
            maxNodes = 10,
            maxClasses = 10,
            maxDepth = 10
          )
      )
    )
    val inDecisionMod = inDecisions ++ Seq.fill(10 - numTrees)(0)
    val decisions = Vec.Lit(inDecisionMod.map(_.U(classWidth.W)): _*)

    test(new MajorityVoterModule()(p)).withAnnotations(annos) { dut =>
      dut.io.numClasses.poke(numClasses)
      dut.io.numTrees.poke(numTrees)
      dut.clock.step()

      dut.io.in.valid.poke(false.B)
      dut.io.in.ready.expect(true.B)
      dut.clock.step()
      dut.io.in.bits.poke(decisions)
      dut.io.in.valid.poke(true.B)
      dut.io.out.ready.poke(false.B)
      while (dut.io.out.valid.peek().litValue == 0) dut.clock.step()
      dut.io.out.bits.classification.expect(expectedClassification.U)
      if (expectedNoClearMajority.isDefined) {
        dut.io.out.bits.noClearMajority.expect(expectedNoClearMajority.get.B)
      }
    }
  }

  def majorityVoterSeqTest(
                            numTrees:                Int,
                            numClasses:              Int,
                            inDecisions:             Seq[Seq[Int]],
                            expectedClassifications: Seq[Int],
                            expectedNoClearMajority: Seq[Boolean]
                          ): Unit = {
    val annos = Seq(WriteVcdAnnotation)
    val classWidth = log2Ceil(10)
    val p = (new DefaultConfig).alterMap(
      Map(
        FixedPointWidth -> 32,
        FixedPointBinaryPoint -> 16,
        MaxTrees -> 10,
        DecisionTreeConfigKey ->
          DecisionTreeConfig(
            maxFeatures = 2,
            maxNodes = 10,
            maxClasses = 10,
            maxDepth = 10
          )
      )
    )
    val decisions = inDecisions.map(d =>
      Vec.Lit(
        (d ++ Seq.fill(10-numTrees)(0)).map(_.U(classWidth.W)): _*)
    )

    val expected = expectedClassifications
      .zip(expectedNoClearMajority)
      .map(x => (new MajorityVoterOut()(p)).Lit(_.classification -> x._1.U, _.noClearMajority -> x._2.B))

    test(new MajorityVoterModule()(p)).withAnnotations(annos) { dut =>
      dut.io.numClasses.poke(numClasses)
      dut.io.numTrees.poke(numTrees)
      dut.io.in.initSource()
      dut.io.in.setSourceClock(dut.clock)
      dut.io.out.initSink()
      dut.io.out.setSinkClock(dut.clock)
      dut.io.out.setSinkClock(dut.clock)
      fork {
        dut.io.in.enqueueSeq(decisions)
      }.fork {
        dut.io.out.expectDequeueSeq(expected)
      }.join()
    }
  }

  it should "give correct classification for three input trees with two classes" in {
    val numTrees                = 3
    val numClasses              = 2
    val inDecisions             = Seq(1, 0, 1)
    val expectedClassification  = 1
    val expectedNoClearMajority = false

    majorityVoterSingleTest(numTrees, numClasses, inDecisions, expectedClassification)
  }

  it should "give correct classifications for five input trees with two classes in any order" in {
    val numTrees       = 5
    val numClasses     = 2
    val countThreshold = math.ceil(numTrees.toDouble / 2).toInt
    def intAsBinarySeq(bits: Int, n: Int): Seq[Int] = {
      (0 until bits).map(b => (n >> b) & 1)
    }

    val inDecisions             = (0 until math.pow(2, numTrees).toInt).map(intAsBinarySeq(numTrees, _))
    val expectedClassifications = inDecisions.map(d => if (d.count(_ == 1) >= countThreshold) 1 else 0)
    val expectedNoClearMajority = Seq.fill(math.pow(2, numTrees).toInt)(false)

    majorityVoterSeqTest(numTrees, numClasses, inDecisions, expectedClassifications, expectedNoClearMajority)
  }

  it should "keep output classification valid until it is consumed" in {
    val numTrees                = 5
    val numClasses              = 2
    val inDecisions             = Seq(1, 1, 0, 1, 0)
    val expectedClassification  = 1
    val expectedNoClearMajority = false

    val annos = Seq(WriteVcdAnnotation)
    val p = (new DefaultConfig).alterMap(
      Map(
      FixedPointWidth -> 32,
      FixedPointBinaryPoint -> 16,
        MaxTrees -> numTrees,
        DecisionTreeConfigKey ->
          DecisionTreeConfig(
            maxFeatures = 2,
            maxNodes = 10,
            maxClasses = numClasses,
            maxDepth = 10
          )
      )
    )
    val decisions = Vec.Lit(inDecisions.map(_.U): _*)

    test(new MajorityVoterModule()(p)).withAnnotations(annos) { dut =>
      dut.io.numClasses.poke(numClasses)
      dut.io.numTrees.poke(numTrees)
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
      dut.io.out.bits.classification.expect(expectedClassification.U)
      dut.io.out.bits.noClearMajority.expect(expectedNoClearMajority.B)
      dut.io.in.valid.poke(false.B)
      // Check if output stays latched for 10 cycles
      for (i <- 0 until 10) {
        dut.clock.step()
        dut.io.in.ready.expect(false.B)
        dut.io.out.valid.expect(true.B)
        dut.io.out.bits.classification.expect(expectedClassification.U)
        dut.io.out.bits.noClearMajority.expect(expectedNoClearMajority.B)
      }
      // Check if module goes back to initial idle state when output is consumed
      dut.io.out.ready.poke(true.B)
      dut.clock.step()
      dut.io.in.ready.expect(true.B)
      dut.io.out.valid.expect(false.B)
    }
  }

  it should "give correct classification for five input trees with three classes" in {
    val numTrees                = 5
    val numClasses              = 3
    val inDecisions             = Seq(2, 1, 0, 0, 0)
    val expectedClassification  = 0
    val expectedNoClearMajority = false

    majorityVoterSingleTest(numTrees, numClasses, inDecisions, expectedClassification)
  }

  it should "give correct classification and indicate no clear majority for five input trees with three classes" in {
    val numTrees                = 5
    val numClasses              = 3
    val inDecisions             = Seq(2, 1, 0, 0, 2)
    val expectedClassification  = 0
    val expectedNoClearMajority = true

    majorityVoterSingleTest(numTrees, numClasses, inDecisions, expectedClassification)
  }
}

