package psrf.params

import chipsalliance.rocketchip.config.{Field, Parameters}

case object DataWidth extends Field[Int]
case object RAMSize extends Field[Int]

trait RAMParams {
  implicit val p: Parameters
  val ramSize = p(RAMSize)
  val dataWidth = p(DataWidth)
}
