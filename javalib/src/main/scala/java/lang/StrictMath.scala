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

/*
 * ====================================================
 * Copyright (C) 1993 by Sun Microsystems, Inc. All rights reserved.
 *
 * Developed at SunSoft, a Sun Microsystems, Inc. business.
 * Permission to use, copy, modify, and distribute this
 * software is freely granted, provided that this notice
 * is preserved.
 * ====================================================
 */

package java.lang

import java.lang.{Double => JDouble}

object StrictMath {

  private final val SignBit = scala.Long.MinValue
  private final val SignBitInt = scala.Int.MinValue

  // Port from https://www.netlib.org/fdlibm/e_log.c
  def log(x: scala.Double): scala.Double = {
    val Ln2High = 6.93147180369123816490e-01
    val Ln2Low = 1.90821492927058770002e-10
    val Two54 = 1.80143985094819840000e+16 // 2^54
    val Lg1 = 6.666666666666735130e-01
    val Lg2 = 3.999999999940941908e-01
    val Lg3 = 2.857142874366239149e-01
    val Lg4 = 2.222219843214978396e-01
    val Lg5 = 1.818357216161805012e-01
    val Lg6 = 1.531383769920937332e-01
    val Lg7 = 1.479819860511658591e-01

    val bits0 = JDouble.doubleToRawLongBits(x)
    val high0 = (bits0 >>> 32).toInt
    val low = bits0.toInt

    val (scaledX, k0) = if (high0 < 0x100000) {
      if ((bits0 & ~SignBit) == 0L) {
        return scala.Double.NegativeInfinity // log(+-0)=-inf
      }
      if (high0 < 0) {
        return scala.Double.NaN // log(-#) = NaN
      }
      (x * Two54, -54)
    } else {
      if (high0 >= 0x7ff00000) { // x is inf or NaN
        return x + x
      }
      (x, 0)
    }

    val bits = JDouble.doubleToRawLongBits(scaledX)
    val high = (bits >>> 32).toInt
    val highMantissa = high & 0xfffff // top 20 bits of mantissa

    /* Find k and f such that `x = 2^k * (1+f)`,
     * where `sqrt(2)/2 < 1+f < sqrt(2)`.
     *
     * First, check if mantissa (1+f) is larger than √2 by computing
     * `val i = (highMantissa + 0x95f64) & 0x100000` where
     * 0x6a09c is the top 20 bits approximation of the mantissa of √2
     * and, 0x100000 - 0x6a09c = 0x95f64
     * If mantissa is larger than √2, i is 0x100000, otherwise it's 0.
     */
    val i = (highMantissa + 0x95f64) & 0x100000
    /* `(highMantissa | (i ^ 0x3ff00000)).toLong << 32)` sets the exponent to
     * 0 if the original mantissa (1+f) was smaller than √2, otherwise -1.
     * newX =
     *   2^0 * (1+f)    (when (1+f) < √2)
     *   2^(-1) * (1+f) (when (1+f) >= √2)
     *
     * Now, x = 2^k * newX
     */
    val newX =
      makeDouble((highMantissa | (i ^ 0x3ff00000)), bits)
    val k = k0 + ((high >> 20) - 1023) + (i >> 20)
    val f = newX - 1.0

    /* Fast path: newX is very close to 1.0 (`|newX - 1.0| < 2^(-20)`).
     * For example:
     * | x (f=x−1)    | high     | 2+high   | masked | < 3   |
     * |--------------+----------+----------+--------+-------|
     * | 1.0+2−20     | 3FF00001 | 3FF00003 |   3    | false |
     * | 1.0          | 3FF00000 | 3FF00002 |   2    | true  |
     * | 1.0-2^21     | 3FEFFFFF | 3FF00001 |   1    | true  |
     * | 1.0−2^20     | 3FEFFFFE | 3FF00000 |   0    | true  |
     * | 1.0−1.5×2^20 | 3FEFFFFD | 3FEFFFFF | FFFFF  | false |
     */
    if ((0xfffff & (2 + highMantissa)) < 3) {
      if (f == 0.0) {
        if (k == 0) {
          0.0
        } else {
          // ln(x) =  ln(2^k) + ln(newX) (where newX =~ 1.0)
          //       =~ k * ln2 + 0.0
          val dk = k.toDouble
          dk * Ln2High + dk * Ln2Low
        }
      } else {
        /* R = f*f*(0.5-0.33333333333333333*f);
	     * if(k==0) return f-R; else {dk=(double)k;
	     *   return dk*ln2_hi-((R-dk*ln2_lo)-f);}
         *
         * Third-order Maclaurin expansion of ln(1+f).
         * Since |f| < 2^(-20), the fourth term f^4/4 is approximately 2^(-82),
         * which is far below the precision of a double 2^(-53).
         */
        val r = f * f * (0.5 - 0.33333333333333333 * f)
        if (k == 0) {
          return f - r
        } else {
          val dk = k.toDouble
          /* Adding large and small values can lead to a loss of precision;
           * adjust the order of calculations accordingly.
           * log(x) = k*ln(2) + log(1+f) = k*ln2 + (f - R)
           *        = k*Ln2High + k*Ln2Low + f - R
           */
          dk * Ln2High - ((r - dk * Ln2Low) - f)
        }
      }
    } else {
      // √2/2       < 1+f < √2
      // -0.292.... <  f  < 0.4142...
      // -0.17153.. <  s  < 0.17153...
      val s = f / (2.0 + f)
      val z = s * s
      val w = z * z

      /* ln(x) = k*ln2 + ln(1+f).
       * Since `1+f = (1+s)/(1-s)`,
       * ln(1+f) = ln(1+s) - ln(1-s) = 2s + 2/3*s^3 + 2/5*s^5 + ...
       * because
       * ln(1+s) =  s - (s^2)/2 + (s^3)/3 - (s^4)/4 + ...
       * ln(1-s) = -s - (s^2)/2 - (s^3)/3 - (s^4)/4 + ...
       * and this equals 2*artanh(s).
       *
       * ln(x) = k*ln2 + 2s + s*R, where
       * R = 2/3*s^2 + 2/5*s^4 + 2/7*s^6 + ...
       *   = 2z*(1/3 + 1/5*z^2 + 1/7*z^3 + ...)
       * Note that s*R = 2*artanh(s) - 2s, and
       * R = 2*atanh(s)/s - 2
       *   = 2*atanh(√z)/√z - 2
       * Hence, 2*(1/3 + 1/5*z^2 + 1/7*z^3 + ...) =
       *        (2*atanh(√z)/√z - 2)/z
       *
       * We use Remez on [0, 0.1716^2] to generate
       * a polynomial of degree 6 to approximate (1/3 + 1/5*z^2 + 1/7*z^3 + ...).
       * The maximum error of this polynomial approximation is bounded by 2**-58.45.
       * (In the sollya script below, it was around 2^-56.629, but it is still small enough.)
       * (With d = 5, the maximum error was around 2^-49.38.)
       *
       * ```sollya
       * d = 6;
       * dom = [2^(-100), 0.1716^2];
       * f = (2 * atanh(sqrt(x)) / sqrt(x) - 2) / x;
       * p = remez(f, d, dom);
       * for n from 0 to d do {
       *    c = coeff(p, n);
       *    print("Lg" @ (n+1) @ ":", c);
       * };
       * e = x * p - (2 * atanh(sqrt(x)) / sqrt(x) - 2);
       * m = dirtyinfnorm(e, dom);
       * print(m); // 2^-56.629
       * ```
       * Lg1...Lg7 are pre-calculated coefficients (copied from fdlibm).
       */

      val t1 = w * (Lg2 + w * (Lg4 + w * Lg6))
      val t2 = z * (Lg1 + w * (Lg3 + w * (Lg5 + w * Lg7)))
      val approx = t2 + t1
      /* log(1+f) = s*R + 2s adds a very small value to a relatively large one,
       * which can lead to a loss of precision. To guarantee error in log below 1ulp,
       * we compute log by
       *   log(1+f) = sR * s2
       *     = (2*f + f*R)/(2+f)
       *     = (2*f + f^2 - f^2 + f*R)/(2+f)
       *     = f - s*(f - R)  (if f is not too large)
       *   log(1+f) = sR * 2s
       *     = sR + f - hfsq + s*hfsq
       *     = f - (hrsq - s*(hfsq+R))
       *     = f - (hfsq - s*(hfsq+R))  (better accuracy)
       * Note that 2s = f - s*f = f - hfsq + s*hfsq, where hfsq = f*f/2.
       */
      val dk = k.toDouble
      // f is somewhere between approximately 0.38 and 0.42 (f is large).
      // Use log(1+f) = f - (hfsq - s*(hfsq+R)) for better accuracy.
      if (((highMantissa - 0x6147a) | (0x6b851 - highMantissa)) > 0) {
        val hfsq = 0.5 * f * f
        if (k == 0) {
          f - (hfsq - s * (hfsq + approx))
        } else {
          dk * Ln2High - ((hfsq - (s * (hfsq + approx) + dk * Ln2Low)) - f)
        }
      } else {
        if (k == 0) {
          f - s * (f - approx)
        } else {
          dk * Ln2High - ((s * (f - approx) - dk * Ln2Low) - f)
        }
      }
    }
  }

