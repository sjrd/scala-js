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

package java
package lang

import scala.scalajs.js
import js.Dynamic.{ global => g }

import scala.scalajs.LinkingInfo
import scala.scalajs.LinkingInfo.ESVersion

object Math {
  final val E  = 2.718281828459045
  final val PI = 3.141592653589793

  @inline private def assumingES6: scala.Boolean =
    LinkingInfo.esVersion >= ESVersion.ES2015

  @inline def abs(a: scala.Int): scala.Int = if (a < 0) -a else a
  @inline def abs(a: scala.Long): scala.Long = if (a < 0) -a else a

  // Wasm intrinsics
  @inline def abs(a: scala.Float): scala.Float = js.Math.abs(a).toFloat
  @inline def abs(a: scala.Double): scala.Double = js.Math.abs(a)

  @inline def max(a: scala.Int, b: scala.Int): scala.Int = if (a > b) a else b
  @inline def max(a: scala.Long, b: scala.Long): scala.Long = if (a > b) a else b

  // Wasm intrinsics
  @inline def max(a: scala.Float, b: scala.Float): scala.Float = js.Math.max(a, b).toFloat
  @inline def max(a: scala.Double, b: scala.Double): scala.Double = js.Math.max(a, b)

  @inline def min(a: scala.Int, b: scala.Int): scala.Int = if (a < b) a else b
  @inline def min(a: scala.Long, b: scala.Long): scala.Long = if (a < b) a else b

  // Wasm intrinsics
  @inline def min(a: scala.Float, b: scala.Float): scala.Float = js.Math.min(a, b).toFloat
  @inline def min(a: scala.Double, b: scala.Double): scala.Double = js.Math.min(a, b)

  // Wasm intrinsics
  @inline def ceil(a: scala.Double): scala.Double = js.Math.ceil(a)
  @inline def floor(a: scala.Double): scala.Double = js.Math.floor(a)

  // Wasm intrinsic
  def rint(a: scala.Double): scala.Double = {
    /* Is the integer-valued `x` odd? Fused by hand of `(x.toLong & 1L) != 0L`.
     * Corner cases: returns false for Infinities and NaN.
     */
    @inline def isOdd(x: scala.Double): scala.Boolean =
      (x.asInstanceOf[js.Dynamic] & 1.asInstanceOf[js.Dynamic]).asInstanceOf[Int] != 0

    /* js.Math.round(a) does *almost* what we want. It rounds to nearest,
     * breaking ties *up*. We need to break ties to *even*. So we need to
     * detect ties, and for them, detect if we rounded to odd instead of even.
     *
     * The reasons why the apparently simple algorithm below works are subtle,
     * and vary a lot depending on the range of `a`:
     *
     * - a is NaN
     *   r is NaN, then the == is false
     *   -> return r
     *
     * - a is +-Infinity
     *   r == a, then == is true! but isOdd(r) is false
     *   -> return r
     *
     * - 2**53 <= abs(a) < Infinity
     *   r == a, r - 0.5 rounds back to a so == is true!
     *   fortunately, isOdd(r) is false because all a >= 2**53 are even
     *   -> return r
     *
     * - 2**52 <= abs(a) < 2**53
     *   r == a (because all a's are integers in that range)
     *   - a is odd
     *     r - 0.5 rounds down (towards even) to r - 1.0
     *     so a == r - 0.5 is false
     *     -> return r
     *   - a is even
     *     r - 0.5 rounds back up! (towards even) to r
     *     so a == r - 0.5 is true!
     *     but, isOdd(r) is false
     *     -> return r
     *
     * - 0.5 < abs(a) < 2**52
     *   then -2**52 + 0.5 <= a <= 2**52 - 0.5 (because values in-between are not representable)
     *   since Math.round rounds *up* on ties, r is an integer in the range (-2**52, 2**52]
     *   r - 0.5 is therefore lossless
     *   so a == r - 0.5 accurately detects ties, and isOdd(r) breaks ties
     *   -> return `r`` or `r - 1.0`
     *
     * - a == +0.5
     *   r == 1.0
     *   a == r - 0.5 is true and isOdd(r) is true
     *   -> return `r - 1.0`, which is +0.0
     *
     * - a == -0.5
     *   r == -0.0
     *   a == r - 0.5 is true and isOdd(r) is false
     *   -> return `r`, which is -0.0
     *
     * - 0.0 <= abs(a) < 0.5
     *   r == 0.0 with the same sign as a
     *   a == r - 0.5 is false
     *   -> return r
     */
    val r = js.Math.round(a)
    if ((a == r - 0.5) && isOdd(r))
      r - 1.0
    else
      r
  }

