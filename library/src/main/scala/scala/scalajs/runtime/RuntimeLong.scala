package scala.scalajs.runtime

import scala.annotation.tailrec

import scala.scalajs.js
import js.|
import js.JSNumberOps._
import js.JSStringOps._

/* IMPORTANT NOTICE about this file
 *
 * The code of RuntimeLong is code-size- and performance critical. The methods
 * of this class are used for every single primitive operation on Longs, and
 * must therefore be as fast as they can.
 *
 * This means that this implementation is oriented for performance over
 * readability and idiomatic code.
 *
 * DRY is applied as much as possible but is bounded by the performance and
 * code size requirements. We use a lot of inline_xyz helpers meant to be used
 * when we already have the parameters on stack, but they are sometimes
 * duplicated in entry points to avoid the explicit extraction of heap fields
 * into temporary variables when they are used only once.
 *
 * Otherwise, we typically extract the lo and hi fields from the heap into
 * local variables once, whether explicitly in vals or implicitly when passed
 * as arguments to inlineable methods. This reduces heap/record accesses, and
 * allows both our optimizer and the JIT to know that we indeed always have the
 * same value (the JIT does not even know that fields are immutable, but even
 * our optimizer does not make use of that information).
 */

/** Emulates a Long on the JavaScript platform. */
//@inline
final class RuntimeLong(val lo: Int, val hi: Int)
    extends java.lang.Number with java.lang.Comparable[java.lang.Long] {
  a =>

  import RuntimeLong._
  import Utils._

  // Universal equality

  @inline
  override def equals(that: Any): Boolean = that match {
    case b: RuntimeLong => inline_equals(b)
    case _              => false
  }

  @inline override def hashCode(): Int = lo ^ hi

  // String operations

  override def toString(): String = {
    if (isZero)
      return "0"
    if (isMinValue)
      return "-9223372036854775808"
    if (isNegative)
      return "-" + (-a).toString()

    var rem = a
    var res = ""

    while (!rem.isZero) {
      // Do several digits each time through the loop, so as to
      // minimize the calls to the very expensive emulated div.
      val tenPowerZeroes = 9
      val tenPower = 1000000000
      val tenPowerLong = fromInt(tenPower)

      rem = divMod(rem, tenPowerLong, true)
      var digits = "" + RuntimeLong.rem.toInt

      if (!rem.isZero) {
        var zeroesNeeded = tenPowerZeroes - digits.length()
        while (zeroesNeeded > 0) {
          digits = "0" + digits
          zeroesNeeded -= 1
        }
      }

      res = digits + res
    }

    res
  }

  // Conversions

  @inline def toByte: Byte = lo.toByte
  @inline def toShort: Short = lo.toShort
  @inline def toChar: Char = lo.toChar
  @inline def toInt: Int = lo
  @inline def toLong: Long = this.asInstanceOf[Long]
  @inline def toFloat: Float = toDouble.toFloat
  @inline def toDouble: Double = RuntimeLong.toDouble(lo, hi)

  def toNumber: Double = {
    var lo = this.lo.toDouble
    val hi = this.hi.toDouble
    if (lo < 0)
      lo += TwoPow32
    TwoPow32 * hi + lo
  }

  // java.lang.Number

  @inline override def byteValue(): Byte = toByte
  @inline override def shortValue(): Short = toShort
  @inline def intValue(): Int = toInt
  @inline def longValue(): Long = toLong
  @inline def floatValue(): Float = toFloat
  @inline def doubleValue(): Double = toDouble

  // Comparisons and java.lang.Comparable interface

  private def isZero: Boolean =
    hi == 0 && lo == 0

  private def isMinValue: Boolean =
    hi == 0x80000000 && lo == 0

  private def isPositive: Boolean =
    (a.hi & 0x80000000) == 0

  private def isNegative: Boolean =
    (a.hi & 0x80000000) != 0

  /**
   * Return the number of leading zeros of a long value.
   */
  private def numberOfLeadingZeros: Int = {
    if (hi == 0) Integer.numberOfLeadingZeros(lo) + 32
    else Integer.numberOfLeadingZeros(hi)
  }

  def compareTo(b: RuntimeLong): Int = {
    /*var r = a.hi - b.hi
    if (r != 0) {
      r
    } else {
      r = (a.lo >>> 1) - (b.lo >>> 1)
      if (r != 0) {
        r
      } else {
        (a.lo & 1) - (b.lo & 1)
      }
    }*/
    RuntimeLong.compare(a.lo, a.hi, b.lo, b.hi)
  }

  @inline
  def compareTo(that: java.lang.Long): Int =
    compareTo(that.asInstanceOf[RuntimeLong])

  @inline
  private def inline_equals(b: RuntimeLong): Boolean =
    a.lo == b.lo && a.hi == b.hi

  def equals(b: RuntimeLong): Boolean =
    a.lo == b.lo && a.hi == b.hi

  def notEquals(b: RuntimeLong): Boolean =
    a.lo != b.lo || a.hi != b.hi

  def <(b: RuntimeLong): Boolean = {
    if (a.hi > b.hi) {
      false
    } else if (a.hi < b.hi) {
      true
    } else {
      val x = a.lo >>> 1
      val y = b.lo >>> 1
      if (x != y) {
        x < y
      } else {
        (a.lo & 1) < (b.lo & 1)
      }
    }
  }

  def <=(b: RuntimeLong): Boolean = {
    if (a.hi > b.hi) {
      false
    } else if (a.hi < b.hi) {
      true
    } else {
      var x = a.lo >>> 1
      var y = b.lo >>> 1
      if (x != y) {
        x <= y
      } else {
        (a.lo & 1) <= (b.lo & 1)
      }
    }
  }

  def >(b: RuntimeLong): Boolean = {
    if (a.hi < b.hi) {
      false
    } else if (a.hi > b.hi) {
      true
    } else {
      val x = a.lo >>> 1
      val y = b.lo >>> 1
      if (x != y) {
        x > y
      } else {
        (a.lo & 1) > (b.lo & 1)
      }
    }
  }

  def >=(b: RuntimeLong): Boolean = {
    if (a.hi < b.hi) {
      false
    } else if (a.hi > b.hi) {
      true
    } else {
      val x = a.lo >>> 1
      val y = b.lo >>> 1
      if (x != y) {
        x >= y
      } else {
        (a.lo & 1) >= (b.lo & 1)
      }
    }
  }

  // Bitwise operations

  def unary_~ : RuntimeLong = // scalastyle:ignore
    new RuntimeLong(~lo, ~hi)

  def |(b: RuntimeLong): RuntimeLong =
    new RuntimeLong(a.lo | b.lo, a.hi | b.hi)

  def &(b: RuntimeLong): RuntimeLong =
    new RuntimeLong(a.lo & b.lo, a.hi & b.hi)

  def ^(b: RuntimeLong): RuntimeLong =
    new RuntimeLong(a.lo ^ b.lo, a.hi ^ b.hi)

  // Shifts

  /** Shift left */
  def <<(n: Int): RuntimeLong = {
    val b = n & 63
    if (b == 0) {
      a
    } else if (b < 32) {
      new RuntimeLong(a.lo << b, (a.lo >>> (32 - b)) | (a.hi << b))
    } else if (b == 32) {
      new RuntimeLong(0, a.lo)
    } else {
      new RuntimeLong(0, a.lo << (b - 32))
    }
  }

  /** Logical shift right */
  def >>>(n: Int): RuntimeLong = {
    val b = n & 63
    if (b == 0) {
      a
    } else if (b < 32) {
      new RuntimeLong((a.lo >>> b) | (a.hi << (32 - b)), a.hi >>> b)
    } else if (b == 32) {
      new RuntimeLong(a.hi, 0)
    } else {
      new RuntimeLong((a.hi >>> (b - 32)), 0)
    }
  }

  /** Arithmetic shift right */
  def >>(n: Int): RuntimeLong = {
    val b = n & 63
    if (b == 0) {
      a
    } else if (b < 32) {
      new RuntimeLong((a.lo >>> b) | (a.hi << (32 - b)), a.hi >> b)
    } else if (b == 32) {
      new RuntimeLong(a.hi, a.hi >> 31)
    } else {
      new RuntimeLong((a.hi >> (b - 32)), a.hi >> 31)
    }
  }

  // Arithmetic operations

  def unary_- : RuntimeLong = { // scalastyle:ignore
    new RuntimeLong(a.lo ^ 0xFFFFFFFF, a.hi ^ 0xFFFFFFFF).inc
  }

  private def inc: RuntimeLong = {
    var lo = a.lo + 1
    var hi = a.hi
    if (lo == 0) {
      hi = hi + 1
    }
    new RuntimeLong(lo, hi)
  }

  def +(b: RuntimeLong): RuntimeLong = {
    val a_lolo = a.lo & 0xFFFF
    val a_lohi = a.lo >>> 16
    val a_hilo = a.hi & 0xFFFF
    val a_hihi = a.hi >>> 16
    val b_lolo = b.lo & 0xFFFF
    val b_lohi = b.lo >>> 16
    val b_hilo = b.hi & 0xFFFF
    val b_hihi = b.hi >>> 16

    val lolo = a_lolo + b_lolo
    val lohi = a_lohi + b_lohi + (lolo >> 16)
    val hilo = a_hilo + b_hilo + (lohi >> 16)
    val hihi = a_hihi + b_hihi + (hilo >> 16)
    new RuntimeLong((lolo & 0xFFFF) | ((lohi & 0xFFFF) << 16),
        (hilo & 0xFFFF) | ((hihi & 0xFFFF) << 16))
  }

  def -(b: RuntimeLong): RuntimeLong = {
    val a_lolo = a.lo & 0xFFFF
    val a_lohi = a.lo >>> 16
    val a_hilo = a.hi & 0xFFFF
    val a_hihi = a.hi >>> 16
    val b_lolo = b.lo & 0xFFFF
    val b_lohi = b.lo >>> 16
    val b_hilo = b.hi & 0xFFFF
    val b_hihi = b.hi >>> 16

    val lolo = a_lolo - b_lolo
    val lohi = a_lohi - b_lohi + (lolo >> 16)
    val hilo = a_hilo - b_hilo + (lohi >> 16)
    val hihi = a_hihi - b_hihi + (hilo >> 16)
    new RuntimeLong((lolo & 0xFFFF) | ((lohi & 0xFFFF) << 16),
        (hilo & 0xFFFF) | ((hihi & 0xFFFF) << 16))
  }

  def *(b: RuntimeLong): RuntimeLong = {
    import js.DynamicImplicits._

    val a_lolo: js.Dynamic = a.lo & 0xFFFF
    val a_lohi: js.Dynamic = a.lo >>> 16
    val a_hilo: js.Dynamic = a.hi & 0xFFFF
    val a_hihi: js.Dynamic = a.hi >>> 16
    val b_lolo: js.Dynamic = b.lo & 0xFFFF
    val b_lohi: js.Dynamic = b.lo >>> 16
    val b_hilo: js.Dynamic = b.hi & 0xFFFF
    val b_hihi: js.Dynamic = b.hi >>> 16

    var lolo: js.Dynamic = 0.0
    var lohi: js.Dynamic = 0.0
    var hilo: js.Dynamic = 0.0
    var hihi: js.Dynamic = 0.0
    lolo = (a_lolo * b_lolo) | 0
    lohi = lolo >>> 16
    lohi = ((lohi & 0xFFFF) + a_lohi * b_lolo) | 0
    hilo = (hilo + (lohi >>> 16)) | 0
    lohi = ((lohi & 0xFFFF) + a_lolo * b_lohi) | 0
    hilo = (hilo + (lohi >>> 16)) | 0
    hihi = hilo >>> 16
    hilo = ((hilo & 0xFFFF) + a_hilo * b_lolo) | 0
    hihi = (hihi + (hilo >>> 16)) | 0
    hilo = ((hilo & 0xFFFF) + a_lohi * b_lohi) | 0
    hihi = (hihi + (hilo >>> 16)) | 0
    hilo = ((hilo & 0xFFFF) + a_lolo * b_hilo) | 0
    hihi = (hihi + (hilo >>> 16)) | 0
    hihi = (hihi + a_hihi * b_lolo + a_hilo * b_lohi + a_lohi * b_hilo + a_lolo * b_hihi) | 0
    new RuntimeLong(
        ((lolo & 0xFFFF) | (lohi << 16)).asInstanceOf[Int],
        ((hilo & 0xFFFF) | (hihi << 16)).asInstanceOf[Int])
  }

  def /(b: RuntimeLong): RuntimeLong = {
    divMod(a, b, computeRemainder = false)
  }

  /** `java.lang.Long.divideUnsigned(a, b)` */
  @inline
  def divideUnsigned(b: RuntimeLong): RuntimeLong =
    RuntimeLong.divideUnsigned(a, b)

  def %(b: RuntimeLong): RuntimeLong = {
    divMod(a, b, computeRemainder = true)
    RuntimeLong.rem
  }

  /** `java.lang.Long.remainderUnsigned(a, b)` */
  @inline
  def remainderUnsigned(b: RuntimeLong): RuntimeLong =
    RuntimeLong.remainderUnsigned(a, b)

}

