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
import org.junit.Assume._

import java.lang.{Double => JDouble}
import java.lang.Math

import java.util.function.{DoubleBinaryOperator, DoubleUnaryOperator}

// Imported under different names for historical reasons
import org.scalajs.testsuite.utils.AssertExtensions.{assertExactEquals => assertSameDouble}
import org.scalajs.testsuite.utils.AssertExtensions.{assertExactEquals => assertSameFloat}

import org.scalajs.testsuite.utils.AssertThrows.assertThrows
import org.scalajs.testsuite.utils.Platform._

import scala.scalajs.LinkingInfo.{linkTimeIf, moduleKind}
import scala.scalajs.LinkingInfo.ModuleKind.WasmModule

class MathFunctionsTest {
  private def assertEqualsWithTolerance(expected: Double, actual: Double,
      toleranceULPs: Int): Unit = {
    if (actual.equals(expected)) {
      // OK fast path; also, the only valid path for non-finite values
    } else {
      val expectedBits = JDouble.doubleToRawLongBits(expected)
      val actualBits = JDouble.doubleToRawLongBits(actual)
      val tolerated = {
        JDouble.isFinite(expected) && // finite only
        ((expectedBits < 0) == (actualBits < 0)) && // same sign, exactly
        Math.abs(actualBits - expectedBits) <= toleranceULPs.toLong
      }
      if (!tolerated)
        fail(s"expected $expected but got $actual")
    }
  }

  private def testFun1WithTolerance(fun: DoubleUnaryOperator,
      vector: Array[Double], toleranceULPs: Int) {
    val len = vector.length
    assert(len % 2 == 0)
    var i = 0
    while (i != len) {
      val input = vector(i)
      val expected = vector(i + 1)
      assertEqualsWithTolerance(expected, fun.applyAsDouble(input), toleranceULPs)
      i += 2
    }
  }

  @Test
  def testCbrt(): Unit = {
    testFun1WithTolerance(Math.cbrt(_), MathFunctionsTestData.cbrt(), toleranceULPs = 1)
  }
}
