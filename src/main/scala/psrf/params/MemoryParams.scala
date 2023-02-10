package psrf.params

import chipsalliance.rocketchip.config.{Field, Parameters}
import chisel3.util.log2Ceil

case object RAMBlockSize extends Field[Int]
case object DataWidth extends Field[Int]
case object ReadBuffer extends Field[Int]

trait RAMBankParams {
  implicit val p: Parameters
  val ramSize = p(RAMBlockSize)
  val dataWidth = p(DataWidth)
  val readBankSize = p(ReadBuffer)

  val addrWidth = log2Ceil(ramSize)
}

case object BusWidth extends Field[Int]

trait BusParams {
  implicit val p: Parameters
  val busWidth = p(BusWidth)
}

case object RAMSize extends Field[Int]

trait RAMParams {
  implicit val p: Parameters
  val ramSize = p(RAMSize)
}