object RuntimeLong {
  import Utils._

  private final val TwoPow32 = 4294967296.0
  private final val TwoPow63 = 9223372036854775808.0

  private final val MaxNormal = 1 << 18

  /** The magical mask that allows to test whether an unsigned long is a safe
   *  double.
   *  @see Utils.isUnsignedSafeDouble
   */
  private final val UnsignedSafeDoubleHiMask = 0xffe00000

  private final val AskQuotient = 0
  private final val AskRemainder = 1
  private final val AskToString = 2

  /** The hi part of a (lo, hi) return value. */
  private[this] var hiReturn: Int = _

  private var rem: RuntimeLong = _

  private object KotlinLong {
    val Zero = new RuntimeLong(0, 0)
    val One = new RuntimeLong(1, 0)
    val NegOne = new RuntimeLong(-1, -1)
    val MinValue = new RuntimeLong(0, 0x80000000)
  }

  private def toString(lo: Int, hi: Int): String = {
    if (isInt32(lo, hi)) {
      lo.toString()
    } else if (hi < 0) {
      "-" + toUnsignedString(inline_lo_unary_-(lo), inline_hi_unary_-(lo, hi))
    } else {
      toUnsignedString(lo, hi)
    }
  }

  private def toUnsignedString(lo: Int, hi: Int): String = {
    // This is called only if (lo, hi) is not an Int32

    if (isUnsignedSafeDouble(hi)) {
      // (lo, hi) is small enough to be a Double, use that directly
      asUnsignedSafeDouble(lo, hi).toString
    } else {
      /* At this point, (lo, hi) >= 2^53.
       * We divide (lo, hi) once by 10^9 and keep the remainder.
       *
       * The remainder must then be < 10^9, and is therefore an int32.
       *
       * The quotient must be <= ULong.MaxValue / 10^9, which is < 2^53, and
       * is therefore a valid double. It must also be non-zero, since
       * (lo, hi) >= 2^53 > 10^9.
       *
       * To avoid allocating a tuple with the quotient and remainder, we push
       * the final conversion to string inside unsignedDivModHelper. According
       * to micro-benchmarks, this optimization makes toString 25% faster in
       * this branch.
       */
      unsignedDivModHelper(lo, hi, 1000000000, 0,
          AskToString).asInstanceOf[String]
    }
  }

