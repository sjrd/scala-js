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

import org.scalajs.ir.Names._
import org.scalajs.ir.Trees._
import org.scalajs.ir.Types._

import org.scalajs.linker.testutils.TestIRBuilder._

import IntegerDivisions._

class IntegerDivisionsTest {
  // We use many binary ops in these tests
  import BinaryOp._

  @Test def testComputeSignedMagicInt(): Unit = {
    def test(expectedM: Int, expectedAdd: Int, expectedShift: Int, divisor: Int): Unit = {
      assertEquals(
          divisor.toString(),
          MagicData(expectedM, expectedAdd, expectedShift),
          computeSignedMagic(Math.abs(divisor), divisor < 0))
    }

    // From section 10-5

    test(0x6DB6DB6D, -1, 2, -7)

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

  private val inum = VarRef(NumeratorArgName)(IntType)
  private val lnum = VarRef(NumeratorArgName)(LongType)

  private def intAddOneIfNumIsNegative(tree: Tree): Tree =
    BinaryOp(Int_+, tree, BinaryOp(BinaryOp.Int_>>>, inum, IntLiteral(31)))

  @Test def testIntSignedPosNonPow2(): Unit = {
    def test(divisor: Int)(expectedTree: Tree): Unit = {
      val actual = new IntegerDivisions(useRuntimeLong = false)
        .makeOptimizedDivision(BinaryOp.Int_/, divisor)
      if (actual != expectedTree)
        fail(s"Expected:\n${expectedTree.show}\nbut got\n${actual.show}")
    }

    test(3) {
      intAddOneIfNumIsNegative {
        BinaryOp(
          Int_>>,
          IntIsUnsignedIntegral.genMulSignedHi(0x55555556, inum, useRuntimeLong = false),
          IntLiteral(0)
        )
      }
    }

    test(5) {
      intAddOneIfNumIsNegative {
        BinaryOp(
          Int_>>,
          IntIsUnsignedIntegral.genMulSignedHi(0x66666667, inum, useRuntimeLong = false),
          IntLiteral(1)
        )
      }
    }

    // TODO Optimize according to section 10-5
    test(-5) {
      BinaryOp(
        Int_-,
        IntLiteral(0),
        intAddOneIfNumIsNegative {
          BinaryOp(
            Int_>>,
            IntIsUnsignedIntegral.genMulSignedHi(0x66666667, inum, useRuntimeLong = false),
            IntLiteral(1)
          )
        }
      )
    }

    test(7) {
      intAddOneIfNumIsNegative {
        BinaryOp(
          Int_>>,
          BinaryOp(
            Int_+,
            IntIsUnsignedIntegral.genMulSignedHi(0x92492493, inum, useRuntimeLong = false),
            inum
          ),
          IntLiteral(2)
        )
      }
    }

    // TODO Optimize according to section 10-5
    test(-7) {
      BinaryOp(
        Int_-,
        IntLiteral(0),
        intAddOneIfNumIsNegative {
          BinaryOp(
            Int_>>,
            BinaryOp(
              Int_+,
              IntIsUnsignedIntegral.genMulSignedHi(0x92492493, inum, useRuntimeLong = false),
              inum
            ),
            IntLiteral(2)
          )
        }
      )
    }
  }

  @Test @noinline def fuzzTestInt_/(): Unit = fuzzTestIntOp(BinaryOp.Int_/)
  @Test @noinline def fuzzTestInt_%(): Unit = fuzzTestIntOp(BinaryOp.Int_%)
  @Test @noinline def fuzzTestInt_unsigned_/(): Unit = fuzzTestIntOp(BinaryOp.Int_unsigned_/)
  @Test @noinline def fuzzTestInt_unsigned_%(): Unit = fuzzTestIntOp(BinaryOp.Int_unsigned_%)

  @Test @noinline def fuzzTestLong_/(): Unit = fuzzTestLongOp(BinaryOp.Long_/)
  @Test @noinline def fuzzTestLong_%(): Unit = fuzzTestLongOp(BinaryOp.Long_%)
  @Test @noinline def fuzzTestLong_unsigned_/(): Unit = fuzzTestLongOp(BinaryOp.Long_unsigned_/)
  @Test @noinline def fuzzTestLong_unsigned_%(): Unit = fuzzTestLongOp(BinaryOp.Long_unsigned_%)

  private val FuzzTestDivisorsSeed = 4075846924374047794L
  private val FuzzTestNumeratorsSeed = 8312049509154985420L
  private val FuzzTestDivisorsRounds = 10000
  private val FuzzTestNumeratorsRounds = 100
  // There will be FuzzTestDivisorsRound * FuzzTestNumeratorsRound inner iterations!

  private def fuzzTestIntOp(op: BinaryOp.Code): Unit = {
    def testDivisor(divisor: Int): Unit =
      fuzzTestIntDivisor(op, divisor)

    testDivisor(0)
    for (small <- 1 to 13) {
      testDivisor(small)
      testDivisor(-small)
    }
    testDivisor(100)
    testDivisor(1000)
    testDivisor(1000000000) // we use this value in RuntimeLong.toString()
    testDivisor(Int.MaxValue - 1)
    testDivisor(Int.MaxValue)
    testDivisor(Int.MinValue)
    testDivisor(Int.MinValue + 1)

    val rnd = new java.util.SplittableRandom(FuzzTestDivisorsSeed)
    for (_ <- 0 until FuzzTestDivisorsRounds)
      testDivisor(rnd.nextInt() >> rnd.nextInt(32))
  }

  private def fuzzTestLongOp(op: BinaryOp.Code): Unit = {
    def testDivisor(divisor: Long): Unit =
      fuzzTestLongDivisor(op, divisor)

    testDivisor(0L)
    for (small <- 1 to 13) {
      testDivisor(small.toLong)
      testDivisor(-small.toLong)
    }
    testDivisor(100L)
    testDivisor(1000L)
    testDivisor(1000000000L) // we use this value in RuntimeLong.toString()
    testDivisor(Long.MaxValue - 1L)
    testDivisor(Long.MaxValue)
    testDivisor(Long.MinValue)
    testDivisor(Long.MinValue + 1L)

    val rnd = new java.util.SplittableRandom(FuzzTestDivisorsSeed)
    for (_ <- 0 until FuzzTestDivisorsRounds)
      testDivisor(rnd.nextLong() >> rnd.nextInt(64))
  }

  private def fuzzTestIntDivisor(op: BinaryOp.Code, divisor: Int): Unit = {
    val reference = BinaryOp(op, inum, IntLiteral(divisor))
    val optimized = new IntegerDivisions(useRuntimeLong = false)
      .makeOptimizedDivision(op, divisor)

    if (divisor == 0) {
      val UnaryOp(UnaryOp.Throw, _) = optimized
      return ()
    }

    def test(numValue: Int): Unit =
      assertEvalEquals(reference, optimized, IntLiteral(numValue))

    test(0)
    for (small <- 1 to 13) {
      test(small)
      test(-small)
    }
    test(divisor - 1)
    test(divisor)
    test(divisor + 1)
    test(-divisor + 1)
    test(-divisor)
    test(-divisor - 1)
    test(Int.MaxValue - 1)
    test(Int.MaxValue)
    test(Int.MinValue)
    test(Int.MinValue + 1)

    val rnd = new java.util.SplittableRandom(FuzzTestNumeratorsSeed)
    for (_ <- 0 until FuzzTestNumeratorsRounds)
      test(rnd.nextInt() >> rnd.nextInt(32))
  }

  private def fuzzTestLongDivisor(op: BinaryOp.Code, divisor: Long): Unit = {
    val reference = BinaryOp(op, lnum, LongLiteral(divisor))
    val optimized = new IntegerDivisions(useRuntimeLong = false)
      .makeOptimizedDivision(op, divisor)

    if (divisor == 0L) {
      val UnaryOp(UnaryOp.Throw, _) = optimized
      return ()
    }

    def test(numValue: Long): Unit =
      assertEvalEquals(reference, optimized, LongLiteral(numValue))

    test(0L)
    for (small <- 1 to 13) {
      test(small.toLong)
      test(-small.toLong)
    }
    test(divisor - 1L)
    test(divisor)
    test(divisor + 1L)
    test(-divisor + 1L)
    test(-divisor)
    test(-divisor - 1L)
    test(Long.MaxValue - 1L)
    test(Long.MaxValue)
    test(Long.MinValue)
    test(Long.MinValue + 1L)

    val rnd = new java.util.SplittableRandom(FuzzTestNumeratorsSeed)
    for (_ <- 0 until FuzzTestNumeratorsRounds)
      test(rnd.nextLong() >> rnd.nextInt(64))
  }

  private def assertEvalEquals(reference: Tree, optimized: Tree, numValue: Literal): Unit = {
    val referenceResult = ElementaryInterpreter.eval(reference, NumeratorArgName -> numValue)
    val optimizedResult = ElementaryInterpreter.eval(optimized, NumeratorArgName -> numValue)

    if (optimizedResult != referenceResult) {
      fail(
          s"Fuzz test failed\n" +
          s"Expected ${referenceResult.show} but got ${optimizedResult.show}\n" +
          s"Numerator: ${numValue.show}\n" +
          s"Reference code was:\n${reference.show}\n" +
          s"Optimized code was:\n${optimized.show}\n")
    }
  }
}
