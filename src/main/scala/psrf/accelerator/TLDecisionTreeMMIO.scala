package psrf.accelerator

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import chisel3.experimental.FixedPoint
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule, LazyModuleImp}
import freechips.rocketchip.regmapper.{RegField, RegFieldDesc, RegisterRouter, RegisterRouterParams}
import freechips.rocketchip.tilelink.HasTLControlRegMap
import psrf.modules.Candidate
import psrf.params.{DecisionTreeConfigKey, HasDecisionTreeParams, HasVariableDecisionTreeParams}

abstract class DecisionTreeMMIO(
  csrAddress: AddressSet,
  scratchpadAddress: AddressSet,
  beatBytes: Int
)(implicit p: Parameters) extends RegisterRouter(
  RegisterRouterParams(
    name = "psrf",
    compat = Seq("com", "psrf"),
    base = csrAddress.base,
    size = csrAddress.mask + 1,
    beatBytes = beatBytes))
    with HasVariableDecisionTreeParams
{
  // TODO: Should I just mark this as 64bits
  val dataWidth = beatBytes * 8
  val tlMaster = LazyModule(new TLRandomForestNode(scratchpadAddress, beatBytes = beatBytes)(p))

  lazy val module = new LazyModuleImp(this) { outer =>
    val config = p(DecisionTreeConfigKey)
    val impl = tlMaster.module

    val idle :: busy :: done :: Nil = Enum(3)
    val state = RegInit(idle)

    val candidates = Reg(Vec(config.maxFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)))
    val decision = RegInit(0.U(32.W))
    val decisionValid = RegInit(false.B)
    val error = RegInit(0.U(2.W))

    //val candidateLast = Wire(Bool())
    val candidateLast = RegInit(false.B)

    def handleCandidate(valid: Bool, data: UInt): Bool = {
      when (valid) {
        val last = data(50)
        val candidateId = data(49, 32)
        val candidateValue = data(31, 0)
        candidates(0) := candidateValue.asTypeOf(new Candidate()(p).data)
        for (i <- 1 until config.maxFeatures) {
          candidates(i) := candidates(i - 1)
        }
        candidateLast := last
      }
      true.B
    }

    def handleResult(ready: Bool): (Bool, UInt) = {
//      when (ready) {
//
//      }

      (ready && decisionValid, Cat(error, decision))
    }

    when (candidateLast) {
      decisionValid := false.B
    }

    impl.io.in.valid := false.B
    when (impl.io.in.ready && !impl.io.busy) {
      impl.io.in.bits.candidates := candidates
      impl.io.in.bits.offset := 0.U // Tree 0
      impl.io.in.valid := candidateLast
      candidateLast := false.B
    }

    impl.io.out.ready := true.B
    when (impl.io.out.fire) {
      decision := impl.io.out.bits.classes
      error := impl.io.out.bits.error
      decisionValid := true.B
    }

    val csr = Cat(0.U(61.W), state === idle, impl.io.busy, decisionValid)

    regmap(
      beatBytes * 0 -> Seq(RegField.r(dataWidth, csr, RegFieldDesc(name="csr", desc="Control Status Register"))),
      beatBytes * 1 -> Seq(RegField.w(dataWidth, handleCandidate(_, _), RegFieldDesc(name="candidate-in", desc="Port for passing candidates"))),
      beatBytes * 2 -> Seq(RegField.r(dataWidth, handleResult(_), RegFieldDesc(name="decision", desc="Result of a classification")))
    )
  }
}

class TLDecisionTreeMMIO(
  csrAddress: AddressSet,
  scratchpadAddress: AddressSet,
  beatBytes: Int
)(implicit p: Parameters) extends DecisionTreeMMIO(csrAddress, scratchpadAddress, beatBytes)(p)
  with HasTLControlRegMap