  private def toDouble(lo: Int, hi: Int): Double = {
    if (hi < 0) {
      // We do .toUint on the hi part specifically for MinValue
      -(inline_hi_unary_-(lo, hi).toUint * TwoPow32 + inline_lo_unary_-(lo).toUint)
    } else {
      hi * TwoPow32 + lo.toUint
    }
  }

  def fromInt(value: Int): RuntimeLong =
    if (value >= 0) new RuntimeLong(value, 0)
    else new RuntimeLong(value, -1)

  def fromDouble(value: Double): RuntimeLong = fromNumber(value)

  private def fromNumber(value: Double): RuntimeLong = {
    if (value >= 0) {
      new RuntimeLong(rawToInt(value), rawToInt(value / TwoPow32))
    } else {
      -(new RuntimeLong(rawToInt(-value), rawToInt(-value / TwoPow32)))
    }
  }

  private def compare(alo: Int, ahi: Int, blo: Int, bhi: Int): Int = {
    if (ahi == bhi) {
      if (alo == blo) 0
      else if (inlineUnsignedInt_<(alo, blo)) -1
      else 1
    } else {
      if (ahi < bhi) -1
      else 1
    }
  }

  @inline
  def divide(a: RuntimeLong, b: RuntimeLong): RuntimeLong = {
    val lo = divideImpl(a.lo, a.hi, b.lo, b.hi)
    new RuntimeLong(lo, hiReturn)
  }

