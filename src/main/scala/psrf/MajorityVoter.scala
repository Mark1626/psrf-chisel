package psrf

import chisel3._
import chisel3.util._

class MajorityVoter(numTrees: Int) extends Module {
  val countWidth     = log2Ceil(numTrees)
  val countThreshold = math.ceil(numTrees.toDouble / 2).toInt.U(countWidth.W)

  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(Vec(numTrees, Bool())))
    val out = Irrevocable(Bool())
  })

  val pipeEnq   = Wire(Decoupled(UInt(countWidth.W)))
  val pipeQueue = Queue(pipeEnq, 1)
  val count     = PopCount(io.in.bits)

  pipeEnq.valid := io.in.valid
  io.in.ready   := pipeEnq.ready
  pipeEnq.bits  := count

  val majorityClassification = pipeQueue.bits >= countThreshold

  pipeQueue.ready := io.out.ready
  io.out.valid    := pipeQueue.valid
  io.out.bits     := majorityClassification
}
