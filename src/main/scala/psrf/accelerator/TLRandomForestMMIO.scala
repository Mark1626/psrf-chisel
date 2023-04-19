package psrf.accelerator

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import chisel3.experimental.FixedPoint
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule, LazyModuleImp}
import freechips.rocketchip.regmapper.{RegField, RegFieldDesc, RegisterRouter, RegisterRouterParams}
import freechips.rocketchip.tilelink.HasTLControlRegMap
import psrf.modules.RandomForestMMIOModule
import psrf.params.{DecisionTreeConfigKey, HasDecisionTreeParams}

class Candidate()(implicit val p: Parameters) extends Bundle with HasDecisionTreeParams {
  val data = FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP)
}

abstract class RandomForestMMIO(
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
    with HasDecisionTreeParams
{
  // TODO: Should I just mark this as 64bits
  val dataWidth = beatBytes * 8
  val tlMaster = LazyModule(new TLRandomForestNode(scratchpadAddress, beatBytes = beatBytes)(p))

  lazy val module = new LazyModuleImp(this) { outer =>
    val config = p(DecisionTreeConfigKey)
    val impl = tlMaster.module

    val decisionValid = Wire(Bool())

    val error = Wire(UInt(2.W))
    val decision = Wire(UInt(32.W))

    val mmioHandler = Module(new RandomForestMMIOModule()(p))

    mmioHandler.io <> impl.io
    decisionValid := mmioHandler.decisionValidIO
    decision := mmioHandler.decisionIO
    error := mmioHandler.errorIO

    mmioHandler.candidateData.valid := false.B
    mmioHandler.candidateData.bits := DontCare
    mmioHandler.resetDecision := false.B

    def handleCandidate(valid: Bool, data: UInt): Bool = {
      when (valid) {
        mmioHandler.candidateData.valid := true.B
        mmioHandler.candidateData.bits := data
      }
      mmioHandler.candidateData.ready
    }

    def handleResult(ready: Bool): (Bool, UInt) = {
      when (ready) {
        mmioHandler.resetDecision := true.B
      }

      (ready && decisionValid, Cat(error, decision))
    }

    val csr = Cat(0.U(62.W), impl.io.busy, decisionValid)

    regmap(
      beatBytes * 0 -> Seq(RegField.r(dataWidth, csr, RegFieldDesc(name="csr", desc="Control Status Register"))),
      beatBytes * 1 -> Seq(RegField.w(dataWidth, handleCandidate(_, _), RegFieldDesc(name="candidate-in", desc="Port for passing candidates"))),
      beatBytes * 2 -> Seq(RegField.r(dataWidth, handleResult(_), RegFieldDesc(name="decision", desc="Result of a classification")))
    )
  }
}

class TLRandomForestMMIO(
  csrAddress: AddressSet,
  scratchpadAddress: AddressSet,
  beatBytes: Int
)(implicit p: Parameters) extends RandomForestMMIO(csrAddress, scratchpadAddress, beatBytes)(p)
  with HasTLControlRegMap