  def divideImpl(alo: Int, ahi: Int, blo: Int, bhi: Int): Int = {
    if (isZero(blo, bhi))
      throw new ArithmeticException("/ by zero")

    if (isInt32(alo, ahi)) {
      if (isInt32(blo, bhi)) {
        if (alo == Int.MinValue && blo == -1) {
          hiReturn = 0
          Int.MinValue
        } else {
          val lo = alo / blo
          hiReturn = lo >> 31
          lo
        }
      } else {
        // Either a == Int.MinValue && b == (Int.MaxValue + 1), or (abs(b) > abs(a))
        if (alo == Int.MinValue && (blo == 0x80000000 && bhi == 0)) {
          hiReturn = -1
          -1
        } else {
          // 0L, because abs(b) > abs(a)
          hiReturn = 0
          0
        }
      }
    } else {
      val (aNeg, aAbs) = inline_abs(alo, ahi)
      val (bNeg, bAbs) = inline_abs(blo, bhi)
      val absRLo = unsigned_/(aAbs.lo, aAbs.hi, bAbs.lo, bAbs.hi)
      if (aNeg == bNeg) absRLo
      else inline_hiReturn_unary_-(absRLo, hiReturn)
    }
  }

  @inline
  def divideUnsigned(a: RuntimeLong, b: RuntimeLong): RuntimeLong = {
    val lo = divideUnsignedImpl(a.lo, a.hi, b.lo, b.hi)
    new RuntimeLong(lo, hiReturn)
  }

  def divideUnsignedImpl(alo: Int, ahi: Int, blo: Int, bhi: Int): Int = {
    if (isZero(blo, bhi))
      throw new ArithmeticException("/ by zero")

    if (isUInt32(ahi)) {
      if (isUInt32(bhi)) {
        hiReturn = 0
        // Integer.divideUnsigned(alo, blo), inaccessible when compiling on JDK < 8
        rawToInt(alo.toUint / blo.toUint)
      } else {
        // a < b
        hiReturn = 0
        0
      }
    } else {
      unsigned_/(alo, ahi, blo, bhi)
    }
  }

