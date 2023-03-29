package psrf.accelerator

import chisel3._
import chisel3.util._
import chisel3.SyncReadMem
import chisel3.util.Irrevocable
import freechips.rocketchip.amba.AMBACorrupt
import freechips.rocketchip.amba.axi4.{AXI4Buffer, AXI4Bundle, AXI4BundleParameters, AXI4Parameters, AXI4RAM, AXI4SlaveNode, AXI4SlaveParameters, AXI4SlavePortParameters}
import freechips.rocketchip.config.{Config, Field, Parameters}
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule, LazyModuleImp, RegionType, Resource, SimpleDevice, TransferSizes}
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.tilelink.{TLFragmenter, TLToAXI4}
import freechips.rocketchip.util.BundleMap
import psrf.modules.{HasVariableDecisionTreeParams, RFTile}
import psrf.params.RAMSize

case class AXIRandomForestParams(
                             addressSet: AddressSet
                           )

case object AXIRandomForestKey extends Field[Option[AXIRandomForestParams]](None)

trait CanHavePeripheryAXIRandomForest { this: BaseSubsystem =>
  private val portName = "device"

  val ram = p(AXIRandomForestKey) match {
    case Some(params) => {
      val axiNode = LazyModule(new AXI4RandomForest(address = params.addressSet, beatBytes = pbus.beatBytes))
      //val axiNode = LazyModule(new AXI4RAM(address = params.addressSet, beatBytes = pbus.beatBytes))
      //pbus.coupleFrom(Some(Seq("abc"))) () :=
      //pbus.fromPort(Some("init-zero"))() :=
      pbus.toSlave(Some(portName)) {
        axiNode.node :=
          AXI4Buffer() :=
          TLToAXI4() :=
          TLFragmenter(pbus.beatBytes, pbus.blockBytes, holdFirstDeny = true)
      }
      Some(axiNode)
    }
    case None => None
  }
}

class WithAXIRandomForest(addressSet: AddressSet) extends Config((site, here, up) => {
  case AXIRandomForestKey => Some(AXIRandomForestParams(addressSet))
  case RAMSize => 1024
})

class AXI4RandomForest(
                   val address: AddressSet,
                   cacheable: Boolean = false,
                   executable: Boolean = false,
                   val beatBytes: Int = 8,
                   devName: Option[String] = None,
                   errors: Seq[AddressSet] = Nil,
                   wcorrupt: Boolean = true)
                 (implicit p: Parameters) extends LazyModule {
  val device = new SimpleDevice("axirf", Seq("axi,rf"))
  val resources = device.reg

  val node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
      address = List(address) ++ errors,
      resources = resources,
      regionType = if (cacheable) RegionType.UNCACHED else RegionType.IDEMPOTENT,
      executable = executable,
      supportsRead = TransferSizes(1, beatBytes),
      supportsWrite = TransferSizes(1, beatBytes),
      interleavedId = Some(0)
    )),
    beatBytes = beatBytes,
    requestKeys = if (wcorrupt) Seq(AMBACorrupt) else Seq(),
    minLatency = 1
  )))

  private val outer = this

  lazy val module = new AXI4RandomForestImp(this)
}

class AXI4RandomForestImp(outer: AXI4RandomForest) extends LazyModuleImp(outer) {
  val (in, edgeIn) = outer.node.in(0)

  val laneDataBits = 8
  val address = outer.address
  val beatBytes = outer.beatBytes

  def bigBits(x: BigInt, tail: List[Boolean] = Nil): List[Boolean] =
    if (x == 0) tail.reverse else bigBits(x >> 1, ((x & 1) == 1) :: tail)

  def mask: List[Boolean] = bigBits(address.mask >> log2Ceil(beatBytes))

  val tile = Module(new RFTile()(p))

  val r_addr = Cat((mask zip (in.ar.bits.addr >> log2Ceil(beatBytes)).asBools).filter(_._1).map(_._2).reverse)
  val w_addr = Cat((mask zip (in.aw.bits.addr >> log2Ceil(beatBytes)).asBools).filter(_._1).map(_._2).reverse)
  val r_sel0 = address.contains(in.ar.bits.addr)
  val w_sel0 = address.contains(in.aw.bits.addr)

  val w_full = RegInit(false.B)
  val w_id = Reg(UInt())
  val w_echo = Reg(BundleMap(in.params.echoFields))
  val r_sel1 = RegInit(r_sel0)
  val w_sel1 = RegInit(w_sel0)

  when(in.b.fire) {
    w_full := false.B
  }
  when(in.aw.fire) {
    w_full := true.B
  }

  when(in.aw.fire) {
    w_id := in.aw.bits.id
    w_sel1 := w_sel0
    w_echo :<= in.aw.bits.echo
  }

  val wdata = in.w.bits.data
  val wen = in.aw.fire && w_sel0
  val (wresp, wreq_ready, wresp_valid) = tile.write(w_addr, wdata, wen, in.b.ready)

  in.b.valid := w_full && wresp_valid
  in.aw.ready := in.w.valid && (in.b.ready || !(w_full && wreq_ready))
  in.w.ready := in.aw.valid && (in.b.ready || !(w_full && wreq_ready))

  in.b.bits.id := w_id
  in.b.bits.resp := Mux(w_sel1, AXI4Parameters.RESP_OKAY, AXI4Parameters.RESP_DECERR)
  in.b.bits.echo :<= w_echo

  val r_full = RegInit(false.B)
  val r_id = Reg(UInt())
  val r_echo = Reg(BundleMap(in.params.echoFields))

  when(in.r.fire) {
    r_full := false.B
  }
  when(in.ar.fire) {
    r_full := true.B
  }

  when(in.ar.fire) {
    r_id := in.ar.bits.id
    r_sel1 := r_sel0
    r_echo :<= in.ar.bits.echo
  }

  val (rdata, rreq_ready, rresp_valid) = tile.read(r_addr, in.ar.fire, in.r.ready)

  in.r.valid := r_full && rresp_valid
  in.ar.ready := in.r.ready || !(r_full && rreq_ready)

  in.r.bits.id := r_id

  in.r.bits.resp := Mux(r_sel1, AXI4Parameters.RESP_OKAY, AXI4Parameters.RESP_DECERR)
  in.r.bits.data := rdata
  in.r.bits.echo :<= r_echo
  in.r.bits.last := true.B
}

object AXI4RandomForest
{
  def apply(
             address: AddressSet,
             cacheable: Boolean = true,
             executable: Boolean = true,
             beatBytes: Int = 4,
             devName: Option[String] = None,
             errors: Seq[AddressSet] = Nil,
             wcorrupt: Boolean = true)(implicit p: Parameters) =
  {
    val axi4rf = LazyModule(new AXI4RandomForest(
      address, cacheable, executable, beatBytes, devName, errors, wcorrupt
    ))
    axi4rf.node
  }
}
