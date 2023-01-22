package psrf

import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.experimental.VecLiterals._
import chisel3.internal.firrtl.{BinaryPoint, Width}

package object modules {
  implicit class fromSeqDoubleToLiteral(s: Seq[Double]) {
    def asFixedPointVecLit(width: Width, binaryPoint: BinaryPoint): Vec[FixedPoint] =
      Vec.Lit(s.map(d => d.F(width, binaryPoint)): _*)
  }
}