  private def unsigned_/(alo: Int, ahi: Int, blo: Int, bhi: Int): Int = {
    // This method is not called if isInt32(alo, ahi) nor if isZero(blo, bhi)
    if (isUnsignedSafeDouble(ahi)) {
      if (isUnsignedSafeDouble(bhi)) {
        val aDouble = asUnsignedSafeDouble(alo, ahi)
        val bDouble = asUnsignedSafeDouble(blo, bhi)
        val rDouble = aDouble / bDouble
        hiReturn = unsignedSafeDoubleHi(rDouble)
        unsignedSafeDoubleLo(rDouble)
      } else {
        // 0L, because b > a
        hiReturn = 0
        0
      }
    } else {
      if (bhi == 0 && isPowerOfTwo_IKnowItsNot0(blo)) {
        val pow = log2OfPowerOfTwo(blo)
        hiReturn = ahi >>> pow
        (alo >>> pow) | (ahi << 1 << (31-pow))
      } else if (blo == 0 && isPowerOfTwo_IKnowItsNot0(bhi)) {
        val pow = log2OfPowerOfTwo(bhi)
        hiReturn = 0
        ahi >>> pow
      } else {
        unsignedDivModHelper(alo, ahi, blo, bhi, AskQuotient).asInstanceOf[Int]
      }
    }
  }

  @inline
  def remainder(a: RuntimeLong, b: RuntimeLong): RuntimeLong = {
    val lo = remainderImpl(a.lo, a.hi, b.lo, b.hi)
    new RuntimeLong(lo, hiReturn)
  }

  def remainderImpl(alo: Int, ahi: Int, blo: Int, bhi: Int): Int = {
    if (isZero(blo, bhi))
      throw new ArithmeticException("/ by zero")

    if (isInt32(alo, ahi)) {
      if (isInt32(blo, bhi)) {
        if (blo != -1) {
          val lo = alo % blo
          hiReturn = lo >> 31
          lo
        } else {
          // Work around https://github.com/ariya/phantomjs/issues/12198
          hiReturn = 0
          0
        }
      } else {
        // Either a == Int.MinValue && b == (Int.MaxValue + 1), or (abs(b) > abs(a))
        if (alo == Int.MinValue && (blo == 0x80000000 && bhi == 0)) {
          hiReturn = 0
          0
        } else {
          // a, because abs(b) > abs(a)
          hiReturn = ahi
          alo
        }
      }
    } else {
      val (aNeg, aAbs) = inline_abs(alo, ahi)
      val (_, bAbs) = inline_abs(blo, bhi)
      val absRLo = unsigned_%(aAbs.lo, aAbs.hi, bAbs.lo, bAbs.hi)
      if (aNeg) inline_hiReturn_unary_-(absRLo, hiReturn)
      else absRLo
    }
  }

  @inline
  def remainderUnsigned(a: RuntimeLong, b: RuntimeLong): RuntimeLong = {
    val lo = remainderUnsignedImpl(a.lo, a.hi, b.lo, b.hi)
    new RuntimeLong(lo, hiReturn)
  }

  def remainderUnsignedImpl(alo: Int, ahi: Int, blo: Int, bhi: Int): Int = {
    if (isZero(blo, bhi))
      throw new ArithmeticException("/ by zero")

    if (isUInt32(ahi)) {
      if (isUInt32(bhi)) {
        hiReturn = 0
        // Integer.remainderUnsigned(alo, blo), inaccessible when compiling on JDK < 8
        rawToInt(alo.toUint % blo.toUint)
      } else {
        // a < b
        hiReturn = ahi
        alo
      }
    } else {
      unsigned_%(alo, ahi, blo, bhi)
    }
  }

  private def unsigned_%(alo: Int, ahi: Int, blo: Int, bhi: Int): Int = {
    // This method is not called if isInt32(alo, ahi) nor if isZero(blo, bhi)
    if (isUnsignedSafeDouble(ahi)) {
      if (isUnsignedSafeDouble(bhi)) {
        val aDouble = asUnsignedSafeDouble(alo, ahi)
        val bDouble = asUnsignedSafeDouble(blo, bhi)
        val rDouble = aDouble % bDouble
        hiReturn = unsignedSafeDoubleHi(rDouble)
        unsignedSafeDoubleLo(rDouble)
      } else {
        // a, because b > a
        hiReturn = ahi
        alo
      }
    } else {
      if (bhi == 0 && isPowerOfTwo_IKnowItsNot0(blo)) {
        hiReturn = 0
        alo & (blo - 1)
      } else if (blo == 0 && isPowerOfTwo_IKnowItsNot0(bhi)) {
        hiReturn = ahi & (bhi - 1)
        alo
      } else {
        unsignedDivModHelper(alo, ahi, blo, bhi, AskRemainder).asInstanceOf[Int]
      }
    }
  }