  // Port from https://www.netlib.org/fdlibm/e_log10.c
  def log10(x: scala.Double): scala.Double = {
    val Two54 = 1.80143985094819840000e+16
    val Ivln10 = 4.34294481903251816668e-01
    val Log10Of2Hi = 3.01029995663611771306e-01
    val Log10Of2Lo = 3.69423907715893078616e-13

    val bits = JDouble.doubleToRawLongBits(x)
    val high0 = (bits >>> 32).toInt
    val low = bits.toInt

    val (scaledX, k0) = if (high0 < 0x100000) { // x < 2**-1022 (subnormal)
      if ((bits & ~SignBit) == 0L) {
        return scala.Double.NegativeInfinity // log(+-0)=-inf
      }
      if (high0 < 0) {
        return scala.Double.NaN // log(-#) = NaN
      }
      (x * Two54, -54)
    } else {
      if (high0 >= 0x7ff00000) {
        return x + x // Inf or NaN
      }
      (x, 0)
    }
    val high = (JDouble.doubleToRawLongBits(scaledX) >>> 32).toInt

    /* Normalize x = 2^k * newX
     * log10(x) = log10(2^k * newX)
     *          = k * log10(2) + log10(newX)
     *          = k * log10(2) + ln(newX) / ln(10)
     * where newX ∈ [0.5, 2.0).
     */
    val exponent = k0 + (high >> 20) - 1023
    val i = ((exponent & 0x80000000) >>> 31) // if (exponent>=0) 0 else 1
    val k = (exponent + i).toDouble
    val newX = // exponent will be 0 if i=0, otherwise -1
      makeDouble(((0x3ff - i) << 20) | (high & 0xfffff), JDouble.doubleToRawLongBits(scaledX))
    k * Log10Of2Lo + k * Log10Of2Hi + Ivln10 * log(newX)
  }