  @inline def round(a: scala.Float): scala.Int = js.Math.round(a).toInt
  @inline def round(a: scala.Double): scala.Long = js.Math.round(a).toLong

  // Wasm intrinsic
  @inline def sqrt(a: scala.Double): scala.Double = js.Math.sqrt(a)

  @inline def pow(a: scala.Double, b: scala.Double): scala.Double = js.Math.pow(a, b)

  @inline def exp(a: scala.Double): scala.Double = js.Math.exp(a)
  @inline def log(a: scala.Double): scala.Double = js.Math.log(a)

  @inline def log10(a: scala.Double): scala.Double = {
    if (assumingES6 || !Utils.isUndefined(g.Math.log10))
      js.Math.log10(a)
    else
      log(a) / 2.302585092994046
  }

  @inline def log1p(a: scala.Double): scala.Double = {
    if (assumingES6 || !Utils.isUndefined(g.Math.log1p))
      js.Math.log1p(a)
    else if (a == 0.0) a
    else log(a + 1)
  }

  @inline def sin(a: scala.Double): scala.Double = js.Math.sin(a)
  @inline def cos(a: scala.Double): scala.Double = js.Math.cos(a)
  @inline def tan(a: scala.Double): scala.Double = js.Math.tan(a)
  @inline def asin(a: scala.Double): scala.Double = js.Math.asin(a)
  @inline def acos(a: scala.Double): scala.Double = js.Math.acos(a)
  @inline def atan(a: scala.Double): scala.Double = js.Math.atan(a)
  @inline def atan2(y: scala.Double, x: scala.Double): scala.Double = js.Math.atan2(y, x)

  @inline def random(): scala.Double =
    if (LinkingInfo.targetPureWasm) WasmSystem.random()
    else js.Math.random()

  @inline def toDegrees(a: scala.Double): scala.Double = a * 180.0 / PI
  @inline def toRadians(a: scala.Double): scala.Double = a / 180.0 * PI

  @inline def signum(a: scala.Double): scala.Double = {
    if (a > 0) 1.0
    else if (a < 0) -1.0
    else a
  }

  @inline def signum(a: scala.Float): scala.Float = {
    if (a > 0) 1.0f
    else if (a < 0) -1.0f
    else a
  }

  def cbrt(a: scala.Double): scala.Double = {
    if (assumingES6 || !Utils.isUndefined(g.Math.cbrt)) {
      js.Math.cbrt(a)
    } else {
      if (a == 0 || Double.isNaN(a) || Double.isInfinite(a)) {
        a
      } else {
        val sign = if (a < 0.0) -1.0 else 1.0
        val value = sign * a

        //Initial Approximation
        var x = 0.0
        var xi = pow(value, 0.3333333333333333)

        //Halley's Method (http://metamerist.com/cbrt/cbrt.htm)
        while (abs(x - xi) >= 1E-16) {
          x = xi
          val x3 = js.Math.pow(x, 3)
          val x3Plusa = x3 + value
          xi = x * (x3Plusa + value) / (x3Plusa + x3)
        }
        sign * xi
      }
    }
  }

  def nextUp(a: scala.Double): scala.Double = {
    if (a != a || a == scala.Double.PositiveInfinity) {
      a
    } else if (a == -0.0) { // also matches +0.0 but that's fine
      scala.Double.MinPositiveValue
    } else {
      val abits = Double.doubleToLongBits(a)
      val rbits = if (a > 0) abits + 1L else abits - 1L
      Double.longBitsToDouble(rbits)
    }
  }

