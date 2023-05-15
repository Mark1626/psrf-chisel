package psrf.modules

import chisel3._
import chisel3.experimental.VecLiterals._
import chisel3.experimental.BundleLiterals._
import chisel3.util._
import chipsalliance.rocketchip.config.{Config, Parameters}
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import psrf.params.{DecisionTreeConfig, DecisionTreeConfigKey, FixedPointBinaryPoint, FixedPointWidth, MaxTrees}

class RandomForestMMIOModuleSpecHelper(dut: RandomForestMMIOModule) {
  def toFixedPoint(x: Double, scale: Long): Long = {
    val BP_SCALE = 1L << scale
    val xv = x * BP_SCALE
    if (xv < 0.0) {
      (xv - 0.5).toLong
    } else {
      (xv + 0.5).toLong
    }
  }

  def createCandidate(value: Double, last: Long = 0L): Long = {
    toFixedPoint(value, Constants.bpWidth) + (last << 50)
  }
}

class RandomForestMMIOModuleSpec extends AnyFlatSpec with ChiselScalatestTester {
  val threeTreesParams = new Config((site, here, up) => {
    case FixedPointWidth => Constants.fpWidth
    case FixedPointBinaryPoint => Constants.bpWidth
    case DecisionTreeConfigKey => DecisionTreeConfig(
      maxFeatures = 2,
      maxNodes = 10,
      maxClasses = 10,
      maxDepth = 10
    )
    case MaxTrees => 3
  })
  val oneTreeParams = new Config((site, here, up) => {
    case FixedPointWidth => Constants.fpWidth
    case FixedPointBinaryPoint => Constants.bpWidth
    case DecisionTreeConfigKey => DecisionTreeConfig(
      maxFeatures = 2,
      maxNodes = 10,
      maxClasses = 10,
      maxDepth = 10
    )
    case MaxTrees => 1
  })

