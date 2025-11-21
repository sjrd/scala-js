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

    val (scaledX, high, k0) = if (high0 < 0x00100000) {
      if (((high0 & 0x7fffffff) | low) == 0) {
        return -Two54 / 0.0  // log(+-0)=-inf
      }
      if (high0 < 0) {
        return (x - x) / 0.0  // log(-#) = NaN
      }
      val scaledX = x * Two54
      val scaledHigh = (JDouble.doubleToRawLongBits(scaledX) >>> 32).toInt
      (scaledX, scaledHigh, -54)
    } else {
      (x, high0, 0)
    }
    if (high >= 0x7ff00000) { // x is inf or NaN
      return x + x
    }
    val highMantissa = high & 0x000fffff // top 20 bits of mantissa

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
    val bits = JDouble.doubleToRawLongBits(scaledX)
    /* `(highMantissa | (i ^ 0x3ff00000)).toLong << 32)` sets the exponent to
     * 0 if the original mantissa (1+f) was smaller than √2, otherwise -1.
     * newMantissa =
     *   2^0 * (1+f)    (when (1+f) < √2)
     *   2^(-1) * (1+f) (when (1+f) >= √2)
     *
     * Now, x = 2^newExponent * newMantissa
     */
    val newMantissa =
        JDouble.longBitsToDouble(((highMantissa | (i ^ 0x3ff00000)).toLong << 32) | (bits & 0x00000000ffffffffL))
    val newExponent = k0 + ((high >> 20) - 1023) + (i >> 20)
    val f = newMantissa - 1.0

    /* Fast path: newMantissa is very close to 1.0 (`|newMantissa - 1.0| < 2^(-20)`).
     * For example:
     * | x (f=x−1)    | high     | 2+high   | masked | < 3   |
     * |--------------+----------+----------+--------+-------|
     * | 1.0+2−20     | 3FF00001 | 3FF00003 |   3    | false |
     * | 1.0          | 3FF00000 | 3FF00002 |   2    | true  |
     * | 1.0-2^21     | 3FEFFFFF | 3FF00001 |   1    | true  |
     * | 1.0−2^20     | 3FEFFFFE | 3FF00000 |   0    | true  |
     * | 1.0−1.5×2^20 | 3FEFFFFD | 3FEFFFFF | FFFFF  | false |
     */
    if ((0x000fffff & (2 + highMantissa)) < 3) {
      if (f == 0.0) {
        if (newExponent == 0) {
          0.0
        } else {
          // ln(x) =  ln(2^newExponent) + ln(newMantissa) (where newMantissa =~ 1.0)
          //       =~ newExponent * ln2 + 0.0
          val dk = newExponent.toDouble
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
        if (newExponent == 0) {
          return f - r
        } else {
          val dk = newExponent.toDouble
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
      val dk = newExponent.toDouble
      // f is somewhere between approximately 0.38 and 0.42 (f is large).
      // Use log(1+f) = f - (hfsq - s*(hfsq+R)) for better accuracy.
      if ((highMantissa - 0x6147a | 0x6b851 - highMantissa) > 0) {
        val hfsq = 0.5 * f * f
        if (newExponent == 0) {
          f - (hfsq - s * (hfsq + approx))
        } else {
          dk * Ln2High - ((hfsq - (s * (hfsq + approx) + dk * Ln2Low)) - f)
        }
      } else {
        if (newExponent == 0) {
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

    val (scaledX, high, k0) = if (high0 < 0x00100000) { // x < 2**-1022 (subnormal)
      if (((high0 & 0x7fffffff) | low) == 0) {
        return -Two54 / 0.0 // log(+-0)=-inf
      }
      if (high0 < 0) {
        return (x - x) / 0.0 // log(-#) = NaN
      }
      val scaledX = x * Two54 // subnormal number, scale up x
      val scaledHigh = (JDouble.doubleToRawLongBits(scaledX) >>> 32).toInt
      (scaledX, scaledHigh, -54)
    } else {
      (x, high0, 0)
    }
    if (high >= 0x7ff00000) return x + x // Inf or NaN

    /* Normalize x = 2^newExponent * newMantissa
     * log10(x) = log10(2^newExponent * newMantissa)
     *          = newExponent * log10(2) + log10(newMantissa)
     *          = newExponent * log10(2) + ln(newMantissa) / ln(10)
     * where newMantissa ∈ [0.5, 2.0).
     */
    val exponent = k0 + (high >> 20) - 1023
    val i = ((exponent & 0x80000000) >>> 31) // if (exponent>=0) 0 else 1
    val newExponent = (exponent + i).toDouble
    val newMantissaBits = // exponent will be 0 if i=0, otherwise -1
      ((((0x3ff - i) << 20) | (high & 0x000fffff)).toLong << 32) |
          (JDouble.doubleToRawLongBits(scaledX) & 0x00000000ffffffffL)
    val newMantissa = JDouble.longBitsToDouble(newMantissaBits)

    newExponent * Log10Of2Lo +
        newExponent * Log10Of2Hi +
        Ivln10 * log(newMantissa)
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
          return -Two54 / 0.0 // log1p(-1) = log(0) = -inf
        } else {
          return (x - x) / (x - x) // log1p(x<-1) = NaN
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
      if (high > 0 || high <= 0xbfd2bec3.toInt) {
        /* -0.2929 < x < 0.41422
         * sqrt(2)/2 < 1+x < sqrt(2), no argument reduction needed.
         */
        false
      } else {
        true
      }
    } else {
      true // x >= 0.41422, need argument reduction
    }
    if (high >= 0x7ff00000) return x + x // x is inf or NaN

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
     * Then, normalize to 1+x = 2^newExponent * (1+f)
     * where f is close to 0, suitable for polynomial approximation.
     * We adjust the exponent field to bring the value into [sqrt(2)/2, sqrt(2)).
     */
    val (correction, newExponent, f, newHighMantissa) =
      if (needReduction) {
        val (exponent0, u, corr, highMantissa) =
          if (high < 0x43400000) { // x < 2^53
            val u = 1.0 + x
            val uBits = JDouble.doubleToRawLongBits(u)
            val uHigh = (uBits >>> 32).toInt
            val exp = (uHigh >> 20) - 1023
            val corr = if (exp > 0) 1.0 - (u - x) else x - (u - 1.0)
            (exp, uBits, corr / u, uHigh & 0x000fffff)
          } else {
            /* For x >= 2^53, 1+x = x in floating point (the +1 is below precision).
             * So log(1+x) = log(x), and we just use x directly with no correction needed.
             */
            val xBits = JDouble.doubleToRawLongBits(x)
            val xHigh = (xBits >>> 32).toInt
            ((xHigh >> 20) - 1023, xBits, 0.0, xHigh & 0x000fffff)
          }

        if (highMantissa < 0x6a09e) { // mantissa < √2
          // Set exponent to 0, so normalized value is in [1.0, sqrt(2)).
          val newBits = ((highMantissa | 0x3ff00000).toLong << 32) | (u & 0x00000000ffffffffL)
          (corr, exponent0, JDouble.longBitsToDouble(newBits) - 1.0, highMantissa)
        } else { // mantissa >= √2
          // set exponent to -1 (2^-1 = 0.5) and increment final exponent.
          val newBits = ((highMantissa | 0x3fe00000).toLong << 32) | (u & 0x00000000ffffffffL)
          (corr, exponent0 + 1, JDouble.longBitsToDouble(newBits) - 1.0, (0x00100000 - highMantissa) >> 2)
        }
      } else {
        (0.0, 0, x, 1)
      }

    val hfsq = 0.5 * f * f
    // Fast path: when |f| < 2^-20, use simpler approximation.
    if (newHighMantissa == 0) { // |f| < 2^-20
      if (f == 0.0) {
        if (newExponent == 0) {
          return 0.0
        } else {
          // log(1+x) = log(2^newExponent * (1+f)) = newExponent*ln(2) when f = 0
          val c = correction + newExponent * Ln2Low
          return newExponent * Ln2High + c
        }
      }
      /* Third-order Taylor expansion: log(1+f) ≈ f - f^2/2 + f^3/3
       * Rewritten as: f - (f^2/2)*(1 - 2f/3) for better accuracy.
       */
      val approx = hfsq * (1.0 - 0.66666666666666666 * f)
      if (newExponent == 0) {
        return f - approx
      } else {
        return newExponent * Ln2High - ((approx - (newExponent * Ln2Low + correction)) - f)
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
    if (newExponent == 0) {
      f - (hfsq - s * (hfsq + approx))
    } else {
      newExponent * Ln2High - ((hfsq - (s * (hfsq + approx) + (newExponent * Ln2Low + correction))) - f)
    }
  }
}