  def nextUp(a: scala.Float): scala.Float = {
    if (a != a || a == scala.Float.PositiveInfinity) {
      a
    } else if (a == -0.0f) { // also matches +0.0f but that's fine
      scala.Float.MinPositiveValue
    } else {
      val abits = Float.floatToIntBits(a)
      val rbits = if (a > 0) abits + 1 else abits - 1
      Float.intBitsToFloat(rbits)
    }
  }

  def nextDown(a: scala.Double): scala.Double = {
    if (a != a || a == scala.Double.NegativeInfinity) {
      a
    } else if (a == 0.0) { // also matches -0.0 but that's fine
      -scala.Double.MinPositiveValue
    } else {
      val abits = Double.doubleToLongBits(a)
      val rbits = if (a > 0) abits - 1L else abits + 1L
      Double.longBitsToDouble(rbits)
    }
  }

  def nextDown(a: scala.Float): scala.Float = {
    if (a != a || a == scala.Float.NegativeInfinity) {
      a
    } else if (a == 0.0f) { // also matches -0.0f but that's fine
      -scala.Float.MinPositiveValue
    } else {
      val abits = Float.floatToIntBits(a)
      val rbits = if (a > 0) abits - 1 else abits + 1
      Float.intBitsToFloat(rbits)
    }
  }

  def nextAfter(a: scala.Double, b: scala.Double): scala.Double = {
    if (b > a)
      nextUp(a)
    else if (b < a)
      nextDown(a)
    else if (a != a)
      scala.Double.NaN
    else
      b
  }

  def nextAfter(a: scala.Float, b: scala.Double): scala.Float = {
    if (b > a)
      nextUp(a)
    else if (b < a)
      nextDown(a)
    else if (a != a)
      scala.Float.NaN
    else
      b.toFloat
  }

  def ulp(a: scala.Double): scala.Double = {
    val absa = abs(a)
    if (absa == scala.Double.PositiveInfinity)
      scala.Double.PositiveInfinity
    else if (absa == scala.Double.MaxValue)
      1.9958403095347198e292
    else
      nextUp(absa) - absa // this case handles NaN as well
  }

  def ulp(a: scala.Float): scala.Float = {
    val absa = abs(a)
    if (absa == scala.Float.PositiveInfinity)
      scala.Float.PositiveInfinity
    else if (absa == scala.Float.MaxValue)
      2.028241e31f
    else
      nextUp(absa) - absa // this case handles NaN as well
  }

  def hypot(a: scala.Double, b: scala.Double): scala.Double = {
    if (assumingES6 || !Utils.isUndefined(g.Math.hypot)) {
      js.Math.hypot(a, b)
    } else {
      // http://en.wikipedia.org/wiki/Hypot#Implementation
      if (abs(a) == scala.Double.PositiveInfinity || abs(b) == scala.Double.PositiveInfinity)
        scala.Double.PositiveInfinity
      else if (Double.isNaN(a) || Double.isNaN(b))
        scala.Double.NaN
      else if (a == 0 && b == 0)
        0.0
      else {
        //To Avoid Overflow and UnderFlow
        // calculate |x| * sqrt(1 - (y/x)^2) instead of sqrt(x^2 + y^2)
        val x = abs(a)
        val y = abs(b)
        val m = max(x, y)
        val t = min(x, y) / m
        m * sqrt(1 + t * t)
      }
    }
  }

  def expm1(a: scala.Double): scala.Double = {
    if (assumingES6 || !Utils.isUndefined(g.Math.expm1)) {
      js.Math.expm1(a)
    } else {
      // https://github.com/ghewgill/picomath/blob/master/javascript/expm1.js
      if (a == 0 || Double.isNaN(a))
        a
      // Power Series http://en.wikipedia.org/wiki/Power_series
      // for small values of a, exp(a) = 1 + a + (a*a)/2
      else if (abs(a) < 1E-5)
        a + 0.5 * a * a
      else
        exp(a) - 1.0
    }
  }

