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

object StrictMath {

  // Ported from https://www.netlib.org/fdlibm/s_floor.c
  def floor(x: scala.Double): scala.Double = {
    var bits = Double.doubleToRawLongBits(x)
    val exponent = (((bits >>> 52).toInt) & 0x7ff) - 1023

    if (exponent < 0) { // |x| < 1
      /* Note: In the original C implementation, code block below was surrounded by
       * "if (huge + x > 0.0)" (huge = 1.0e300),
       * which was used to raise the IEEE 754 inexact flag.
       * In Java, there is no standard way to access these flags,
       * so this check has no practical effect(?) and commented out.
       */
      // if (huge + x > 0.0) {
      if (bits >= 0) {
        0.0
      } else if ((bits & 0x7fffffffffffffffL) != 0L) {
        -1.0
      } else { // -0.0
        x
      }
    } else if (exponent > 51) { // Very large numbers or special values
      if (exponent == 0x400) { // Infinity or NaN
        x + x // returns qNaN
      } else {
        x
      }
    } else { // 0 <= exponent <= 51
      val fractionalMask = 0x000fffffffffffffL >>> exponent
      if ((bits & fractionalMask) == 0L) { // x is integral
        x
      } else {
        // if (huge + x > 0.0) { // Raise inexact flag
        val adjustedBits = if (bits < 0) {
          // increment the integer part (and then clear the fractional part)
          // 0x0010000000000000L is the bit right after the integer part
          bits + (0x0010000000000000L >>> exponent)
        } else {
          bits
        }
        // Clear fractional bits
        Double.longBitsToDouble(adjustedBits & ~fractionalMask)
      }
    }
  }

  // Ported from https://www.netlib.org/fdlibm/s_ceil.c
  def ceil(x: scala.Double): scala.Double = {
    val doubleBits = Double.doubleToLongBits(x)

    var highWord = (doubleBits >>> 32).toInt  // sign(1) + exponent(11) + mantissa_high(20)
    var lowWord = doubleBits.toInt            // mantissa_low(32)
    val exponent = ((highWord >> 20) & 0x7ff) - 1023

    @inline def reconstructDouble(highWord: Int, lowWord: Int): scala.Double = {
      val bits = (highWord.toLong << 32) | (lowWord.toLong & 0xffffffffL)
      Double.longBitsToDouble(bits)
    }

    if (exponent < 20) { // Case 1: decimal point is within the high word.
      if (exponent < 0) { // Case 1a: |x| < 1
        /* Note: In the original C implementation, code block below was surrounded by
         * "if (huge + x > 0.0)" (huge = 1.0e300),
         * which was used to raise the IEEE 754 inexact flag.
         * In Java, there is no standard way to access these flags,
         * so this check has no practical effect(?) and commented out.
         */
        // if (huge + x > 0.0) {
        if (highWord < 0) {
          -0.0
        } else if ((highWord | lowWord) != 0) {
          1.0
        } else {
          x // +0.0
        }
        // }
      } else { // Case 1b: 0 <= exponent < 20
        val fractionalMask = 0x000fffff >>> exponent
        if (((highWord & fractionalMask) | lowWord) == 0) { // x is integral
          x
        } else {
          // if (huge + x > 0.0) { // Raise inexact flag
          val adjustedHighWord = if (highWord > 0) {
            // increment the integer part (and then clear the fractional part)
            // 0x00100000 is the 21st bit
            highWord + (0x00100000 >>> exponent)
          } else {
            highWord
          }
          // Clear fractional bits
          reconstructDouble(adjustedHighWord & ~fractionalMask, 0)
        }
      }
    } else if (exponent > 51) { // Case 2: Very large numbers or special values
      if (exponent == 0x400) { // Infinity or NaN
        x + x // returns qNaN for NaN, Infinity for Infinity
      } else {
        x
      }
    } else { // Case 3: 20 <= exponent <= 51: decimal point is in the low 32 bits
      val fractionalMask = (0xffffffff >>> (exponent - 20)).toInt // fractional bits in low word
      if ((lowWord & fractionalMask) == 0) { // x is integer
        x
      } else {
        // if (huge + x > 0.0) {
        val (adjustedHighWord, adjustedLowWord) =
          if (highWord > 0) {
            if (exponent == 20) {
              (highWord + 1, lowWord)
            } else {
              val newLowWord = lowWord + (1 << (52 - exponent)) // + LSB of integer part
              if (Integer.compareUnsigned(newLowWord, lowWord) < 0) { // carry
                (highWord + 1, newLowWord)
              } else {
                (highWord, newLowWord)
              }
            }
          } else {
            (highWord, lowWord)
          }
        // clear fractional bits
        reconstructDouble(adjustedHighWord, adjustedLowWord & ~fractionalMask)
      }
    }
  }
}