  /** Helper for `unsigned_/`, `unsigned_%` and `toUnsignedString()`.
   *
   *  The value of `ask` may be one of:
   *
   *  - `AskQuotient`: returns the quotient (with the hi part in `hiReturn`)
   *  - `AskRemainder`: returns the remainder (with the hi part in `hiReturn`)
   *  - `AskToString`: returns the conversion of `(alo, ahi)` to string.
   *    In this case, `blo` must be 10^9 and `bhi` must be 0.
   */
  private def unsignedDivModHelper(alo: Int, ahi: Int, blo: Int, bhi: Int,
      ask: Int): Int | String = {

    var shift =
      inlineNumberOfLeadingZeros(blo, bhi) - inlineNumberOfLeadingZeros(alo, ahi)
    val initialBShift = new RuntimeLong(blo, bhi) << shift
    var bShiftLo = initialBShift.lo
    var bShiftHi = initialBShift.hi
    var remLo = alo
    var remHi = ahi
    var quotLo = 0
    var quotHi = 0

    /* Invariants:
     *   bShift == b << shift == b * 2^shift
     *   quot >= 0
     *   0 <= rem < 2 * bShift
     *   quot * b + rem == a
     *
     * The loop condition should be
     *   while (shift >= 0 && !isUnsignedSafeDouble(remHi))
     * but we manually inline isUnsignedSafeDouble because remHi is a var. If
     * we let the optimizer inline it, it will first store remHi in a temporary
     * val, which will explose the while condition as a while(true) + if +
     * break, and we don't want that.
     */
    while (shift >= 0 && (remHi & UnsignedSafeDoubleHiMask) != 0) {
      if (inlineUnsigned_>=(remLo, remHi, bShiftLo, bShiftHi)) {
        val newRem =
          new RuntimeLong(remLo, remHi) - new RuntimeLong(bShiftLo, bShiftHi)
        remLo = newRem.lo
        remHi = newRem.hi
        if (shift < 32)
          quotLo |= (1 << shift)
        else
          quotHi |= (1 << shift) // == (1 << (shift - 32))
      }
      shift -= 1
      val newBShift = new RuntimeLong(bShiftLo, bShiftHi) >>> 1
      bShiftLo = newBShift.lo
      bShiftHi = newBShift.hi
    }

    // Now rem < 2^53, we can finish with a double division
    if (inlineUnsigned_>=(remLo, remHi, blo, bhi)) {
      val remDouble = asUnsignedSafeDouble(remLo, remHi)
      val bDouble = asUnsignedSafeDouble(blo, bhi)

      if (ask != AskRemainder) {
        val rem_div_bDouble = fromUnsignedSafeDouble(remDouble / bDouble)
        val newQuot = new RuntimeLong(quotLo, quotHi) + rem_div_bDouble
        quotLo = newQuot.lo
        quotHi = newQuot.hi
      }

      if (ask != AskQuotient) {
        val rem_mod_bDouble = remDouble % bDouble
        remLo = unsignedSafeDoubleLo(rem_mod_bDouble)
        remHi = unsignedSafeDoubleHi(rem_mod_bDouble)
      }
    }

    if (ask == AskQuotient) {
      hiReturn = quotHi
      quotLo
    } else if (ask == AskRemainder) {
      hiReturn = remHi
      remLo
    } else {
      // AskToString (recall that b = 10^9 in this case)
      val quot = asUnsignedSafeDouble(quotLo, quotHi) // != 0
      val remStr = remLo.toString // remHi is always 0
      quot.toString + "000000000".jsSubstring(remStr.length) + remStr
    }
  }

