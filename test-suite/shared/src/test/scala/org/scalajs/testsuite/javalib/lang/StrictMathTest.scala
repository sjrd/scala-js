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

/* Port test data from FreeBSD:
 * - https://github.com/freebsd/freebsd-src/blob/9b0102837e305ca75de2bc14d284f786a33f9a6a/lib/msun/tests/logarithm_test.c
 *
 * Copyright (c) 2008-2010 David Schultz <das@FreeBSD.org>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package org.scalajs.testsuite.javalib.lang

import org.junit.Test
import org.junit.Assert._

import java.lang.{Double => JDouble}
import java.lang.{Math, StrictMath}

import org.scalajs.testsuite.utils.AssertExtensions.assertExactEquals

class StrictMathTest {

  @Test def log(): Unit = {
    assertExactEquals(Double.NegativeInfinity, StrictMath.log(-0.0))
    assertExactEquals(Double.NegativeInfinity, StrictMath.log(0.0))
    assertTrue(StrictMath.log(Double.NaN).isNaN)
    assertExactEquals(Double.PositiveInfinity, StrictMath.log(Double.PositiveInfinity))
    assertTrue(StrictMath.log(Double.NegativeInfinity).isNaN)
    assertExactEquals(0.0, StrictMath.log(1.0))
    assertTrue(StrictMath.log(-1.0).isNaN)

    val tests = Array(
      (Math.scalb(1.0, -120) + Math.scalb(1.0, -140), -8.31776607135195754708796206665656732e1),
      (1.0 - Math.scalb(1.0, -20), -9.53674771153890007250243736279163253e-7),
      (1.0 + Math.scalb(1.0, -20), 9.53673861659188233908415514963336144e-7),
      (19.75, 2.98315349134713087533848129856505779e0),
      (19.75 * Math.scalb(1.0, 100), 72.29787154734166181706169344438271459357255439172762452)
    )
    for ((x, expected) <- tests) {
      val delta = Math.ulp(expected)
      assertEquals(s"log($x)", expected, StrictMath.log(x), Math.ulp(delta))
    }
  }

  @Test def log10(): Unit = {
    assertExactEquals(Double.NegativeInfinity, StrictMath.log10(-0.0))
    assertExactEquals(Double.NegativeInfinity, StrictMath.log10(0.0))
    assertTrue(StrictMath.log10(Double.NaN).isNaN)
    assertExactEquals(Double.PositiveInfinity, StrictMath.log10(Double.PositiveInfinity))
    assertTrue(StrictMath.log10(Double.NegativeInfinity).isNaN)
    assertExactEquals(0.0, StrictMath.log10(1.0))
    assertTrue(StrictMath.log10(-1.0).isNaN)

    val tests = Array(
      (Math.scalb(1.0, -120) + Math.scalb(1.0, -140), -3.61235990655024477716980559136055915e1),
      (1.0 - Math.scalb(1.0, -20), -4.14175690642480911859354110516159131e-7),
      (1.0 + Math.scalb(1.0, -20), 4.14175295653950611453333571759200697e-7),
      (19.75, 1.29556709996247903756734359702926363e0),
      (19.75 * Math.scalb(1.0, 100), 3.139856666636059855894123306947856631e1)
    )
    for ((x, expected) <- tests) {
      assertEquals(s"log10($x)", expected, StrictMath.log10(x), Math.ulp(expected))
    }
  }

  @Test def log1p(): Unit = {
    assertExactEquals(-0.0, StrictMath.log1p(-0.0))
    assertExactEquals(0.0, StrictMath.log1p(0.0))
    assertTrue(StrictMath.log1p(Double.NaN).isNaN)
    assertExactEquals(Double.PositiveInfinity, StrictMath.log1p(Double.PositiveInfinity))
    assertTrue(StrictMath.log1p(Double.NegativeInfinity).isNaN)
    assertExactEquals(Double.NegativeInfinity, StrictMath.log1p(-1.0))
    assertTrue(StrictMath.log1p(-1.5).isNaN)

    val tests = Array(
      (1.0 - Math.scalb(1.0, -20) - 1.0, -9.53674771153890007250243736279163253e-7),
      (Math.scalb(1.0, -20), 9.53673861659188233908415514963336144e-7),
      (18.75, 2.98315349134713087533848129856505779e0)
    )
    for ((x, expected) <- tests) {
      assertEquals(s"log1p($x)", expected, StrictMath.log1p(x), Math.ulp(expected))
    }

    val expected1 = 1.82321556793954589204283870982629267635e-1
    assertEquals(expected1, StrictMath.log1p(JDouble.parseDouble("0x0.3333333333333p0")), Math.ulp(expected1))

    val expected2 = -2.23143551314209700255143859052009022937e-1
    assertEquals(expected2, StrictMath.log1p(JDouble.parseDouble("-0x0.3333333333333p0")), Math.ulp(expected2))
  }
}
