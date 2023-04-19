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
  val busy = IO(Output(Bool()))
  val numTrees = IO(Input(UInt(10.W)))

  val s_idle :: s_busy :: s_count :: s_done :: Nil = Enum(4)
  val state = RegInit(s_idle)

  val candidates = Reg(Vec(maxFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)))

  val currTree = RegInit(0.U((log2Ceil(maxTrees)+1).W))
  // Decision from all the trees
  // TODO: How do I know everything is complete
  val decisions = Reg(Vec(maxTrees, UInt(9.W)))
  val errors = Reg(Vec(maxTrees, UInt(2.W)))

  // Final decision after counting
  val decision = RegInit(0.U(32.W))
  val decisionValid = RegInit(false.B)

  val error = RegInit(0.U(2.W))
  //val candidateLast = Wire(Bool())
  val activeClassification = RegInit(false.B)

  busy := state =/= s_idle

  // TODO: Change this
  candidateData.ready := state === s_idle
  when (candidateData.fire) {
    val last = candidateData.bits(50)
    val candidateId = candidateData.bits(49, 32)
    val candidateValue = candidateData.bits(31, 0)

    candidates(config.maxFeatures-1) := candidateValue.asTypeOf(new Candidate()(p).data)
    for (i <- config.maxFeatures-2 to 0 by -1) {
      candidates(i) := candidates(i+1)
    }
    activeClassification := last
    when (last) {
      state := s_busy
      currTree := 0.U
    }
  }

  decisionValidIO := decisionValid
  decisionIO := decision
  errorIO := error

  io.in.valid := false.B
  io.in.bits.candidates := DontCare
  io.in.bits.offset := DontCare

  when(state === s_busy && io.in.ready && !io.busy) {
    io.in.bits.candidates := candidates
    io.in.bits.offset := currTree
    io.in.valid := activeClassification
  }

  when (state === s_busy && currTree === numTrees) {
    state := s_done
  }

  when (state === s_done) {
    decision := decisions(0)
    error := errors(0)
    decisionValid := true.B
    activeClassification := false.B
  }

//  when (state === s_done) {
//    decision := decisions(0)
//    error := errors(0)
//    decisionValid := true.B
//    activeClassification := false.B
//  }

  io.out.ready := true.B
  when(io.out.fire) {
    decisions(currTree) := io.out.bits.classes
    errors(currTree) := io.out.bits.error
    currTree := currTree + 1.U
  }

  when(resetDecision) {
    decisionValid := false.B
    state := s_idle
    currTree := 0.U
  }
  
}
