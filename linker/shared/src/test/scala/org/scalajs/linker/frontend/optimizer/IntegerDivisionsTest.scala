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

package org.scalajs.linker.frontend.optimizer

import org.junit.Test
import org.junit.Assert._

import IntegerDivisions._

class IntegerDivisionsTest {
  @Test def testComputeSignedMagicInt(): Unit = {
    def test(expectedM: Int, expectedAdd: Int, expectedShift: Int, divisor: Int): Unit = {
      assertEquals(
          divisor.toString(),
          MagicData(expectedM, expectedAdd, expectedShift),
          computeSignedMagic(Math.abs(divisor), divisor < 0))
    }

    // Test cases from Hacker's Delight, 10-14, Table 10-1, Signed column

    test(0x99999999, 0, 1, -5)
    test(0x55555555, -1, 1, -3)
    test(0x55555556, 0, 0, 3)
    test(0x66666667, 0, 1, 5)
    test(0x2AAAAAAB, 0, 0, 6)
    test(0x92492493, 1, 2, 7)
    test(0x38E38E39, 0, 1, 9)
    test(0x66666667, 0, 2, 10)
    test(0x2E8BA2E9, 0, 1, 11)
    test(0x2AAAAAAB, 0, 1, 12)
    test(0x51EB851F, 0, 3, 25)
    test(0x10624DD3, 0, 3, 125)
    test(0x68DB8BAD, 0, 8, 625)

    // For completeness, but we never use the results of this function for powers of 2
    for (k <- 1 to 30) {
      test(0x7fffffff, -1, k - 1, -(1 << k))
      test(0x80000001, 1, k - 1, 1 << k)
    }
  }

  @Test def testComputeUnsignedMagicInt(): Unit = {
    def test(expectedM: Int, expectedAdd: Int, expectedShift: Int, divisor: Int): Unit = {
      assertEquals(
          divisor.toString(),
          MagicData(expectedM, expectedAdd, expectedShift),
          computeUnsignedMagic(divisor, negativeDivisor = false))
    }

    // Test cases from Hacker's Delight, 10-14, Table 10-1, Unsigned column

    test(0xAAAAAAAB, 0, 1, 3)
    test(0xCCCCCCCD, 0, 2, 5)
    test(0xAAAAAAAB, 0, 2, 6)
    test(0x24924925, 1, 3, 7)
    test(0x38E38E39, 0, 1, 9)
    test(0xCCCCCCCD, 0, 3, 10)
    test(0xBA2E8BA3, 0, 3, 11)
    test(0xAAAAAAAB, 0, 3, 12)
    test(0x51EB851F, 0, 3, 25)
    test(0x10624DD3, 0, 3, 125)
    test(0xD1B71759, 0, 9, 625)

    // For completeness, but we never use the results of this function for powers of 2
    for (k <- 1 to 31)
      test(1 << (32 - k), 0, 0, 1 << k)
  }

  @Test def testComputeSignedMagicLong(): Unit = {
    def test(expectedM: Long, expectedAdd: Int, expectedShift: Int, divisor: Long): Unit = {
      assertEquals(
          divisor.toString(),
          MagicData(expectedM, expectedAdd, expectedShift),
          computeSignedMagic(Math.abs(divisor), divisor < 0L))
    }

    // Test cases from Hacker's Delight, 10-14, Table 10-2, Signed column

    test(0x9999999999999999L, 0, 1, -5L)
    test(0x5555555555555555L, -1, 1, -3L)
    test(0x5555555555555556L, 0, 0, 3L)
    test(0x6666666666666667L, 0, 1, 5L)
    test(0x2AAAAAAAAAAAAAABL, 0, 0, 6L)
    test(0x4924924924924925L, 0, 1, 7L)
    test(0x1C71C71C71C71C72L, 0, 0, 9L)
    test(0x6666666666666667L, 0, 2, 10L)
    test(0x2E8BA2E8BA2E8BA3L, 0, 1, 11L)
    test(0x2AAAAAAAAAAAAAABL, 0, 1, 12L)
    test(0xA3D70A3D70A3D70BL, 1, 4, 25L)
    test(0x20C49BA5E353F7CFL, 0, 4, 125L)
    test(0x346DC5D63886594BL, 0, 7, 625L)

    // For completeness, but we never use the results of this function for powers of 2
    for (k <- 1 to 62) {
      test(0x7fffffffffffffffL, -1, k - 1, -(1L << k))
      test(0x8000000000000001L, 1, k - 1, 1L << k)
    }
  }

  @Test def testComputeUnsignedMagicLong(): Unit = {
    def test(expectedM: Long, expectedAdd: Int, expectedShift: Int, divisor: Long): Unit = {
      assertEquals(
          divisor.toString(),
          MagicData(expectedM, expectedAdd, expectedShift),
          computeUnsignedMagic(divisor, negativeDivisor = false))
    }

    // Test cases from Hacker's Delight, 10-14, Table 10-2, Unsigned column

    test(0xAAAAAAAAAAAAAAABL, 0, 1, 3L)
    test(0xCCCCCCCCCCCCCCCDL, 0, 2, 5L)
    test(0xAAAAAAAAAAAAAAABL, 0, 2, 6L)
    test(0x2492492492492493L, 1, 3, 7L)
    test(0xE38E38E38E38E38FL, 0, 3, 9L)
    test(0xCCCCCCCCCCCCCCCDL, 0, 3, 10L)
    test(0x2E8BA2E8BA2E8BA3L, 0, 1, 11L)
    test(0xAAAAAAAAAAAAAAABL, 0, 3, 12L)
    test(0x47AE147AE147AE15L, 1, 5, 25L)
    test(0x0624DD2F1A9FBE77L, 1, 7, 125L)
    test(0x346DC5D63886594BL, 0, 7, 625L)

    // For completeness, but we never use the results of this function for powers of 2
    for (k <- 1 to 63)
      test(1L << (64 - k), 0, 0, 1L << k)
  }
}