  private def divMod(a0: RuntimeLong, b0: RuntimeLong,
      computeRemainder: Boolean): RuntimeLong = {
    var a = a0
    var b = b0

    if (b.isZero) {
      throw new ArithmeticException("divide by zero");
    }
    if (a.isZero) {
      if (computeRemainder) {
        rem = new RuntimeLong(0, 0) // zero
      }
      return new RuntimeLong(0, 0) // zero
    }

    // MIN_VALUE / MIN_VALUE = 1, anything other a / MIN_VALUE is 0
    if (b.isMinValue) {
      return divModByMinValue(a, computeRemainder)
    }

    // Normalize b to abs(b), keeping track of the parity in 'negative'.
    // We can do this because we have already ensured that b != MIN_VALUE.
    var negative = false
    if (b.isNegative) {
      b = -b
      negative = !negative
    }

    // If b == 2^n, bpower will be n, otherwise it will be -1
    val bpower = powerOfTwo(b)

    // True if the original value of a is negative
    var aIsNegative = false
    // True if the original value of a is Long.MIN_VALUE
    var aIsMinValue = false

    /*
     * Normalize a to a positive value, keeping track of the sign change in
     * 'negative' (which tracks the sign of both a and b and is used to
     * determine the sign of the quotient) and 'aIsNegative' (which is used to
     * determine the sign of the remainder).
     *
     * For all values of a except MIN_VALUE, we can just negate a and modify
     * negative and aIsNegative appropriately. When a == MIN_VALUE, negation is
     * not possible without overflowing 64 bits, so instead of computing
     * abs(MIN_VALUE) / abs(b) we compute (abs(MIN_VALUE) - 1) / abs(b). The
     * only circumstance under which these quotients differ is when b is a power
     * of two, which will divide abs(MIN_VALUE) == 2^64 exactly. In this case,
     * we can get the proper result by shifting MIN_VALUE in unsigned fashion.
     *
     * We make a single copy of a before the first operation that needs to
     * modify its value.
     */
    if (a.isMinValue) {
      aIsMinValue = true
      aIsNegative = true
      // If b is not a power of two, treat -a as MAX_VALUE (instead of the
      // actual value (MAX_VALUE + 1)).
      if (bpower == -1) {
        a = new RuntimeLong(0xffffffff, 0x7fffffff)
        negative = !negative
      } else {
        // Signed shift of MIN_VALUE produces the right answer
        var c = a >> bpower
        if (negative) {
          c = -c
        }
        if (computeRemainder) {
          rem = new RuntimeLong(0, 0) // zero
        }
        return c
      }
    } else if (a.isNegative) {
      aIsNegative = true
      a = -a
      negative = !negative
    }

    // Now both a and b are non-negative

    // If b is a power of two, just shift
    if (bpower != -1) {
      return divModByShift(a, bpower, negative, aIsNegative, computeRemainder)
    }

    // if a < b, the quotient is 0 and the remainder is a
    if (a < b) {
      if (computeRemainder) {
        if (aIsNegative) {
          rem = -a
        } else {
          rem = a
        }
      }
      return new RuntimeLong(0, 0) // zero
    }

    // Generate the quotient using bit-at-a-time long division
    divModHelper(a, b, negative, aIsNegative,
        aIsMinValue, computeRemainder)
  }

  /**
   * Return the exact log base 2 of a, or -1 if a is not a power of two:
   *
   * <pre>
   * if (x == 2^n) {
   *   return n;
   * } else {
   *   return -1;
   * }
   * </pre>
   */
  private def powerOfTwo(a: RuntimeLong): Int = {
    val lo = a.lo
    if ((lo & (lo - 1)) != 0) {
      -1
    } else {
      val hi = a.hi
      if ((hi & (hi - 1)) != 0) {
        -1
      } else {
        if (hi == 0 && lo != 0)
          Integer.numberOfTrailingZeros(lo)
        else if (lo == 0 && hi != 0)
          Integer.numberOfTrailingZeros(hi) + 32
        else
          -1
      }
    }
  }

  private def divModByMinValue(a: RuntimeLong,
      computeRemainder: Boolean): RuntimeLong = {
    // MIN_VALUE / MIN_VALUE == 1, remainder = 0
    // (a != MIN_VALUE) / MIN_VALUE == 0, remainder == a
    if (a.isMinValue) {
      if (computeRemainder) {
        rem = new RuntimeLong(0, 0) // zero
      }
      new RuntimeLong(1, 0)
    } else {
      if (computeRemainder) {
        rem = a
      }
      new RuntimeLong(0, 0) // zero
    }
  }

  private def divModByShift(a: RuntimeLong, bpower: Int, negative: Boolean,
      aIsNegative: Boolean, computeRemainder: Boolean): RuntimeLong = {
    var c = a >> bpower
    if (negative) {
      c = -c
    }

    if (computeRemainder) {
      val a2 = maskRight(a, bpower)
      if (aIsNegative) {
        rem = -a2
      } else {
        rem = a2
      }
    }

    c
  }

  /**
   * a & ((1L << bits) - 1)
   */
  private def maskRight(a: RuntimeLong, bits: Int): RuntimeLong = {
    if (bits <= 32)
      new RuntimeLong(a.lo & ((1 << bits) - 1), 0)
    else
      new RuntimeLong(a.lo, a.hi & ((1 << (bits - 32)) - 1))
  }

  private def divModHelper(a0: RuntimeLong, b: RuntimeLong,
      negative: Boolean, aIsNegative: Boolean, aIsMinValue: Boolean,
      computeRemainder: Boolean): RuntimeLong = {
    var a = a0

    // Align the leading one bits of a and b by shifting b left
    var shift = b.numberOfLeadingZeros - a.numberOfLeadingZeros
    var bshift = b << shift

    var quotient = new RuntimeLong(0, 0)
    var break = false
    while (!break && shift >= 0) {
      if (a >= bshift) {
        a -= bshift
        //setBit(quotient, shift);
        quotient |= (new RuntimeLong(1, 0) << shift)
        if (a.isZero) {
          break = true
        } else {
          bshift >>>= 1
          shift -= 1
        }
      } else {
        bshift >>>= 1
        shift -= 1
      }
    }

    if (negative) {
      quotient = -quotient
    }

    if (computeRemainder) {
      if (aIsNegative) {
        rem = -a
        if (aIsMinValue) {
          rem = rem - new RuntimeLong(1, 0)
        }
      } else {
        rem = a
      }
    }

    quotient
  }