  // Port from https://www.netlib.org/fdlibm/s_log1p.c
  def log1p(x: scala.Double): scala.Double = {
    val Ln2High = 6.93147180369123816490e-01
    val Ln2Low = 1.90821492927058770002e-10
    val Two54 = 1.80143985094819840000e+16
    val Lp1 = 6.666666666666735130e-01
    val Lp2 = 3.999999999940941908e-01
    val Lp3 = 2.857142874366239149e-01
    val Lp4 = 2.222219843214978396e-01
    val Lp5 = 1.818357216161805012e-01
    val Lp6 = 1.531383769920937332e-01
    val Lp7 = 1.479819860511658591e-01

    val bits = JDouble.doubleToRawLongBits(x)
    val high = (bits >>> 32).toInt
    val absHigh = high & 0x7fffffff

    val needReduction = if (high < 0x3fda827a) { // x < 0.41422
      if (absHigh >= 0x3ff00000) { // x <= -1.0
        if (x == -1.0) {
          return scala.Double.NegativeInfinity // log1p(-1) = log(0) = -inf
        } else {
          return scala.Double.NaN // log1p(x<-1) = NaN
        }
      }
      if (absHigh < 0x3e200000) { // |x| < 2^-29
        // For very small |x|, use Taylor expansion: log(1+x) = x - x^2/2 + ...
        if (absHigh < 0x3c900000) { // |x| < 2^-54
          return x
        } else {
          return x - x * x * 0.5
        }
      }
      if (high > 0 || high <= 0xbfd2bec3) {
        /* -0.2929 < x < 0.41422
         * sqrt(2)/2 < 1+x < sqrt(2), no argument reduction needed.
         */
        false
      } else {
        true
      }
    } else {
      if (high >= 0x7ff00000) {
        return x + x // x is inf or NaN
      }
      true // x >= 0.41422, need argument reduction
    }

    /* 1. Argument Reduction: find k and f such that
     * 1+x = 2^k * (1+f), where  sqrt(2)/2 < 1+f < sqrt(2).
     *
     * Note: If k=0 (needReduction=false), then f=x is exact.
     * However, if k!=0, then f may not be representable exactly.
     * In that case, a correction term is needed.
     * Let u = 1+x rounded, and c = (1+x)-u, then
     * log(1+x) - log(u) =~ c/u, because
     *   log(1+x) = log(u + c)
     *            = log(u * (1 + c/u))
     *            = log(u) + log(1 + c/u)
     *   By taylor expansion on log(1+c/u)
     *   log(1 + c/u) ≈ c/u - (c/u)²/2 + ... =~ c/u
     *   log(1+x) ≈ log(u) + c/u
     *
     * Thus, we proceed to compute log(u),
     * and add back the correction term c/u.
     * (Note: when x > 2^53, one can simply return log(x))
     *
     * Then, normalize to 1+x = 2^k * (1+f)
     * where f is close to 0, suitable for polynomial approximation.
     * We adjust the exponent field to bring the value into [sqrt(2)/2, sqrt(2)).
     */
    val (correction, k, f, newHighMantissa) = {
      if (needReduction) {
        val (exponent0, u, corr, highMantissa) = {
          if (high < 0x43400000) { // x < 2^53
            val u = 1.0 + x
            val uBits = JDouble.doubleToRawLongBits(u)
            val uHigh = (uBits >>> 32).toInt
            val exp = (uHigh >> 20) - 1023
            val corr = if (exp > 0) 1.0 - (u - x) else x - (u - 1.0)
            (exp, uBits, corr / u, uHigh & 0xfffff)
          } else {
            /* For x >= 2^53, 1+x = x in floating point (the +1 is below precision).
             * So log(1+x) = log(x), and we just use x directly with no correction needed.
             */
            val xBits = JDouble.doubleToRawLongBits(x)
            val xHigh = (xBits >>> 32).toInt
            ((xHigh >> 20) - 1023, xBits, 0.0, xHigh & 0xfffff)
          }
        }

        if (highMantissa < 0x6a09e) { // mantissa < √2
          // Set exponent to 0, so normalized value is in [1.0, sqrt(2)).
          (corr, exponent0, makeDouble(highMantissa | 0x3ff00000, u) - 1.0, highMantissa)
        } else { // mantissa >= √2
          (corr, exponent0 + 1, makeDouble(highMantissa | 0x3fe00000, u) - 1.0, (0x100000 - highMantissa) >> 2)
        }
      } else {
        (0.0, 0, x, 1)
      }
    }

    val hfsq = 0.5 * f * f
    // Fast path: when |f| < 2^-20, use simpler approximation.
    if (newHighMantissa == 0) { // |f| < 2^-20
      if (f == 0.0) {
        if (k == 0) {
          return 0.0
        } else {
          // log(1+x) = log(2^k * (1+f)) = k*ln(2) when f = 0
          val c = correction + k * Ln2Low
          return k * Ln2High + c
        }
      }
      /* Third-order Taylor expansion: log(1+f) ≈ f - f^2/2 + f^3/3
       * Rewritten as: f - (f^2/2)*(1 - 2f/3) for better accuracy.
       */
      val approx = hfsq * (1.0 - 0.66666666666666666 * f)
      if (k == 0) {
        return f - approx
      } else {
        return k * Ln2High - ((approx - (k * Ln2Low + correction)) - f)
      }
    }

    /* 2. Approximation of log1p(f).
     * Full polynomial approximation for log(1+f).
     * Let s = f/(2+f), then 1+f = (1+s)/(1-s), so:
     * log(1+f) = log(1+s) - log(1-s) = 2s + 2/3*s^3 + 2/5*s^5 + ... = 2s + s*R
     * where R is approximated by a polynomial in z = s^2.
     */
    val s = f / (2.0 + f)
    val z = s * s
    val approx = z * (Lp1 + z * (Lp2 + z * (Lp3 + z * (Lp4 + z * (Lp5 + z * (Lp6 + z * Lp7))))))

    /* 3. Final result: log(1+x) = k*ln(2) + log(1+f)
     * where log(1+f) = f - (hfsq - s*(hfsq+R))
     */
    if (k == 0) {
      f - (hfsq - s * (hfsq + approx))
    } else {
      k * Ln2High - ((hfsq - (s * (hfsq + approx) + (k * Ln2Low + correction))) - f)
    }
  }

