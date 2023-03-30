package psrf.modules

// TODO: Should this be in src
case class TreeNodeLit(
  leaf: Int,
  featureIndex: Int,
  threshold: Long,
  leftNode: Int,
  rightNode: Int
) {

  def toBinary: BigInt = {
    def twosCompliment(x: Int): Int = { if (x < 0) ((1<<11 - 1) - x) else x }

    val left = twosCompliment(leftNode)
    val right = twosCompliment(rightNode)

    val rawbin = "%1s%9s%32s%11s%11s".format(
      leaf.toBinaryString,
      featureIndex.toBinaryString,
      threshold.toBinaryString,
      left.toBinaryString,
      right.toBinaryString
    )

    BigInt(rawbin.replace(' ', '0'), 2)
  }
}

object Helper {
  def toFixedPoint(x: Double, scale: Long): Long = {
    val BP_SCALE = 1L << scale
    val xv = x * BP_SCALE
    if (xv < 0.0) {
      (xv - 0.5).toLong
    } else {
      (xv + 0.5).toLong
    }
  }
}

object Constants {
  val fpWidth = 32
  val bpWidth = 16
  val CSR_ADDR: Long = 0x00L
  val MODE_CHANGE: Long = 0x08L
  val CANDIDATE_IN: Long = 0x10L
  val DECISION_ADDR: Long = 0x19L
  val WEIGHTS_IN: Long = 0x20L
  val WEIGHTS_OUT: Long = 0x28L
  val OPERATIONAL_STATE = 1;
  val WE_WEIGHTS_STATE = 0;
}
