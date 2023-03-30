package psrf.params

import chipsalliance.rocketchip.config.{Field, Parameters}

case object RAMBlockSize extends Field[Int]
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