  @inline
  private def inline_hiReturn_unary_-(lo: Int, hi: Int): Int = {
    hiReturn = inline_hi_unary_-(lo, hi)
    inline_lo_unary_-(lo)
  }

  // In a different object so they can be inlined without cost
  private object Utils {
    /** Tests whether the long (lo, hi) is 0. */
    @inline def isZero(lo: Int, hi: Int): Boolean =
      (lo | hi) == 0

    /** Tests whether the long (lo, hi)'s mathematic value fits in a signed Int. */
    @inline def isInt32(lo: Int, hi: Int): Boolean =
      hi == (lo >> 31)

    /** Tests whether the long (_, hi)'s mathematic value fits in an unsigned Int. */
    @inline def isUInt32(hi: Int): Boolean =
      hi == 0

    /** Tests whether an unsigned long (lo, hi) is a safe Double.
     *  This test is in fact slightly stricter than necessary, as it tests
     *  whether `x < 2^53`, although x == 2^53 would be a perfectly safe
     *  Double. The reason we do this is that testing `x <= 2^53` is much
     *  slower, as `x == 2^53` basically has to be treated specially.
     *  Since there is virtually no gain to treating 2^53 itself as a safe
     *  Double, compared to all numbers smaller than it, we don't bother, and
     *  stay on the fast side.
     */
    @inline def isUnsignedSafeDouble(hi: Int): Boolean =
      (hi & UnsignedSafeDoubleHiMask) == 0

    /** Converts an unsigned safe double into its Double representation. */
    @inline def asUnsignedSafeDouble(lo: Int, hi: Int): Double =
      hi * TwoPow32 + lo.toUint

    /** Converts an unsigned safe double into its RuntimeLong representation. */
    @inline def fromUnsignedSafeDouble(x: Double): RuntimeLong =
      new RuntimeLong(unsignedSafeDoubleLo(x), unsignedSafeDoubleHi(x))

    /** Computes the lo part of a long from an unsigned safe double. */
    @inline def unsignedSafeDoubleLo(x: Double): Int =
      rawToInt(x)

    /** Computes the hi part of a long from an unsigned safe double. */
    @inline def unsignedSafeDoubleHi(x: Double): Int =
      rawToInt(x / TwoPow32)

    /** Performs the JavaScript operation `(x | 0)`. */
    @inline def rawToInt(x: Double): Int =
      (x.asInstanceOf[js.Dynamic] | 0.asInstanceOf[js.Dynamic]).asInstanceOf[Int]

    /** Tests whether the given non-zero unsigned Int is an exact power of 2. */
    @inline def isPowerOfTwo_IKnowItsNot0(i: Int): Boolean =
      (i & (i - 1)) == 0

    /** Returns the log2 of the given unsigned Int assuming it is an exact power of 2. */
    @inline def log2OfPowerOfTwo(i: Int): Int =
      31 - Integer.numberOfLeadingZeros(i)

    /** Returns the number of leading zeros in the given long (lo, hi). */
    @inline def inlineNumberOfLeadingZeros(lo: Int, hi: Int): Int =
      if (hi != 0) Integer.numberOfLeadingZeros(hi)
      else Integer.numberOfLeadingZeros(lo) + 32

    /** Tests whether the unsigned long (alo, ahi) is >= (blo, bhi). */
    @inline
    def inlineUnsigned_>=(alo: Int, ahi: Int, blo: Int, bhi: Int): Boolean =
      if (ahi == bhi) inlineUnsignedInt_>=(alo, blo)
      else inlineUnsignedInt_>=(ahi, bhi)

    @inline
    def inlineUnsignedInt_<(a: Int, b: Int): Boolean =
      (a ^ 0x80000000) < (b ^ 0x80000000)

    @inline
    def inlineUnsignedInt_>(a: Int, b: Int): Boolean =
      (a ^ 0x80000000) > (b ^ 0x80000000)

    @inline
    def inlineUnsignedInt_>=(a: Int, b: Int): Boolean =
      (a ^ 0x80000000) >= (b ^ 0x80000000)

    @inline
    def inline_lo_unary_-(lo: Int): Int =
      -lo

    @inline
    def inline_hi_unary_-(lo: Int, hi: Int): Int =
      if (lo != 0) ~hi else -hi

    @inline
    def inline_abs(lo: Int, hi: Int): (Boolean, RuntimeLong) = {
      val neg = hi < 0
      val abs =
        if (neg) new RuntimeLong(inline_lo_unary_-(lo), inline_hi_unary_-(lo, hi))
        else new RuntimeLong(lo, hi)
      (neg, abs)
    }
  }

}
