package psrf.accelerator

import chipsalliance.rocketchip.config.{Config, Field}
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule}
import freechips.rocketchip.subsystem.BaseSubsystem
import psrf.params.{DecisionTreeConfig, DecisionTreeConfigKey, FixedPointBinaryPoint, FixedPointWidth}

case class TLRandomForestConfig(
  val csrAddress: AddressSet,
  val scratchpadAddress: AddressSet,
)

case object TLRandomForestKey extends Field[Option[TLRandomForestConfig]](None)

trait CanHaveTLRandomForestWithScratchpad { this: BaseSubsystem =>
  private val portName = "tlpsrf"
  val node = p(TLRandomForestKey) match {
    case Some(params) => {
      val psrf = LazyModule(new TLDecisionTreeWithScratchpad(params.csrAddress, params.scratchpadAddress, pbus.beatBytes, pbus.blockBytes)(p))
      pbus.coupleTo(portName) {
        psrf.node :=
        _
      }

      pbus.fromPort(Some("tlpsrf-master"))() := psrf.mmio.tlMaster.psrfMaster

      Some(psrf)
    }
    case None => None
  }
}

class WithTLRandomForest(
  csrAddress: AddressSet,
  scratchpadAddress: AddressSet,
  fixedPointWidth: Int = 32,
  fixedPointBinaryPoint: Int = 16,
) extends Config((site, here, up) => {
  case TLRandomForestKey => Some(TLRandomForestConfig(csrAddress, scratchpadAddress))
  case FixedPointWidth => fixedPointWidth
  case FixedPointBinaryPoint => fixedPointBinaryPoint
  case DecisionTreeConfigKey => DecisionTreeConfig(
    maxFeatures = 2,
    maxNodes = 10,
    maxClasses = 10,
    maxDepth = 10
  )
})
