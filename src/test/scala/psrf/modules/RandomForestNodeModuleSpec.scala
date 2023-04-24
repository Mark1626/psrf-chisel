package psrf.modules

import chisel3._
import chisel3.experimental.VecLiterals._
import chisel3.experimental.BundleLiterals._
import chisel3.util._
import chipsalliance.rocketchip.config.{Config, Parameters}
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
      maxDepth = 10
    )
    case MaxTrees => 1
  })

  it should "be able able to create the node" in {
    test(new RandomForestNodeModule()(oneTreeParams))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      }
  }
}
