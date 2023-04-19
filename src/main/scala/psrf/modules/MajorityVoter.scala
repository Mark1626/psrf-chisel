package psrf.modules

import chipsalliance.rocketchip.config.{Parameters}
import chisel3._
import chisel3.util._
import psrf.params.HasRandomForestParams

trait HasMajorityVoterParams extends HasRandomForestParams

class MajorityVoterBundle()(implicit val p: Parameters) extends Bundle with HasMajorityVoterParams

class MajorityVoterOut()(implicit p: Parameters) extends MajorityVoterBundle {
  val classification = UInt(9.W)
  val noClearMajority = Bool()
}

class MajorityVoterIO()(implicit p: Parameters) extends MajorityVoterBundle {
  // TODO: Move this constant outside
  val in = Flipped(Decoupled(Vec(maxTrees, UInt(classIndexWidth.W))))
  val out = Irrevocable(new MajorityVoterOut()(p))
}

class MajorityVoterModule()(implicit val p: Parameters) extends Module with HasRandomForestParams {
  val io = IO(new MajorityVoterIO()(p))

  if (maxClasses == 2) {
    val countWidth = log2Ceil(maxTrees) + 1
    val countThreshold = math.ceil(maxTrees.toDouble / 2).toInt.U(countWidth.W)

    val pipeEnq = Wire(Decoupled(UInt(countWidth.W)))
    val pipeQueue = Queue(pipeEnq, 1)
    val count = PopCount(io.in.bits.map(_.asBool))

    pipeEnq.valid := io.in.valid
    io.in.ready := pipeEnq.ready
    pipeEnq.bits := count

    pipeQueue.ready := io.out.ready
    io.out.valid := pipeQueue.valid

    if (maxTrees % 2 == 0) {
      io.out.bits.classification := pipeQueue.bits > countThreshold
      io.out.bits.noClearMajority := pipeQueue.bits === countThreshold
    } else {
      io.out.bits.classification := pipeQueue.bits >= countThreshold
      io.out.bits.noClearMajority := false.B
    }
  } else {
    val idle :: busy :: done :: Nil = Enum(3)
    val count :: compare :: Nil = Enum(2)

    val state = RegInit(idle)
    val busyState = RegInit(count)
    val start = io.in.valid & io.in.ready
    val rest = io.out.valid & io.out.ready

    val decisions = Reg(Vec(maxTrees, UInt(classIndexWidth.W)))
    val voteCount = Reg(Vec(maxClasses, UInt((log2Ceil(maxTrees) + 1).W)))
    val maxClass = Reg(UInt(log2Ceil(maxClasses).W))
    val noClearMajority = RegInit(false.B)

    val decisionInputCountCond = WireDefault(false.B)
    val (decisionInputCount, decisionInputCountWrap) = Counter(decisionInputCountCond, maxTrees)

    val classCountCond = WireDefault(false.B)
    val (classCount, classCountWrap) = Counter(classCountCond, maxClasses)

    io.in.ready := false.B
    io.out.valid := false.B
    io.out.bits.classification := 0.U
    io.out.bits.noClearMajority := false.B

    // FSM
    switch(state) {
      is(idle) {
        io.in.ready := true.B
        voteCount := 0.U.asTypeOf(voteCount)
        when(start) {
          state := busy
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
              maxClass := 0.U
              noClearMajority := false.B
            }.elsewhen(voteCount(classCount) > voteCount(maxClass)) {
              maxClass := classCount
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
        io.out.valid := true.B
        io.out.bits.classification := maxClass
        io.out.bits.noClearMajority := noClearMajority
        when(rest) {
          state := idle
        }
      }
    }
  }

}
