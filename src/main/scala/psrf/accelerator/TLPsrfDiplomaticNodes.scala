package psrf.accelerator

import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.{AddressSet, IdRange, LazyModule, LazyModuleImp}
import freechips.rocketchip.tilelink.{TLBuffer, TLFragmenter, TLIdentityNode, TLRAM, TLXbar}

class TLDecisionTreeWithScratchpad(val csrAddress: AddressSet,
  val scratchpadAddress: AddressSet,
  val beatBytes: Int = 4,
  val blockBytes: Int = 4,
  val id: IdRange = IdRange(0, 1),
  val aligned: Boolean = false
)(implicit p: Parameters) extends LazyModule {
  val node = TLIdentityNode()
  val mmio = LazyModule(new TLRandomForestMMIO(csrAddress, scratchpadAddress, beatBytes)(p))

  val ram = TLRAM(
    address = scratchpadAddress,
    executable = false,
    beatBytes = beatBytes,
    devName = Some("psrf,scratchpad"),
  )
  val xbar = TLXbar()

  ram := TLFragmenter(beatBytes, beatBytes * beatBytes) := TLBuffer() := xbar
  mmio.node := TLFragmenter(beatBytes, beatBytes * beatBytes) := TLBuffer() := xbar
  xbar := node

  lazy val module = new LazyModuleImp(this)
}