  def sinh(a: scala.Double): scala.Double = {
    if (assumingES6 || !Utils.isUndefined(g.Math.sinh)) {
      js.Math.sinh(a)
    } else {
      if (Double.isNaN(a) || a == 0.0 || abs(a) == scala.Double.PositiveInfinity) a
      else (exp(a) - exp(-a)) / 2.0
    }
  }

  def cosh(a: scala.Double): scala.Double = {
    if (assumingES6 || !Utils.isUndefined(g.Math.cosh)) {
      js.Math.cosh(a)
    } else {
      if (Double.isNaN(a))
        a
      else if (a == 0.0)
        1.0
      else if (abs(a) == scala.Double.PositiveInfinity)
        scala.Double.PositiveInfinity
      else
        (exp(a) + exp(-a)) / 2.0
    }
  }

  def tanh(a: scala.Double): scala.Double = {
    if (assumingES6 || !Utils.isUndefined(g.Math.tanh)) {
      js.Math.tanh(a)
    } else {
      if (Double.isNaN(a) || a == 0.0)
        a
      else if (abs(a) == scala.Double.PositiveInfinity)
        signum(a)
      else {
        // sinh(a) / cosh(a) =
        // 1 - 2 * (exp(-a)/ (exp(-a) + exp (a)))
        val expma = exp(-a)
        if (expma == scala.Double.PositiveInfinity) //Infinity / Infinity
          -1.0
        else {
          val expa = exp(a)
          val ret = expma / (expa + expma)
          1.0 - (2.0 * ret)
        }
      }
    }
  }

  def addExact(a: scala.Int, b: scala.Int): scala.Int = {
    val res = a + b
    val resSgnBit = res < 0
    if (resSgnBit == (a < 0) || resSgnBit == (b < 0)) res
    else throw new ArithmeticException("Integer overflow")
  }

  def addExact(a: scala.Long, b: scala.Long): scala.Long = {
    val res = a + b
    val resSgnBit = res < 0
    if (resSgnBit == (a < 0) || resSgnBit == (b < 0)) res
    else throw new ArithmeticException("Long overflow")
  }

  def subtractExact(a: scala.Int, b: scala.Int): scala.Int = {
    val res = a - b
    val resSgnBit = res < 0
    if (resSgnBit == (a < 0) || resSgnBit == (b > 0)) res
    else throw new ArithmeticException("Integer overflow")
  }

  def subtractExact(a: scala.Long, b: scala.Long): scala.Long = {
    val res = a - b
    val resSgnBit = res < 0
    if (resSgnBit == (a < 0) || resSgnBit == (b > 0)) res
    else throw new ArithmeticException("Long overflow")
  }

  def multiplyExact(a: scala.Int, b: scala.Int): scala.Int = {
    val overflow = {
      if (b > 0)
        a > Integer.MAX_VALUE / b || a < Integer.MIN_VALUE / b
      else if (b < -1)
        a > Integer.MIN_VALUE / b || a < Integer.MAX_VALUE / b
      else if (b == -1)
        a == Integer.MIN_VALUE
      else
        false
    }
    if (!overflow) a * b
    else throw new ArithmeticException("Integer overflow")
  }

  @inline
  def multiplyExact(a: scala.Long, b: scala.Int): scala.Long =
    multiplyExact(a, b.toLong)

  def multiplyExact(a: scala.Long, b: scala.Long): scala.Long = {
    val overflow = {
      if (b > 0)
        a > Long.MAX_VALUE / b || a < Long.MIN_VALUE / b
      else if (b < -1)
        a > Long.MIN_VALUE / b || a < Long.MAX_VALUE / b
      else if (b == -1)
        a == Long.MIN_VALUE
      else
        false
    }
    if (!overflow) a * b
    else throw new ArithmeticException("Long overflow")
  }

  def incrementExact(a: scala.Int): scala.Int =
    if (a != Integer.MAX_VALUE) a + 1
    else throw new ArithmeticException("Integer overflow")

