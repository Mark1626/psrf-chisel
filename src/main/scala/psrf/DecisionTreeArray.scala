package psrf

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import config.{Field, Parameters}

case object TreeLiterals extends Field[List[List[DecisionTreeNodeLit]]]

trait HasDecisionTreeArrayParameters extends HasRandomForestParameters {
  val treeLiterals = p(TreeLiterals)
}

class DecisionTreeArraySimple(implicit val p: Parameters) extends Module with HasDecisionTreeArrayParameters {
  import p._
  require(treeLiterals.length == numTrees, "Number of tree ROMs provided does not match number of trees")

  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(Vec(numFeatures, FixedPoint(fixedPointWidth.W, fixedPointBinaryPoint.BP))))
    val out = Irrevocable(Vec(numTrees, UInt(classIndexWidth.W)))
  })

  val decisionTrees = treeLiterals.map(tl =>
    Module(
      new DecisionTree()(
        p.alterMap(
          Map(
            TreeLiteral -> tl
          )
        )
      )
    )
  )

  io.in.ready  := decisionTrees.foldLeft(true.B) { (r, tree) => WireDefault(r & tree.io.in.ready) }
  io.out.valid := decisionTrees.foldLeft(true.B) { (v, tree) => WireDefault(v & tree.io.out.valid) }

  decisionTrees
    .zip(io.out.bits)
    .foreach {
      case (t, o) => {
        t.io.in.valid  := io.in.valid
        t.io.out.ready := io.out.ready & io.out.valid
        t.io.in.bits   := io.in.bits
        o              := t.io.out.bits
      }
    }
}
