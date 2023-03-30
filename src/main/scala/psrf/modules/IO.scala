package psrf.modules

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.util.{Decoupled, log2Ceil}
import psrf.params.HasVariableDecisionTreeParams

class ReadReq(val n: Int) extends Bundle {
  val addr = UInt(log2Ceil(n).W)
}

class ReadResp(val w: Int) extends Bundle {
  val data = UInt(w.W)
}

class ReadIO(val n: Int, val w: Int) extends Bundle {
  val req = Flipped(Decoupled(new ReadReq(n)))
  val resp = Decoupled(new ReadResp(w))
}

class WriteReq(val n: Int, val w: Int) extends Bundle {
  val addr = UInt(log2Ceil(n).W)
  val data = UInt(w.W)
}

class WriteResp() extends Bundle {
  val resp = Bool()
}

class WriteIO(val n: Int, val w: Int) extends Bundle {
  val en = Input(Bool())
  val req = Flipped(Decoupled(new WriteReq(n, w)))
  val resp = Decoupled(new WriteResp())
}

class ReadWriteIO(val n: Int, val w: Int) extends Bundle {
  val read = new ReadIO(n, w)
  val write = new WriteIO(n, w)
}

// TODO: The width of in interface should be reduced, we are assuming that
//  our features are going to be less. This potentially can be a Wishbone Slave
//
class TreeIO()(implicit val p: Parameters) extends Bundle with HasVariableDecisionTreeParams {
  val in = Flipped(Decoupled(new TreeInputBundle()))
  val out = Decoupled(UInt(9.W))
  val busy = Output(Bool())
}