  it should "be able to store candidates and fire request during last candidate" in {
    test(new RandomForestMMIOModule()(oneTreeParams))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new RandomForestMMIOModuleSpecHelper(dut)

        val candidate1 = 0.5
        val candidate2 = 1.0

        dut.numClasses.poke(2.U)
        dut.numTrees.poke(1.U)
        dut.candidateData.initSource()
        dut.candidateData.setSourceClock(dut.clock)
        dut.io.in.initSink()
        dut.io.in.setSinkClock(dut.clock)

        val expected = new TreeInputBundle()(oneTreeParams).Lit(
          _.candidates -> Vec.Lit(candidate1.F(32.W,16.BP), candidate2.F(32.W,16.BP)),
          _.offset -> 0.U)

        fork {
          dut.busy.expect(false.B)
          dut.candidateData.enqueueSeq(Seq(
            helper.createCandidate(candidate1).U,
            helper.createCandidate(candidate2, 1).U
          ))
        } .fork {
          dut.io.in.expectDequeue(expected)
          dut.busy.expect(true.B)
        }.join()
      }
  }

  it should "return decisionValid for 1 tree when classification is done" in {
    test(new RandomForestMMIOModule()(oneTreeParams))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new RandomForestMMIOModuleSpecHelper(dut)

        val candidate1 = 0.5
        val candidate2 = 1.0

        dut.candidateData.initSource()
        dut.candidateData.setSourceClock(dut.clock)
        dut.io.in.initSink()
        dut.io.in.setSinkClock(dut.clock)
        dut.io.out.initSource()
        dut.io.out.setSourceClock(dut.clock)

        val expected = new TreeInputBundle()(oneTreeParams).Lit(
          _.candidates -> Vec.Lit(candidate1.F(32.W, 16.BP), candidate2.F(32.W, 16.BP)),
          _.offset -> 0.U)

        val result = new TreeOutputBundle().Lit(
          _.classes -> 1.U,
          _.error -> 0.U
        )

        dut.numClasses.poke(4.U)
        dut.numTrees.poke(1.U)
        dut.busy.expect(false.B)
        dut.candidateData.enqueueSeq(Seq(
          helper.createCandidate(candidate1).U,
          helper.createCandidate(candidate2, 1).U
        ))

        dut.decisionValidIO.expect(false.B)

        dut.io.in.expectDequeue(expected)
        dut.busy.expect(true.B)
        dut.io.out.enqueue(result)

        // 3 + numClasses cycles are need to get response from majority voter
        dut.clock.step(7)

        dut.decisionValidIO.expect(true.B)
        dut.decisionIO.expect(1)
      }
  }

  it should "be able to run for multiple trees" in {
    test(new RandomForestMMIOModule()(threeTreesParams))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new RandomForestMMIOModuleSpecHelper(dut)

        val candidate1 = 0.5
        val candidate2 = 1.0

        dut.candidateData.initSource()
        dut.candidateData.setSourceClock(dut.clock)
        dut.io.in.initSink()
        dut.io.in.setSinkClock(dut.clock)
        dut.io.out.initSource()
        dut.io.out.setSourceClock(dut.clock)

        val expected0 = new TreeInputBundle()(threeTreesParams).Lit(
          _.candidates -> Vec.Lit(candidate1.F(32.W, 16.BP), candidate2.F(32.W, 16.BP)),
          _.offset -> 0.U)

        val expected1 = new TreeInputBundle()(threeTreesParams).Lit(
          _.candidates -> Vec.Lit(candidate1.F(32.W, 16.BP), candidate2.F(32.W, 16.BP)),
          _.offset -> 1.U)

        val expected2 = new TreeInputBundle()(threeTreesParams).Lit(
          _.candidates -> Vec.Lit(candidate1.F(32.W, 16.BP), candidate2.F(32.W, 16.BP)),
          _.offset -> 2.U)

        val result = new TreeOutputBundle().Lit(
          _.classes -> 2.U,
          _.error -> 0.U
        )

        dut.numClasses.poke(3.U)
        dut.numTrees.poke(3.U)
        dut.busy.expect(false.B)
        dut.candidateData.enqueueSeq(Seq(
          helper.createCandidate(candidate1).U,
          helper.createCandidate(candidate2, 1).U
        ))

        dut.io.in.expectDequeue(expected0)
        dut.busy.expect(true.B)
        dut.io.out.enqueue(result)
        dut.decisionValidIO.expect(false.B)

        dut.io.in.expectDequeue(expected1)
        dut.busy.expect(true.B)
        dut.io.out.enqueue(result)
        dut.decisionValidIO.expect(false.B)

        dut.io.in.expectDequeue(expected2)
        dut.io.out.enqueue(result)

        dut.clock.step(8)

        dut.decisionValidIO.expect(true.B)
        dut.decisionIO.expect(2)
      }
  }

  it should "be able to run for multiple trees and return majority" in {
    test(new RandomForestMMIOModule()(threeTreesParams))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new RandomForestMMIOModuleSpecHelper(dut)

        val candidate1 = 0.5
        val candidate2 = 1.0

        dut.candidateData.initSource()
        dut.candidateData.setSourceClock(dut.clock)
        dut.io.in.initSink()
        dut.io.in.setSinkClock(dut.clock)
        dut.io.out.initSource()
        dut.io.out.setSourceClock(dut.clock)

        val expected0 = new TreeInputBundle()(threeTreesParams).Lit(
          _.candidates -> Vec.Lit(candidate1.F(32.W, 16.BP), candidate2.F(32.W, 16.BP)),
          _.offset -> 0.U)

        val expected1 = new TreeInputBundle()(threeTreesParams).Lit(
          _.candidates -> Vec.Lit(candidate1.F(32.W, 16.BP), candidate2.F(32.W, 16.BP)),
          _.offset -> 1.U)

        val expected2 = new TreeInputBundle()(threeTreesParams).Lit(
          _.candidates -> Vec.Lit(candidate1.F(32.W, 16.BP), candidate2.F(32.W, 16.BP)),
          _.offset -> 2.U)

        val result0 = new TreeOutputBundle().Lit(
          _.classes -> 2.U,
          _.error -> 0.U
        )

        val result1 = new TreeOutputBundle().Lit(
          _.classes -> 1.U,
          _.error -> 0.U
        )

        dut.numClasses.poke(3.U)
        dut.numTrees.poke(3.U)
        dut.busy.expect(false.B)
        dut.candidateData.enqueueSeq(Seq(
          helper.createCandidate(candidate1).U,
          helper.createCandidate(candidate2, 1).U
        ))

        dut.io.in.expectDequeue(expected0)
        dut.busy.expect(true.B)
        dut.io.out.enqueue(result0)
        dut.decisionValidIO.expect(false.B)

        dut.io.in.expectDequeue(expected1)
        dut.busy.expect(true.B)
        dut.io.out.enqueue(result1)
        dut.decisionValidIO.expect(false.B)

        dut.io.in.expectDequeue(expected2)
        dut.io.out.enqueue(result1)

        dut.clock.step(8)

        dut.decisionValidIO.expect(true.B)
        dut.decisionIO.expect(1)
      }
  }

  it should "be able to stop on first error" in {
    test(new RandomForestMMIOModule()(threeTreesParams))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val helper = new RandomForestMMIOModuleSpecHelper(dut)

        val candidate1 = 0.5
        val candidate2 = 1.0

        dut.candidateData.initSource()
        dut.candidateData.setSourceClock(dut.clock)
        dut.io.in.initSink()
        dut.io.in.setSinkClock(dut.clock)
        dut.io.out.initSource()
        dut.io.out.setSourceClock(dut.clock)

        val expected0 = new TreeInputBundle()(threeTreesParams).Lit(
          _.candidates -> Vec.Lit(candidate1.F(32.W, 16.BP), candidate2.F(32.W, 16.BP)),
          _.offset -> 0.U)

        val expected1 = new TreeInputBundle()(threeTreesParams).Lit(
          _.candidates -> Vec.Lit(candidate1.F(32.W, 16.BP), candidate2.F(32.W, 16.BP)),
          _.offset -> 1.U)

        val expected2 = new TreeInputBundle()(threeTreesParams).Lit(
          _.candidates -> Vec.Lit(candidate1.F(32.W, 16.BP), candidate2.F(32.W, 16.BP)),
          _.offset -> 2.U)

        val result0 = new TreeOutputBundle().Lit(
          _.classes -> 2.U,
          _.error -> 1.U
        )

        dut.numClasses.poke(3.U)
        dut.numTrees.poke(3.U)
        dut.busy.expect(false.B)
        dut.candidateData.enqueueSeq(Seq(
          helper.createCandidate(candidate1).U,
          helper.createCandidate(candidate2, 1).U
        ))

        dut.io.in.expectDequeue(expected0)
        dut.busy.expect(true.B)
        dut.io.out.enqueue(result0)
        dut.decisionValidIO.expect(true.B)

        dut.errorIO.expect(1.U)
      }
  }

}
