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

package org.scalajs.testsuite.javalib.lang

import org.junit.Test
import org.junit.Assert._

import java.lang.{Double => JDouble}
import java.lang.StrictMath

import org.scalajs.testsuite.utils.AssertExtensions.assertExactEquals

class StrictMathTest {

  @Test def floor(): Unit = {
    // basic
    assertExactEquals(5.0, StrictMath.floor(5.0))
    assertExactEquals(5.0, StrictMath.floor(5.7))
    assertExactEquals(-6.0, StrictMath.floor(-5.7))
    assertExactEquals(0.0, StrictMath.floor(0.0))
    assertExactEquals(-0.0, StrictMath.floor(-0.0))
    assertExactEquals(0.0, StrictMath.floor(0.5))
    assertExactEquals(-1.0, StrictMath.floor(-0.5))

    // Special values
    assertExactEquals(Double.PositiveInfinity, StrictMath.floor(Double.PositiveInfinity))
    assertExactEquals(Double.NegativeInfinity, StrictMath.floor(Double.NegativeInfinity))
    assertExactEquals(Double.NaN, StrictMath.floor(Double.NaN))

    // Exponent = 19, 2^19 = 524288
    assertExactEquals(524288.0, StrictMath.floor(JDouble.longBitsToDouble(0x4120000000000000L))) // Exactly 2^19 * 1.0
    assertExactEquals(524288.0, StrictMath.floor(JDouble.longBitsToDouble(0x4120000000000001L))) // 2^19 + tiny fraction
    assertExactEquals(-524289.0, StrictMath.floor(JDouble.longBitsToDouble(0xc120000000000001L))) // -(2^19 + tiny fraction)

    // Exponent = 20, 2^20 = 1048576
    assertExactEquals(1048576.0, StrictMath.floor(JDouble.longBitsToDouble(0x4130000000000000L))) // Exactly 2^20 * 1.0
    assertExactEquals(1048576.0, StrictMath.floor(JDouble.longBitsToDouble(0x4130000000000001L))) // 2^20 + tiny fraction
    assertExactEquals(-1048577.0, StrictMath.floor(JDouble.longBitsToDouble(0xc130000000000001L))) // -(2^20 + tiny fraction)

    // Exponent = 51, 2^51 = 2251799813685248
    assertExactEquals(2251799813685248.0, StrictMath.floor(JDouble.longBitsToDouble(0x4320000000000000L))) // Exactly 2^51
    assertExactEquals(2251799813685248.0, StrictMath.floor(JDouble.longBitsToDouble(0x4320000000000001L))) // 2^51 + 1
    assertExactEquals(-2251799813685249.0, StrictMath.floor(JDouble.longBitsToDouble(0xc320000000000001L))) // -(2^51 + tiny fraction)

    // Exponent = 52 (all integers are exact beyond this point), 2^52 = 4503599627370496
    assertExactEquals(4503599627370496.0, StrictMath.floor(JDouble.longBitsToDouble(0x4330000000000000L))) // Exactly 2^52
    assertExactEquals(4503599627370497.0, StrictMath.floor(JDouble.longBitsToDouble(0x4330000000000001L))) // 2^52 + 1
    assertExactEquals(-4503599627370496.0, StrictMath.floor(JDouble.longBitsToDouble(0xc330000000000000L))) // -2^52
    assertExactEquals(-4503599627370497.0, StrictMath.floor(JDouble.longBitsToDouble(0xc330000000000001L))) // -(2^52 + 1)
  }

  @Test def ceil(): Unit = {
    // basic
    assertExactEquals(5.0, StrictMath.ceil(5.0))
    assertExactEquals(6.0, StrictMath.ceil(5.7))
    assertExactEquals(-5.0, StrictMath.ceil(-5.7))
    assertExactEquals(0.0, StrictMath.ceil(0.0))
    assertExactEquals(-0.0, StrictMath.ceil(-0.0))
    assertExactEquals(1.0, StrictMath.ceil(0.5))
    assertExactEquals(-0.0, StrictMath.ceil(-0.5))

    // Special values
    assertExactEquals(Double.PositiveInfinity, StrictMath.ceil(Double.PositiveInfinity))
    assertExactEquals(Double.NegativeInfinity, StrictMath.ceil(Double.NegativeInfinity))
    assertExactEquals(Double.NaN, StrictMath.ceil(Double.NaN))

    // Exponent = 19, 2^19 = 524288
    assertExactEquals(524288.0, StrictMath.ceil(JDouble.longBitsToDouble(0x4120000000000000L))) // Exactly 2^19 * 1.0
    assertExactEquals(524289.0, StrictMath.ceil(JDouble.longBitsToDouble(0x4120000000000001L))) // 2^19 + tiny fraction
    assertExactEquals(-524288.0, StrictMath.ceil(JDouble.longBitsToDouble(0xc120000000000001L))) // -(2^19 + tiny fraction)

    // Exponent = 20, 2^20 = 1048576
    assertExactEquals(1048576.0, StrictMath.ceil(JDouble.longBitsToDouble(0x4130000000000000L))) // Exactly 2^20 * 1.0
    assertExactEquals(1048577.0, StrictMath.ceil(JDouble.longBitsToDouble(0x4130000000000001L))) // 2^20 + tiny fraction
    assertExactEquals(-1048576.0, StrictMath.ceil(JDouble.longBitsToDouble(0xc130000000000001L))) // -(2^20 + tiny fraction)

    // Exponent = 51, 2^51 = 2251799813685248
    assertExactEquals(2251799813685248.0, StrictMath.ceil(JDouble.longBitsToDouble(0x4320000000000000L))) // Exactly 2^51
    assertExactEquals(2251799813685249.0, StrictMath.ceil(JDouble.longBitsToDouble(0x4320000000000001L))) // 2^51 + 1
    assertExactEquals(-2251799813685248.0, StrictMath.ceil(JDouble.longBitsToDouble(0xc320000000000001L))) // -(2^51 + tiny fraction)

    // Exponent = 52 (all integers are exact beyond this point), 2^52 = 4503599627370496
    assertExactEquals(4503599627370496.0, StrictMath.ceil(JDouble.longBitsToDouble(0x4330000000000000L))) // Exactly 2^52
    assertExactEquals(4503599627370497.0, StrictMath.ceil(JDouble.longBitsToDouble(0x4330000000000001L))) // 2^52 + 1
    assertExactEquals(-4503599627370496.0, StrictMath.ceil(JDouble.longBitsToDouble(0xc330000000000000L))) // -2^52
    assertExactEquals(-4503599627370497.0, StrictMath.ceil(JDouble.longBitsToDouble(0xc330000000000001L))) // -(2^52 + 1)
  }
}
