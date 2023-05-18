package psrf.modules

import chisel3._
import org.chipsalliance.cde.config.{Config, Parameters}
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import psrf.params.{DecisionTreeConfig, DecisionTreeConfigKey, FixedPointBinaryPoint, FixedPointWidth, MaxTrees}

class RandomForestNodeHelper(dut: RandomForestNodeModule) {
  def handleReq(node: TreeNodeLit): Unit = {
    handleReq(node.toBinary)
  }

  def handleReq(value: BigInt): Unit = {
    dut.clock.step()
    dut.busResp.ready.expect(true.B) //bus_resp_wait
    dut.busResp.valid.poke(true.B)
    dut.busResp.bits.poke(value.U(64.W))
    dut.clock.step()
  }
}

class RandomForestNodeModuleSpec extends AnyFlatSpec with ChiselScalatestTester {
  val oneTreeParams: Parameters = new Config((site, here, up) => {
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
  val twoTreeParams: Parameters = new Config((site, here, up) => {
    case FixedPointWidth => Constants.fpWidth
    case FixedPointBinaryPoint => Constants.bpWidth
    case DecisionTreeConfigKey => DecisionTreeConfig(
      maxFeatures = 2,
      maxNodes = 10,
      maxClasses = 10,
      maxDepth = 10
    )
    case MaxTrees => 2
  })

  def init(dut: RandomForestNodeModule): Unit = {
    dut.io.in.valid.poke(true.B)
    dut.io.in.ready.expect(true.B)

    dut.io.out.ready.poke(true.B)
    dut.io.out.valid.expect(false.B)

    dut.clock.step()

    dut.busReq.ready.poke(true.B)
    dut.busReq.valid.expect(false.B)

    dut.busResp.valid.poke(true.B)
    dut.busResp.ready.expect(false.B)

    dut.clock.step()
  }
  // TODO: Test is not working as expected
  it should "return candidate when at leaf node" in {
    test(new RandomForestNodeModule(0x2000, 0xfff, 4)(twoTreeParams))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        val helper = new RandomForestNodeHelper(dut)

        val inCandidates = Seq(0.5, 2)
        val treeNode = TreeNodeLit(1, 2, Helper.toFixedPoint(3.75, Constants.bpWidth), 0, 0)

        // TODO: This only works when size of Vec is equal to maxFeatures
        val candidate = inCandidates.asFixedPointVecLit(
          twoTreeParams(FixedPointWidth).W,
          twoTreeParams(FixedPointBinaryPoint).BP)

        dut.io.in.ready.expect(true.B)  //idle
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.offset.poke(0.U)
        dut.io.in.bits.candidates.poke(candidate)
        dut.clock.step()

        dut.busResp.ready.expect(false.B) //bus_req_wait
        dut.busReq.ready.poke(true.B)
        dut.clock.step()

        dut.busReq.valid.expect(true.B) //bus_req
        dut.busReq.bits.expect(0x2000)
        dut.busReqDone.poke(true.B)

        helper.handleReq(treeNode)

        dut.busReq.ready.poke(true.B) //bus_req_wait to bus_req
        dut.busReqDone.poke(true.B) //bus_req to bus_resp_wait
        dut.clock.step()

        helper.handleReq(treeNode)

        dut.io.out.bits.error.expect(1.U)
        dut.io.out.valid.expect(true.B)
        dut.io.out.bits.classes.expect(2.U)

      }
  }
  // TODO: Test is not working as expected
  it should "go to left node and give correct decision" in {
    test(new RandomForestNodeModule(0x2000, 0xfff, 3)(twoTreeParams))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        val helper = new RandomForestNodeHelper(dut)

        val inCandidates = Seq(0.5, 2)
        val treeNode0 = TreeNodeLit(0, 0, Helper.toFixedPoint(0.5, Constants.bpWidth), 1, 2)
        val treeNode1 = TreeNodeLit(1, 2, Helper.toFixedPoint(0, Constants.bpWidth), -1, -1)

        // TODO: This only works when size of Vec is equal to maxFeatures
        val candidate = inCandidates.asFixedPointVecLit(
          twoTreeParams(FixedPointWidth).W,
          twoTreeParams(FixedPointBinaryPoint).BP)

        dut.io.in.ready.expect(true.B) //idle
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.offset.poke(0.U)
        dut.io.in.bits.candidates.poke(candidate)
        dut.clock.step()

        dut.busResp.ready.expect(false.B) //bus_req_wait
        dut.busReq.ready.poke(true.B)
        dut.clock.step()

        dut.busReq.valid.expect(true.B) //bus_req
        dut.busReq.bits.expect(0x2000)
        dut.busReqDone.poke(true.B)
//        helper.handleReq(0x2400)
//
//        dut.busReq.bits.expect(0x2400)
//        dut.busReq.ready.poke(true.B) //bus_req_wait to bus_req
//        dut.busReqDone.poke(true.B) //bus_req to bus_resp_wait
//        dut.clock.step()

        helper.handleReq(treeNode0)

        dut.busReq.ready.poke(true.B) //bus_req_wait to bus_req
        dut.busReqDone.poke(true.B) //bus_req to bus_resp_wait
        dut.clock.step()

        helper.handleReq(treeNode1)

        dut.io.out.bits.error.expect(1.U)
        dut.io.out.valid.expect(true.B)
        dut.io.out.bits.classes.expect(2.U)
        dut.clock.step()
        dut.io.out.ready.poke(true.B)

      }
  }
  // TODO: Test is not working as expected
  it should "go to right node and give correct decision" in {
    test(new RandomForestNodeModule(0x2000, 0xfff, 3)(twoTreeParams))
      .withAnnotations(Seq(TreadleBackendAnnotation)) { dut =>

        val helper = new RandomForestNodeHelper(dut)

        val inCandidates = Seq(1.5, 2)
        val treeNode0 = TreeNodeLit(0, 0, Helper.toFixedPoint(0.5, Constants.bpWidth), 1, 2)
        val treeNode1 = TreeNodeLit(1, 3, Helper.toFixedPoint(0, Constants.bpWidth), -1, -1)

        // TODO: This only works when size of Vec is equal to maxFeatures
        val candidate = inCandidates.asFixedPointVecLit(
          twoTreeParams(FixedPointWidth).W,
          twoTreeParams(FixedPointBinaryPoint).BP)

        dut.io.in.ready.expect(true.B) //idle
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.offset.poke(0.U)
        dut.io.in.bits.candidates.poke(candidate)
        dut.clock.step()

        dut.busResp.ready.expect(false.B) //bus_req_wait
        dut.busReq.ready.poke(true.B)
        dut.clock.step()

        dut.busReq.valid.expect(true.B) //bus_req
        dut.busReq.bits.expect(0x2000)
        dut.busReqDone.poke(true.B)

//        helper.handleReq(0x2400)
//
//        dut.busReq.bits.expect(0x2400)
//        dut.busReq.ready.poke(true.B) //bus_req_wait to bus_req
//        dut.busReqDone.poke(true.B) //bus_req to bus_resp_wait
//        dut.clock.step()

        helper.handleReq(treeNode1)

        dut.busReq.ready.poke(true.B) //bus_req_wait to bus_req
        dut.busReqDone.poke(true.B) //bus_req to bus_resp_wait
        dut.clock.step()

        helper.handleReq(treeNode1)

        dut.io.out.valid.expect(true.B)
        dut.io.out.bits.classes.expect(3.U)

      }
  }

  it should "be able to wrap when maximum depth is reached " in {
      test(new RandomForestNodeModule(0x2000, 0xfff, 4)(oneTreeParams))
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

          init(dut)

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

    it should "get error when node address exceeds scratchpad address" in {
      test(new RandomForestNodeModule(0x2000, 0xfff, 4)(oneTreeParams))
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

          init(dut)

          dut.busReq.ready.poke(true.B)
          dut.busReq.valid.expect(true.B)
          dut.busReqDone.poke(true.B)
          dut.clock.step(2)

          dut.busResp.bits.poke(300000.U(64.W))
          dut.busResp.valid.poke(true.B)
          dut.clock.step(2)

          dut.busReq.ready.poke(true.B)
          dut.busReqDone.poke(true.B)
          dut.clock.step(2)

          dut.io.out.bits.error.expect(1.U(32.W))
        }
    }

}

