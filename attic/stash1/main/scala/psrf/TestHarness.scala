package psrf

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import config.{Field, Parameters}

case object TestHarnessKey extends Field[TestHarnessParams](TestHarnessParams(Nil, Nil, Nil))

case class TestHarnessParams(
  testCandidates:            List[List[Double]],
  swRelativeClassifications: List[Int],
  targetClassifications:     List[Int])

trait HasTestHarnessParams extends HasFixedPointParameters {
  val params                    = p(TestHarnessKey)
  val testCandidates            = params.testCandidates
  val swRelativeClassifications = params.swRelativeClassifications
  val targetClassifications     = params.targetClassifications
}

/** Harness to test the [[psrf.RandomForestClassifier]] module.
  *
  * Test cases relative to software and target dataset are stored in internal ROMs.
  */
class RandomForestClassifierTestHarness(implicit val p: Parameters) extends Module with HasTestHarnessParams {
  require(
    testCandidates.length == swRelativeClassifications.length,
    "Number of test candidates and software relative classifications don't match"
  )
  require(
    testCandidates.length == targetClassifications.length,
    "Number of test candidates and target classifications don't match"
  )
  val numCases = testCandidates.length
  val io = IO(new Bundle {
    val start = Input(Bool())
    val done  = Output(Bool())
    val out = Irrevocable(new Bundle {
      val swRelativeClassification = UInt()
      val targetClassification     = UInt()
      val resultantClassification  = UInt()
      val swRelativePass           = Bool()
      val targetPass               = Bool()
      val noClearMajority          = Bool()
    })
  })

  val busy     = RegInit(false.B)
  val pokeDone = RegInit(true.B)
  val testCandidateROM = VecInit(
    testCandidates.map(c => VecInit(c.map(f => FixedPoint.fromDouble(f, fixedPointWidth.W, fixedPointBinaryPoint.BP))))
  )
  val swRelativeClassificationROM        = VecInit(swRelativeClassifications.map(_.U))
  val targetClassificationROM            = VecInit(targetClassifications.map(_.U))
  val randomForestClassifier             = Module(new RandomForestClassifier()(p))
  val (pokeCounter, pokeCounterWrap)     = Counter(randomForestClassifier.io.in.fire, numCases)
  val (expectCounter, expectCounterWrap) = Counter(randomForestClassifier.io.out.fire, numCases)

  randomForestClassifier.io.in.valid := busy && !pokeDone
  randomForestClassifier.io.in.bits  := testCandidateROM(pokeCounter)

  io.out.valid := randomForestClassifier.io.out.valid
  io.out.bits.swRelativePass := swRelativeClassificationROM(
    expectCounter
  ) === randomForestClassifier.io.out.bits.classification
  io.out.bits.targetPass := targetClassificationROM(
    expectCounter
  ) === randomForestClassifier.io.out.bits.classification
  io.out.bits.swRelativeClassification := swRelativeClassificationROM(expectCounter)
  io.out.bits.targetClassification     := targetClassificationROM(expectCounter)
  io.out.bits.resultantClassification  := randomForestClassifier.io.out.bits.classification
  io.out.bits.noClearMajority          := randomForestClassifier.io.out.bits.noClearMajority
  randomForestClassifier.io.out.ready  := io.out.ready

  io.done := expectCounterWrap && randomForestClassifier.io.out.fire

  busy     := Mux(busy, !io.done, io.start)
  pokeDone := Mux(pokeDone, !io.start, pokeCounterWrap && randomForestClassifier.io.in.ready)
}
