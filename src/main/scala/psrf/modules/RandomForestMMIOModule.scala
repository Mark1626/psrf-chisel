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
  val numClasses = IO(Input(UInt(10.W)))

  val majorityVoter = Module(new MajorityVoterModule()(p))

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

  majorityVoter.io.in.valid := false.B
  majorityVoter.io.in.bits := DontCare
  majorityVoter.io.out.ready := state === s_count

  majorityVoter.io.numClasses := numClasses
  majorityVoter.io.numTrees := numTrees

  candidateData.ready := state === s_idle

  decisionValidIO := decisionValid
  decisionIO := decision
  errorIO := error

  io.in.valid := false.B
  io.in.bits.candidates := DontCare
  io.in.bits.offset := DontCare

  when(candidateData.fire) {
    val last = candidateData.bits(50)
    val candidateId = candidateData.bits(49, 32)
    val candidateValue = candidateData.bits(31, 0)

    candidates(config.maxFeatures - 1) := candidateValue.asTypeOf(new Candidate()(p).data)
    for (i <- config.maxFeatures - 2 to 0 by -1) {
      candidates(i) := candidates(i + 1)
    }
    activeClassification := last
    when(last) {
      state := s_busy
      currTree := 0.U
    }
  }

  when(state === s_busy && io.in.ready && !io.busy) {
    io.in.bits.candidates := candidates
    io.in.bits.offset := currTree
    io.in.valid := activeClassification
  }

  when (state === s_busy && currTree === numTrees) {
    state := s_count
    majorityVoter.io.in.valid := true.B
    majorityVoter.io.in.bits := decisions
  }

  // TODO: Check ready of majority voter
  when (state === s_count && majorityVoter.io.out.fire) {
    decision := majorityVoter.io.out.bits.classification
    // TODO: Add handling for no clear majority
    error := 0.U
    decisionValid := true.B
    state := s_done
    activeClassification := false.B
  }

  io.out.ready := true.B
  when(io.out.fire) {
    decisions(currTree) := io.out.bits.classes
    errors(currTree) := io.out.bits.error
    currTree := currTree + 1.U
  }

  when (io.out.fire && io.out.bits.error =/= 0.U) {
    decisionValid := true.B
    activeClassification := false.B
    state := s_done
    error := io.out.bits.error
  }

  when(resetDecision) {
    decisionValid := false.B
    state := s_idle
    currTree := 0.U
  }
  
}
