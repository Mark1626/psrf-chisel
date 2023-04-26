package psrf.modules

import chisel3._
import chipsalliance.rocketchip.config.{Config}
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import psrf.params.{DecisionTreeConfig, DecisionTreeConfigKey, FixedPointBinaryPoint, FixedPointWidth, MaxTrees}

class RandomForestNodeModuleSpec extends AnyFlatSpec with ChiselScalatestTester {
  val oneTreeParams = new Config((site, here, up) => {
    case FixedPointWidth => Constants.fpWidth
    case FixedPointBinaryPoint => Constants.bpWidth
    case DecisionTreeConfigKey => DecisionTreeConfig(
      maxFeatures = 2,
      maxNodes = 10,
      maxClasses = 10,
      maxDepth = 3
    )
    case MaxTrees => 1
  })

  it should "be able able to wrap when condition is reached" in {
    test(new RandomForestNodeModule(0x2000, 0xfff, 4)(oneTreeParams))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        dut.io.in.valid.poke(true.B)
        dut.io.in.ready.expect(true.B)

        dut.io.out.ready.poke(true.B)
        dut.io.out.valid.expect(false.B)

        dut.clock.step()

        dut.busResp.valid.poke(true.B)
        dut.busResp.ready.expect(false.B)

        dut.busReq.ready.poke(true.B)
        dut.busReq.valid.expect(false.B)

        dut.clock.step()

        dut.busReq.ready.poke(true.B)
        dut.busReqDone.poke(true.B)
        dut.clock.step(2)

        dut.busResp.bits.poke(0.U(64.W))
        dut.busResp.valid.poke(true.B)
        dut.clock.step(2)

        dut.busReq.ready.poke(true.B)
        dut.busReqDone.poke(true.B)
        dut.clock.step(2)

        dut.busResp.bits.poke(0.U(64.W))
        dut.busResp.valid.poke(true.B)
        dut.clock.step(2)

        dut.busReq.ready.poke(true.B)
        dut.busReqDone.poke(true.B)
        dut.clock.step(2)

        dut.busResp.bits.poke(0.U(64.W))
        dut.busResp.valid.poke(true.B)
        dut.clock.step(2)

        dut.io.out.bits.error.expect(2.U(32.W))
      }
  }
}
