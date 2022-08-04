package psrf

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import config.{Field, Parameters}

case object TestHarnessKey extends Field[TestHarnessParams]

case class TestHarnessParams(
  testCandidates:          List[List[Double]],
  expectedClassifications: List[Int])

trait HasTestHarnessParams extends HasFixedPointParameters {
  val params                  = p(TestHarnessKey)
  val testCandidates          = params.testCandidates
  val expectedClassifications = params.expectedClassifications
}

class RandomForestClassifierTestHarness(implicit val p: Parameters) extends Module with HasTestHarnessParams {
  require(
    testCandidates.length == expectedClassifications.length,
    "Number of test candidates and expected classifications don't match"
  )
  val numCases = testCandidates.length
  val io = IO(new Bundle {
    val start = Input(Bool())
    val done  = Output(Bool())
    val out = Irrevocable(new Bundle {
      val pass            = Bool()
      val noClearMajority = Bool()
    })
  })

  val busy     = RegInit(false.B)
  val pokeDone = RegInit(false.B)
  val testCandidateROM = VecInit(
    testCandidates.map(c => VecInit(c.map(f => FixedPoint.fromDouble(f, fixedPointWidth.W, fixedPointBinaryPoint.BP))))
  )
  val expectedClassificationROM          = VecInit(expectedClassifications.map(_.U))
  val randomForestClassifier             = Module(new RandomForestClassifier()(p))
  val (pokeCounter, pokeCounterWrap)     = Counter(randomForestClassifier.io.in.fire, numCases)
  val (expectCounter, expectCounterWrap) = Counter(randomForestClassifier.io.out.fire, numCases)

  randomForestClassifier.io.in.valid := busy && !pokeDone
  randomForestClassifier.io.in.bits  := testCandidateROM(pokeCounter)

  io.out.valid                        := randomForestClassifier.io.out.valid
  io.out.bits.pass                    := expectedClassificationROM(expectCounter) === randomForestClassifier.io.out.bits.classification
  io.out.bits.noClearMajority         := randomForestClassifier.io.out.bits.noClearMajority
  randomForestClassifier.io.out.ready := io.out.ready

  io.done := expectCounterWrap && randomForestClassifier.io.out.fire

  busy     := Mux(busy, !io.done, io.start)
  pokeDone := Mux(pokeDone, pokeCounterWrap && randomForestClassifier.io.in.ready, io.start)
}