  // Port from https://www.netlib.org/fdlibm/e_sqrt.c
  def sqrt(x: scala.Double): scala.Double = {
    val bits = JDouble.doubleToRawLongBits(x)
    val high0 = (bits >>> 32).toInt
    val low0 = bits.toInt

    if ((high0 & 0x7ff00000) == 0x7ff00000)
      return x * x + x // sqrt(NaN)=NaN, sqrt(+inf)=+inf, sqrt(-inf)=sNaN

    if (high0 <= 0) {
      if (((high0 & (~SignBitInt)) | low0) == 0)
        return x // sqrt(+-0) = +-0
      else if (high0 < 0)
        return scala.Double.NaN // sqrt(-ve) = NaN
    }

    /* 1. Normalization
     * Scale x to y in [1,4) with even powers of 2:
     * find an integer k such that  1 <= (y=x*2^(2k)) < 4, then
     *   sqrt(x) = 2^k * sqrt(y)
     */
    val (high, low, exp0) = {
      val rawE = high0 >> 20
      // subnormal numbers. shift until we find the leading 1 bit.
      if (rawE == 0) {
        var highTmp = high0
        var lowTmp = low0
        var expTmp = 0
        // optimization: shift in 21-bit chunks from low word to high word
        while (highTmp == 0) {
          expTmp -= 21
          highTmp |= (lowTmp >>> 11)
          lowTmp <<= 21
        }
        var i = 0
        while ((highTmp & 0x100000) == 0) {
          highTmp <<= 1
          i += 1
        }
        expTmp -= i - 1
        highTmp |= (lowTmp >>> (32 - i))
        lowTmp <<= i
        (highTmp, lowTmp, expTmp - 1023)
      } else {
        (high0, low0, rawE - 1023)
      }
    }

    /* If exponent is odd, double the mantissa to make exponent even
     * because sqrt(2^e * m) = 2^(e/2) * sqrt(m).
     * We need e to be even for integer division, so if e is odd:
     * 2^e * m = 2^(e-1) * (2*m), now (e-1) is even
     */
    val mHigh0 = (high & 0xfffff) | 0x100000 // implicit leading 1
    val mLow0 = low
    val (mHigh, mLow) = if ((exp0 & 1) == 1) {
      (mHigh0 + mHigh0 + ((mLow0 & SignBitInt) >>> 31), mLow0 + mLow0)
    } else {
      (mHigh0, mLow0)
    }
    val newExp = exp0 >> 1

    /* Step 2: Bit-by-bit computation of sqrt(mantissa) using integer arithmetic
     *
     * After Step 1, we have: `x = 2^e * m`, and `sqrt(x) = 2^(e/2) * sqrt(m)`, where m ∈ [1, 4).
     * here we compute `sqrt(m)` using digit-by-digit calculation:
     * https://en.wikipedia.org/wiki/Square_root_algorithms#Digit-by-digit_calculation
     */
    // Initialize remainder = mantissa * 2 (scaled for bit extraction)
    var remHigh = mHigh + mHigh + ((mLow & SignBitInt) >>> 31)
    var remLow = mLow + mLow

    var q = 0
    var s0 = 0 // 2 * q (for testing next bit)
    var r = 0x200000 // Current bit position (starts at bit 21)
    while (r != 0) {
      // Test if we can set this bit: (result + r)^2 still be <= original value?
      val t = s0 + r
      if (t <= remHigh) {
        s0 = t + r
        remHigh = remHigh - t
        q = q + r
      }
      // Shift remainder left by 1 for next iteration (extract next bit of precision)
      remHigh = remHigh + remHigh + ((remLow & SignBitInt) >>> 31)
      remLow = remLow + remLow
      r = r >>> 1
    }

    var q1 = 0
    var s1 = 0
    r = SignBitInt
    while (r != 0) {
      val t1 = s1 + r
      val t = s0
      if ((t < remHigh) ||
          ((t == remHigh) && Integer.unsigned_<=(t1, remLow))) {
        s1 = t1 + r
        if (((t1 & SignBitInt) == SignBitInt) && ((s1 & SignBitInt) == 0))
          s0 = s0 + 1
        remHigh = remHigh - t
        if (Integer.unsigned_<(remLow, t1))
          remHigh = remHigh - 1
        remLow = remLow - t1
        q1 = q1 + r
      }
      remHigh = remHigh + remHigh + ((remLow & SignBitInt) >>> 31)
      remLow = remLow + remLow
      r = r >>> 1
    }

    // Step 3: Rounding based on remainder
    // Original e_sqrt implementation had a rouding mode check,
    // but here we omit those checks because we always round to even.
    val (resHigh, resLow) = {
      if ((remHigh | remLow) != 0) {
        if (q1 == 0xffffffff) {
          (q + 1, 0)
        } else {
          (q, q1 + (q1 & 1))
        }
      } else {
        (q, q1)
      }
    }

    // Reconstruct the double value
    val newHigh = (resHigh >>> 1) + 0x3fe00000 + (newExp << 20)
    val newLow =
      if ((resHigh & 1) == 1) (resLow >>> 1) | SignBitInt
      else resLow >>> 1
    makeDouble(newHigh, newLow.toLong)
  }

  @inline private def makeDouble(high: Int, low: scala.Long): scala.Double =
    JDouble.longBitsToDouble((high.toLong << 32) | (low & 0xffffffffL))
}
