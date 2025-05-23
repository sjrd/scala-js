/*
 * Scala.js (https://www.scala-js.org/)
 *
 * Copyright EPFL.
 *
 * Licensed under Apache License 2.0
 * (https://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

/* NOTICE -- The code in this file was adapted from code in libdivide,
 * licensed under the zlib license below.
 *
 * Copyright (C) 2010 - 2019 ridiculous_fish, <libdivide@ridiculousfish.com>
 * Copyright (C) 2016 - 2019 Kim Walisch, <kim.walisch@gmail.com>
 *
 * This software is provided 'as-is', without any express or implied
 * warranty.  In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package org.scalajs.linker.frontend.optimizer

import java.lang.{Long => JLong}
import java.math.BigInteger

import org.scalajs.ir.Position
import org.scalajs.ir.Names._
import org.scalajs.ir.OriginalName.{NoOriginalName => NON}
import org.scalajs.ir.Trees._
import org.scalajs.ir.Types._
import org.scalajs.ir.WellKnownNames._
import org.scalajs.ir.Trees.UnaryOp.LongToInt

private[optimizer] final class IntegerDivisions(useRuntimeLong: Boolean) {
  import IntegerDivisions._

  def makeOptimizedDivision[T](op: BinaryOp.Code, divisor: T)(
      implicit int: UnsignedIntegral[T], pos: Position): Tree = {
    import int.mkNumericOps

    val argRef = VarRef(NumeratorArgName)(int.tpe)

    val (isSigned, isQuotient) = op match {
      case BinaryOp.Int_/ | BinaryOp.Long_/                   => (true, true)
      case BinaryOp.Int_% | BinaryOp.Long_%                   => (true, false)
      case BinaryOp.Int_unsigned_/ | BinaryOp.Long_unsigned_/ => (false, true)
      case BinaryOp.Int_unsigned_% | BinaryOp.Long_unsigned_% => (false, false)
    }

    val negativeDivisor = isSigned && int.lt(divisor, int.zero)
    val absDivisor = if (negativeDivisor) -divisor else divisor

    if (absDivisor == int.zero) {
      UnaryOp(
        UnaryOp.Throw,
        New(
          ArithmeticExceptionClass,
          MethodIdent(MethodName.constructor(List(ClassRef(BoxedStringClass)))),
          List(StringLiteral("/ by zero"))
        )
      )
    } else if (absDivisor == int.one) {
      // The algorithms below assume absDivisor >= 2, so special-case 1
      if (isQuotient) {
        if (negativeDivisor)
          BinaryOp(int.Op_-, int.literalZero, argRef)
        else
          argRef
      } else {
        int.literalZero
      }
    } else if (int.isUnsignedPowerOf2(absDivisor)) {
      val k = int.unsignedFloorLog2(absDivisor) // i.e., absDivisor == 2^k

      if (isSigned) {
        // Sections 10-1 (quotient) and 10-2 (remainder)

        val t1 = BinaryOp(int.Op_>>, argRef, IntLiteral(k - 1))
        val t2 = BinaryOp(int.Op_>>>, t1, IntLiteral(int.bitSize - k))

        if (isQuotient) {
          val t3 = BinaryOp(int.Op_+, argRef, t2)
          val q = BinaryOp(int.Op_>>, t3, IntLiteral(k))

          if (negativeDivisor)
            BinaryOp(int.Op_-, int.literalZero, q)
          else
            q
        } else {
          val temp1Def = tempVarDef(temp1, t2)
          Block(
            temp1Def,
            // ((arg + temp1) & (2^k - 1)) - temp1
            BinaryOp(
              int.Op_-,
              BinaryOp(
                int.Op_&,
                BinaryOp(int.Op_+, argRef, temp1Def.ref),
                int.literal(absDivisor - int.one)
              ),
              temp1Def.ref
            )
          )
        }
      } else {
        // Unsigned is straightforward
        if (isQuotient)
          BinaryOp(int.Op_>>>, argRef, IntLiteral(k))
        else
          BinaryOp(int.Op_&, argRef, int.literal(absDivisor - int.one))
      }
    } else {
      val quotient: Tree = if (isSigned) {
        val data = computeSignedMagic(absDivisor, negativeDivisor)

        var q = int.genMulSignedHi(data.M, argRef)
        if (data.add != 0)
          q = BinaryOp(int.Op_+, q, argRef)
        q = BinaryOp(int.Op_>>, q, IntLiteral(data.shift))
        q = BinaryOp(int.Op_+, q, BinaryOp(int.Op_>>, argRef, IntLiteral(int.bitSize - 1)))

        if (negativeDivisor)
          BinaryOp(int.Op_-, int.literalZero, q)
        else
          q
      } else {
        val data = computeUnsignedMagic(absDivisor, negativeDivisor)

        var q = int.genMulUnsignedHi(data.M, argRef)
        ???
      }

      if (isQuotient) {
        quotient
      } else {
        // r = n - d*q
        BinaryOp(int.Op_-, argRef, BinaryOp(int.Op_*, int.literal(divisor), quotient))
      }
    }
  }
}

private[optimizer] object IntegerDivisions {
  /** Local argument name for the numerator, used by the generated code.
   *
   *  The optimizer should bind this name to the numerator in the scope of the
   *  code generated by `makeOptimizedDivision` (as if it had been inlined from
   *  a method taking `NumeratorArgName` as formal parameter).
   */
  val NumeratorArgName = LocalName("num")

  private val temp1 = LocalName("t")
  private val y0Name = LocalName("y0")
  private val y1Name = LocalName("y1")
  private val mulhiTempName = LocalName("mulht")

  private def tempVarDef(name: LocalName, rhs: Tree)(implicit pos: Position): VarDef =
    VarDef(LocalIdent(name), NON, rhs.tpe, mutable = false, rhs)

  /** Magic data, from which we derive the code to generate.
   *
   *  `add` is +1 if we need an addition, -1 for a subtraction, and 0 for none.
   */
  private[optimizer] final case class MagicData[T](M: T, add: Int, shift: Int)

  // private[optimizer] for unit tests
  private[optimizer] def computeSignedMagic[T](ad: T, negativeDivisor: Boolean)(
      implicit int: UnsignedIntegral[T]): MagicData[T] = {
    import int.mkNumericOps

    val two31 = int.one_<<(int.bitSize - 1)

    val t = two31 + (if (negativeDivisor) int.one else int.zero)
    val anc = t - int.one - (int.remainderUnsigned(t, ad)) // absolute value of nc

    var p = int.bitSize - 1 // init p
    var q1 = int.divideUnsigned(two31, anc) // init q1 = 2**p / |nc|
    var r1 = two31 - q1 * anc // init r1 = rem(2**p, |nc|)
    var q2 = int.divideUnsigned(two31, ad) // init q2 = 2**p / |d|
    var r2 = two31 - q2 * ad // init r2 = rem(2**p, |d|)

    while ({
      // do
      p += 1

      q1 = int.times2(q1)
      r1 = int.times2(r1)
      if (int.compareUnsigned(r1, anc) >= 0) {
        q1 += int.one
        r1 -= anc
      }

      q2 = int.times2(q2)
      r2 = int.times2(r2)
      if (int.compareUnsigned(r2, ad) >= 0) {
        q2 += int.one
        r2 -= ad
      }

      val delta = ad - r2
      // while
      (int.compareUnsigned(q1, delta) < 0 || (q1 == delta && r1 == int.zero))
    }) {}

    val M =
      if (negativeDivisor) -(q2 + int.one)
      else q2 + int.one
    val a =
      if (!negativeDivisor && int.lt(M, int.zero)) +1
      else if (negativeDivisor && int.gt(M, int.zero)) -1
      else 0
    val s = p - int.bitSize
    MagicData(M, a, s)
  }

  // private[optimizer] for unit tests
  private[optimizer] def computeUnsignedMagic[T](ad: T, negativeDivisor: Boolean)(
      implicit int: UnsignedIntegral[T]): MagicData[T] = {
    import int.mkNumericOps

    val two31 = int.one_<<(int.bitSize - 1)

    var add = 0

    val t = two31 + (if (negativeDivisor) int.one else int.zero)
    //val anc = t - int.one - (int.remainderUnsigned(t, ad)) // absolute value of nc
    val anc = -int.one - int.remainderUnsigned(-ad, ad)

    var p = int.bitSize - 1 // init p
    var q1 = int.divideUnsigned(two31, anc) // init q1 = 2**p / |nc|
    var r1 = two31 - q1 * anc // init r1 = rem(2**p, |nc|)
    var q2 = int.divideUnsigned(two31 - int.one, ad) // init q2 = (2**p - 1) / |d|
    var r2 = (two31 - int.one) - q2 * ad // init r2 = rem(2**p - 1, |d|)

    while ({
      // do
      p += 1

      if (int.compareUnsigned(r1, anc - r1) >= 0) {
        q1 = int.times2(q1) + int.one
        r1 = int.times2(r1) - anc
      } else {
        q1 = int.times2(q1)
        r1 = int.times2(r1)
      }

      if (int.compareUnsigned(r2 + int.one, ad - r2) >= 0) {
        if (int.compareUnsigned(q2, two31 - int.one) >= 0)
          add = 1
        q2 = int.times2(q2) + int.one
        r2 = int.times2(r2) + int.one - ad
      } else {
        if (int.compareUnsigned(q2, two31) >= 0)
          add = 1
        q2 = int.times2(q2)
        r2 = int.times2(r2) + int.one
      }

      val delta = ad - int.one - r2
      // while
      ((p < 2 * int.bitSize) && (int.compareUnsigned(q1, delta) < 0 || (q1 == delta && r1 == int.zero)))
    }) {}

    val M =
      if (negativeDivisor) throw new AssertionError("unreachable")
      else q2 + int.one
    val s = p - int.bitSize
    MagicData(M, add, s)
  }

  val IntDivRuntimeClass = ClassName("org.scalajs.linker.runtime.IntDivRuntime")

  private val ConfigParamRefs = List(IntRef, BooleanRef, BooleanRef, BooleanRef)
  private val IntDivParamRefs = IntRef :: IntRef :: IntRef :: ConfigParamRefs
  private val LongDivParamRefs = LongRef :: LongRef :: LongRef :: ConfigParamRefs

  val libdivide_u32_do_MethodName =
    MethodName("libdivide_u32_do", IntDivParamRefs, IntRef)

  val libdivide_s32_do_MethodName =
    MethodName("libdivide_s32_do", IntDivParamRefs, IntRef)

  val libdivide_u64_do_MethodName =
    MethodName("libdivide_u64_do", LongDivParamRefs, LongRef)

  val libdivide_s64_do_MethodName =
    MethodName("libdivide_s64_do", LongDivParamRefs, LongRef)

  val AllIntDivRuntimeMethodNames: List[MethodName] = List(
    libdivide_u32_do_MethodName,
    libdivide_s32_do_MethodName,
    libdivide_u64_do_MethodName,
    libdivide_s64_do_MethodName
  )

  // This type is a structured equivalent of the libdivide_x_t structs
  final case class Strategy[T](
      magic: T, shift: Int, addMarker: Boolean, negativeDivisor: Boolean)

  /** Like Integral[T], but with some unsigned operations that we need. */
  sealed trait UnsignedIntegral[T] extends Integral[T] {
    /** Number of bits used to represent a value of type `T`. */
    val bitSize: Int

    def divideUnsigned(x: T, y: T): T

    def remainderUnsigned(x: T, y: T): T

    /** Divides a wide (2x bit width of T) uint `{numhi, numlo}` by a uint `{den}`.
     *
     *  The result must fit in T's bit width. Returns the pair `(quotient, remainder)`.
     */
    def unsignedDivideWide(numhi: T, numlo: T, den: T): (T, T)

    /** For example, for Int this is `31 - Integer.numberOfLeadingZeros(x)`. */
    def unsignedFloorLog2(x: T): Int

    def compareUnsigned(x: T, y: T): Int

    def isUnsignedPowerOf2(x: T): Boolean

    def one_<<(y: Int): T

    def times2(x: T): T

    // IR-related operations

    val tpe: Type

    def literal(x: T)(implicit pos: Position): Literal

    final def literalZero(implicit pos: Position): Literal = literal(zero)

    val Op_+ : BinaryOp.Code
    val Op_- : BinaryOp.Code
    val Op_* : BinaryOp.Code
    val Op_& : BinaryOp.Code
    val Op_<< : BinaryOp.Code
    val Op_>>> : BinaryOp.Code
    val Op_>> : BinaryOp.Code

    def genMulSignedHi(x: T, y: VarRef)(implicit pos: Position): Tree
    def genMulUnsignedHi(x: T, y: VarRef)(implicit pos: Position): Tree
  }

  implicit object IntIsUnsignedIntegral
      extends UnsignedIntegral[Int]
      with Numeric.IntIsIntegral with Ordering.IntOrdering {

    private final val MaxUIntAsLong = 0xffffffffL

    val bitSize = 32

    def divideUnsigned(x: Int, y: Int): Int = Integer.divideUnsigned(x, y)

    def remainderUnsigned(x: Int, y: Int): Int = Integer.remainderUnsigned(x, y)

    // Port of libdivide_64_div_32_to_32
    def unsignedDivideWide(numhi: Int, numlo: Int, den: Int): (Int, Int) = {
      val num = (numhi.toLong << 32) | Integer.toUnsignedLong(numlo)
      val denLong = Integer.toUnsignedLong(den)

      val quotient = JLong.divideUnsigned(num, denLong)
      val remainder = JLong.remainderUnsigned(num, denLong)

      assert(JLong.compareUnsigned(quotient, MaxUIntAsLong) <= 0)
      assert(JLong.compareUnsigned(remainder, denLong) < 0)

      (quotient.toInt, remainder.toInt)
    }

    def unsignedFloorLog2(x: Int): Int =
      31 - Integer.numberOfLeadingZeros(x)

    def compareUnsigned(x: Int, y: Int): Int =
      Integer.compareUnsigned(x, y)

    def isUnsignedPowerOf2(x: Int): Boolean =
      (x & (x - 1)) == 0 && x != 0

    def one_<<(y: Int): Int =
      1 << y

    def times2(x: Int): Int =
      x << 1

    // IR-related operations

    val tpe: Type = IntType

    def literal(x: Int)(implicit pos: Position): Literal = IntLiteral(x)

    val Op_+ : BinaryOp.Code = BinaryOp.Int_+
    val Op_- : BinaryOp.Code = BinaryOp.Int_-
    val Op_* : BinaryOp.Code = BinaryOp.Int_*
    val Op_& : BinaryOp.Code = BinaryOp.Int_&
    val Op_<< : BinaryOp.Code = BinaryOp.Int_<<
    val Op_>>> : BinaryOp.Code = BinaryOp.Int_>>>
    val Op_>> : BinaryOp.Code = BinaryOp.Int_>>

    def genMulSignedHi(x: Int, y: VarRef)(implicit pos: Position): Tree = {
      // (x.toLong * y.toLong).toInt
      UnaryOp(
        UnaryOp.LongToInt,
        BinaryOp(
          BinaryOp.Long_*,
          LongLiteral(x.toLong),
          UnaryOp(UnaryOp.IntToLong, y)
        )
      )
    }

    def genMulUnsignedHi(x: Int, y: VarRef)(implicit pos: Position): Tree = {
      // (Integer.toUnsignedLong(x) * Integer.toUnsignedLong(y)).toInt
      UnaryOp(
        UnaryOp.LongToInt,
        BinaryOp(
          BinaryOp.Long_*,
          LongLiteral(Integer.toUnsignedLong(x)),
          BinaryOp(
            BinaryOp.Long_&,
            LongLiteral(0xffffffffL),
            UnaryOp(UnaryOp.IntToLong, y)
          )
        )
      )
    }
  }

  implicit object LongIsUnsignedIntegral
      extends UnsignedIntegral[Long]
      with Numeric.LongIsIntegral with Ordering.LongOrdering {

    private val MaxULongAsBigInteger =
      BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)

    private def ulongToBigInteger(a: Long): BigInteger =
      BigInteger.valueOf(a).and(MaxULongAsBigInteger)

    val bitSize = 64

    def divideUnsigned(x: Long, y: Long): Long = JLong.divideUnsigned(x, y)

    def remainderUnsigned(x: Long, y: Long): Long = JLong.remainderUnsigned(x, y)

    def unsignedDivideWide(numhi: Long, numlo: Long, den: Long): (Long, Long) = {
      /* We cheat here. Instead of porting libdivide's implementation, we use
       * full-blown BigInteger's to perform the operation.
       */

      val num = ulongToBigInteger(numhi).shiftLeft(64).or(ulongToBigInteger(numlo))
      val denBig = ulongToBigInteger(den)
      val results = num.divideAndRemainder(denBig)

      assert(results(0).compareTo(MaxULongAsBigInteger) <= 0)
      assert(results(1).compareTo(denBig) < 0)

      (results(0).longValue(), results(1).longValue())
    }

    def unsignedFloorLog2(x: Long): Int =
      63 - JLong.numberOfLeadingZeros(x)

    def compareUnsigned(x: Long, y: Long): Int =
      JLong.compareUnsigned(x, y)

    def isUnsignedPowerOf2(x: Long): Boolean =
      (x & (x - 1L)) == 0L && x != 0L

    def one_<<(y: Int): Long =
      1L << y

    def times2(x: Long): Long =
      x << 1

    // IR-related operations

    val tpe: Type = LongType

    def literal(x: Long)(implicit pos: Position): Literal = LongLiteral(x)

    val Op_+ : BinaryOp.Code = BinaryOp.Long_+
    val Op_- : BinaryOp.Code = BinaryOp.Long_-
    val Op_* : BinaryOp.Code = BinaryOp.Long_*
    val Op_& : BinaryOp.Code = BinaryOp.Long_&
    val Op_<< : BinaryOp.Code = BinaryOp.Long_<<
    val Op_>>> : BinaryOp.Code = BinaryOp.Long_>>>
    val Op_>> : BinaryOp.Code = BinaryOp.Long_>>

    def genMulSignedHi(x: Long, y: VarRef)(implicit pos: Position): Tree = {
      /* Math.multiplyHigh(x, y)
       * Unfortunately, we cannot directly call that method, because it won't
       * be available when we link a javalib < 1.20. So we hard-code its IR.
       */
      genMulHiCommon(x, y, BinaryOp.Long_>>)
    }

    def genMulUnsignedHi(x: Long, y: VarRef)(implicit pos: Position): Tree = {
      /* Math.unsignedMultiplyHigh(x, y)
       * Unfortunately, we cannot directly call that method, because it won't
       * be available when we link a javalib < 1.20. So we hard-code its IR.
       */
      genMulHiCommon(x, y, BinaryOp.Long_>>>)
    }

    private def genMulHiCommon(x: Long, y: VarRef, shiftOp: BinaryOp.Code)(
        implicit pos: Position): Tree = {
      /* val x0 = x & 0xffffffffL
       * val x1 = x >> 32
       * val y0 = y & 0xffffffffL
       * val y1 = y >> 32
       * val t = x1 * y0 + ((x0 * y0) >>> 32)
       * x1 * y1 + (t >> 32) + (((t & 0xffffffffL) + x0 * y1) >> 32)
       */

      val x0 = LongLiteral(x & 0xffffffffL)
      val x1 = LongLiteral(if (shiftOp == BinaryOp.Long_>>) x >> 32 else x >>> 32)
      val lit32 = IntLiteral(32)
      val litMask = LongLiteral(0xffffffffL)

      val y0Def = tempVarDef(y0Name, BinaryOp(BinaryOp.Long_&, litMask, y))
      val y0 = y0Def.ref
      val y1Def = tempVarDef(y1Name, BinaryOp(shiftOp, y, lit32))
      val y1 = y1Def.ref

      val tDef = tempVarDef(
        mulhiTempName,
        BinaryOp(
          BinaryOp.Long_+,
          BinaryOp(BinaryOp.Long_*, x1, y0),
          BinaryOp(
            BinaryOp.Long_>>>,
            BinaryOp(BinaryOp.Long_*, x0, y0),
            lit32
          )
        )
      )
      val t = tDef.ref

      val result = BinaryOp(
        BinaryOp.Long_+,
        BinaryOp(
          BinaryOp.Long_+,
          // x1 * y1
          BinaryOp(BinaryOp.Long_*, x1, y1),
          // t >> 32
          BinaryOp(shiftOp, t, lit32)
        ),
        // ((t & 0xffffffffL) + x0 * y1) >> 32
        BinaryOp(
          shiftOp,
          BinaryOp(
            BinaryOp.Long_+,
            BinaryOp(BinaryOp.Long_&, litMask, t),
            BinaryOp(BinaryOp.Long_*, x0, y1)
          ),
          lit32
        )
      )

      Block(
        y0Def,
        y1Def,
        tDef,
        result
      )
    }
  }

  /** Port and factorization of the libdivide_x_gen for x in {u32,s32,u64,s64}.
   *
   *  We can afford the factorization because this code is not
   *  performance-sensitive. In libdivide, these 4 functions are meant to be
   *  used at run time.
   *
   *  We only use the branch-full variants of libdivide. The branches will be
   *  performed by optimizer, so they are always free.
   */
  def libdivide_gen[T](d: T, isSigned: Boolean)(implicit int: UnsignedIntegral[T]): Strategy[T] = {
    import int.mkNumericOps

    assert(d != int.zero)

    val negativeDivisor = isSigned && int.lt(d, int.zero)
    val absD = if (negativeDivisor) -d else d
    val floor_log_2_d = int.unsignedFloorLog2(absD)

    if (int.isUnsignedPowerOf2(absD)) {
      Strategy(
        magic = int.zero,
        shift = floor_log_2_d,
        addMarker = false,
        negativeDivisor
      )
    } else {
      assert(floor_log_2_d >= 1) // because absD is neither 0 (illegal) nor 1 (power of 2)
      val tentativeShift =
        if (isSigned) floor_log_2_d - 1
        else floor_log_2_d

      var (proposed_m, rem) = int.unsignedDivideWide(int.one_<<(tentativeShift), int.zero, absD)
      val e = absD - rem

      val (shift, addMarker) = if (int.compareUnsigned(e, int.one_<<(floor_log_2_d)) < 0) {
        // This power works
        (tentativeShift, false)
      } else {
        // We do a+a instead of 2*a because we don't have a 2 of type T
        proposed_m += proposed_m
        val twice_rem = rem + rem

        if (int.compareUnsigned(twice_rem, absD) >= 0 || int.compareUnsigned(twice_rem, rem) < 0)
          proposed_m += int.one

        (floor_log_2_d, true)
      }

      proposed_m += int.one

      Strategy(
        magic = if (negativeDivisor) -proposed_m else proposed_m,
        shift,
        addMarker,
        negativeDivisor
      )
    }
  }

}
