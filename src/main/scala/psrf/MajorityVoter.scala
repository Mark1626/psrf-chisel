package psrf

import chisel3._
import chisel3.util._

abstract class MajorityVoterModule(numTrees: Int, numClasses: Int) extends Module {
  val classIndexWidth = log2Ceil(numClasses)
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(Vec(numTrees, UInt(classIndexWidth.W))))
    val out = Irrevocable(new Bundle {
      val classification  = UInt(classIndexWidth.W)
      val noClearMajority = Bool()
    })
  })
}

class MajorityVoterArea(numTrees: Int, numClasses: Int) extends MajorityVoterModule(numTrees, numClasses) {
  if (numClasses == 2) {
    val countWidth     = log2Ceil(numTrees)
    val countThreshold = math.ceil(numTrees.toDouble / 2).toInt.U(countWidth.W)

    val pipeEnq   = Wire(Decoupled(UInt(countWidth.W)))
    val pipeQueue = Queue(pipeEnq, 1)
    val count     = PopCount(io.in.bits.map(_.asBool))

    pipeEnq.valid := io.in.valid
    io.in.ready   := pipeEnq.ready
    pipeEnq.bits  := count

    pipeQueue.ready             := io.out.ready
    io.out.valid                := pipeQueue.valid
    io.out.bits.classification  := pipeQueue.bits > countThreshold
    io.out.bits.noClearMajority := pipeEnq.bits === countThreshold
  } else {
    val idle :: busy :: done :: Nil = Enum(3)
    val count :: compare :: Nil     = Enum(2)

    val state     = RegInit(idle)
    val busyState = RegInit(count)
    val start     = io.in.valid & io.in.ready
    val rest      = io.out.valid & io.out.ready

    val decisions       = Reg(Vec(numTrees, UInt(classIndexWidth.W)))
    val voteCount       = Reg(Vec(numClasses, UInt(log2Ceil(numTrees).W)))
    val maxClass        = Reg(UInt(log2Ceil(numClasses).W))
    val noClearMajority = RegInit(false.B)

    val decisionInputCountCond                       = WireDefault(false.B)
    val (decisionInputCount, decisionInputCountWrap) = Counter(decisionInputCountCond, numTrees)

    val classCountCond               = WireDefault(false.B)
    val (classCount, classCountWrap) = Counter(classCountCond, numClasses)

    io.in.ready  := false.B
    io.out.valid := false.B
    io.out.bits  := 0.U

    // FSM
    switch(state) {
      is(idle) {
        io.in.ready := true.B
        when(start) {
          state     := busy
          busyState := count
          decisions := io.in.bits
        }
      }
      is(busy) {
        switch(busyState) {
          is(count) {
            decisionInputCountCond := true.B
            val currClassIndex = decisions(decisionInputCount)
            voteCount(currClassIndex) := voteCount(currClassIndex) + 1.U
            when(decisionInputCountWrap) {
              busyState := compare
            }
          }
          is(compare) {
            classCountCond := true.B
            when(classCount === 0.U) {
              maxClass        := 0.U
              noClearMajority := false.B
            }.elsewhen(voteCount(classCount) > voteCount(maxClass)) {
              maxClass        := classCount
              noClearMajority := false.B
            }.elsewhen(voteCount(classCount) === voteCount(maxClass)) {
              noClearMajority := true.B
            }
            when(classCountWrap) {
              state := done
            }
          }
        }
      }
      is(done) {
        io.out.valid                := true.B
        io.out.bits.classification  := maxClass
        io.out.bits.noClearMajority := noClearMajority
        when(rest) {
          state := idle
        }
      }
    }
  }
}
