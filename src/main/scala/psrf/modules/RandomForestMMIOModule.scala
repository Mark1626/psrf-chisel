package psrf.modules

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.util._
import psrf.accelerator.Candidate
import psrf.params.{DecisionTreeConfigKey, HasRandomForestParams}

class RandomForestMMIOModule()(implicit val p: Parameters) extends Module with HasRandomForestParams {
  val io = IO(Flipped(new TreeIO()(p)))

  val candidateData = IO(Flipped(Decoupled(UInt(64.W))))

  val decisionValidIO = IO(Output(Bool()))
  val decisionIO = IO(Output(UInt(32.W)))
  val errorIO = IO(Output(UInt(2.W)))
  val resetDecision = IO(Input(Bool()))

  val idle :: busy :: done :: Nil = Enum(3)
  val state = RegInit(idle)

  val candidates = Reg(Vec(maxFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)))
  val decision = RegInit(0.U(32.W))
  val decisionValid = RegInit(false.B)

  val error = RegInit(0.U(2.W))
  //val candidateLast = Wire(Bool())
  val candidateLast = RegInit(false.B)

  candidateData.ready := true.B
  when (candidateData.fire) {
    val last = candidateData.bits(50)
    val candidateId = candidateData.bits(49, 32)
    val candidateValue = candidateData.bits(31, 0)

    candidates(config.maxFeatures-1) := candidateValue.asTypeOf(new Candidate()(p).data)
    for (i <- config.maxFeatures-2 to 0 by -1) {
      candidates(i) := candidates(i+1)
    }

//    candidates(0) := candidateValue.asTypeOf(new Candidate()(p).data)
//    for (i <- 1 until config.maxFeatures) {
//      candidates(i) := candidates(i - 1)
//    }
    candidateLast := last
  }

  decisionValidIO := decisionValid
  decisionIO := decision
  errorIO := error

  io.in.valid := false.B
  io.in.bits.candidates := DontCare
  io.in.bits.offset := DontCare

  when(io.in.ready && !io.busy) {
    io.in.bits.candidates := candidates
    io.in.bits.offset := 0.U // Tree 0
    io.in.valid := candidateLast
  }

  when (io.in.fire) {
    candidateLast := false.B
  }

  when (resetDecision) {
    decisionValid := false.B
  }

  io.out.ready := true.B
  when(io.out.fire) {
    decision := io.out.bits.classes
    error := io.out.bits.error
    decisionValid := true.B
  }
  
}
