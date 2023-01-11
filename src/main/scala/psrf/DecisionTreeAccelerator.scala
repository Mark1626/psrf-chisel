package psrf

import chisel3._
import chisel3.experimental.FixedPoint
import config.{Field, Parameters, Config}
import dspblocks._
import dsptools.numbers._
import freechips.rocketchip.amba.axi4stream.AXI4StreamIdentityNode
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule, LazyModuleImp}
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.tilelink.{TLBundle, TLClientPortParameters, TLEdgeIn, TLEdgeOut, TLFIFOFixer, TLManagerPortParameters}

case class StreamingDecisionTreeAccParams(
  writeAddress: BigInt = 0x2000,
  readAddress: BigInt = 0x2100,
  depth: Int,
  p: Parameters
) extends HasDecisionTreeParameters

case object StreamingDecisionTreeAccKey extends Field[Option[StreamingDecisionTreeAccParams]](None)

class StreamingDecisionTreeAccBundle(w: Int, bp: Int) extends Bundle {
  val data = FixedPoint(w.W, bp.BP)
}

object StreamingDecisionTreeAccBundle {
  def apply(w: Int, bp: Int): StreamingDecisionTreeAccBundle = new StreamingDecisionTreeAccBundle(w, bp)
}

trait StreamingDecisionTreeIO extends Bundle {
  val busy = Output(Bool())
}

abstract class StreamingDecisionTreeBlock[D, U, EO, EI, B<:Data, T<:Data:Ring]
(
  proto: T
)(implicit p: Parameters) extends DspBlock[D, U, EO, EI, B] {
  val streamNode = AXI4StreamIdentityNode()
  val mem = None
  def params: StreamingDecisionTreeAccParams

  lazy val module = new LazyModuleImp(this) {

    require(streamNode.in.length == 1)
    require(streamNode.out.length == 1)

    val in = streamNode.in.head._1
    val out = streamNode.out.head._1

    val decisionTree = Module(new DecisionTreeChiselModule()(params.p))
    val features = Reg(Vec(params.numFeatures, proto.cloneType))

    when (in.valid) {
      features(0) := in.bits.data.asTypeOf(StreamingDecisionTreeAccBundle(params.fixedPointWidth, params.fixedPointBinaryPoint).data)
      for (i <- 1 until params.numFeatures) {
        features(i) := features(i-1)
      }
    }

    in.ready := decisionTree.io.in.ready
    decisionTree.io.in.valid := in.bits.last && in.valid

    for (i <- 0 until params.numFeatures) {
      decisionTree.io.in.bits(i) := features(i)
    }

    decisionTree.io.out.ready := out.ready
    out.valid := decisionTree.io.out.valid

    out.bits.data := decisionTree.io.out.bits.asUInt
  }
}

class TLStreamingDecisionTreeBlock[T<:Data:Ring]
(
  val proto: T,
  val params: StreamingDecisionTreeAccParams
)(implicit p: Parameters) extends
  StreamingDecisionTreeBlock[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle, T](proto)
  with TLDspBlock

class TLStreamingDecisionTreeChain[T<:Data:Ring](proto: T, params: StreamingDecisionTreeAccParams)(implicit p: Parameters)
  extends TLChain(Seq(
    TLWriteQueueWithLast(params.depth, AddressSet(params.writeAddress, 0xff))(_),
    { implicit p: Parameters =>
      val mod = LazyModule(new TLStreamingDecisionTreeBlock(proto, params))
      mod
    },
    TLReadQueue(params.depth, AddressSet(params.readAddress, 0xff))(_)
  ))

trait CanHavePeripheryStreamingDecisionTree { this: BaseSubsystem =>
  val peripheral = p(StreamingDecisionTreeAccKey) match {
    case Some(params) => {
      val chain = LazyModule(new TLStreamingDecisionTreeChain(
        proto=FixedPoint(params.fixedPointWidth.W, params.fixedPointBinaryPoint.BP),
        params=params))
      pbus.toVariableWidthSlave(Some("streamingtree")) { chain.mem.get := TLFIFOFixer() }
      Some(chain)
    }
    case None => None
  }
}

class WithStreamingDecisionTree(p: Parameters) extends Config((site, here, up) => {
  case StreamingDecisionTreeAccKey => Some(StreamingDecisionTreeAccParams(depth = 16, p=p))
})