  def incrementExact(a: scala.Long): scala.Long =
    if (a != Long.MAX_VALUE) a + 1
    else throw new ArithmeticException("Long overflow")

  def decrementExact(a: scala.Int): scala.Int =
    if (a != Integer.MIN_VALUE) a - 1
    else throw new ArithmeticException("Integer overflow")

  def decrementExact(a: scala.Long): scala.Long =
    if (a != Long.MIN_VALUE) a - 1
    else throw new ArithmeticException("Long overflow")

  def negateExact(a: scala.Int): scala.Int =
    if (a != Integer.MIN_VALUE) -a
    else throw new ArithmeticException("Integer overflow")

  def negateExact(a: scala.Long): scala.Long =
    if (a != Long.MIN_VALUE) -a
    else throw new ArithmeticException("Long overflow")

  def toIntExact(a: scala.Long): scala.Int =
    if (a >= Integer.MIN_VALUE && a <= Integer.MAX_VALUE) a.toInt
    else throw new ArithmeticException("Integer overflow")

  // RuntimeLong intrinsic
  @inline
  def multiplyFull(x: scala.Int, y: scala.Int): scala.Long =
    x.toLong * y.toLong

  @inline
  def multiplyHigh(x: scala.Long, y: scala.Long): scala.Long = {
    /* Hacker's Delight, Section 8-2, Figure 8-2,
     * where we have "inlined" all the variables used only once to help our
     * optimizer perform simplifications.
     */

    val x0 = x & 0xffffffffL
    val x1 = x >> 32
    val y0 = y & 0xffffffffL
    val y1 = y >> 32

    val t = x1 * y0 + ((x0 * y0) >>> 32)
    x1 * y1 + (t >> 32) + (((t & 0xffffffffL) + x0 * y1) >> 32)
  }

  @inline
  def unsignedMultiplyHigh(x: scala.Long, y: scala.Long): scala.Long = {
    /* Hacker's Delight, Section 8-2:
     * > For an unsigned version, simply change all the int declarations to unsigned.
     * In Scala, that means changing all the >> into >>>.
     */

    val x0 = x & 0xffffffffL
    val x1 = x >>> 32
    val y0 = y & 0xffffffffL
    val y1 = y >>> 32

    val t = x1 * y0 + ((x0 * y0) >>> 32)
    x1 * y1 + (t >>> 32) + (((t & 0xffffffffL) + x0 * y1) >>> 32)
  }

  def floorDiv(a: scala.Int, b: scala.Int): scala.Int = {
    val quot = a / b
    if ((a < 0) == (b < 0) || quot * b == a) quot
    else quot - 1
  }

  @inline
  def floorDiv(a: scala.Long, b: scala.Int): scala.Long =
    floorDiv(a, b.toLong)

  def floorDiv(a: scala.Long, b: scala.Long): scala.Long = {
    val quot = a / b
    if ((a < 0) == (b < 0) || quot * b == a) quot
    else quot - 1
  }

  def floorMod(a: scala.Int, b: scala.Int): scala.Int = {
    val rem = a % b
    if ((a < 0) == (b < 0) || rem == 0) rem
    else rem + b
  }

  @inline
  def floorMod(a: scala.Long, b: scala.Int): scala.Int =
    floorMod(a, b.toLong).toInt

  def floorMod(a: scala.Long, b: scala.Long): scala.Long = {
    val rem = a % b
    if ((a < 0) == (b < 0) || rem == 0) rem
    else rem + b
  }

  // TODO

  // def IEEEremainder(f1: scala.Double, f2: scala.Double): Double
  // def copySign(magnitude: scala.Double, sign: scala.Double): scala.Double
  // def copySign(magnitude: scala.Float, sign: scala.Float): scala.Float
  // def getExponent(a: scala.Float): scala.Int
  // def getExponent(a: scala.Double): scala.Int
  // def scalb(a: scala.Double, scalaFactor: scala.Int): scala.Double
  // def scalb(a: scala.Float, scalaFactor: scala.Int): scala.Float
}
