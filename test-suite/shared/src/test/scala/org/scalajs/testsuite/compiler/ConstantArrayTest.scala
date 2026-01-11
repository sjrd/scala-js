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

package org.scalajs.testsuite.compiler

import org.junit.Test
import org.junit.Assert._
import org.junit.Assume._

import org.scalajs.testsuite.utils.AssertThrows.assertThrows
import org.scalajs.testsuite.utils.Platform._

/** Tests exercising the various encoding strategies for constant arrays.
 *
 *  See `CoreJSLib.defineConstantArrayMakers()` for details.
 */
class ConstantArrayTest {

  /** Tests that some elements of an array are what they should be.
   *
   *  We avoid `assertArrayEquals` on purpose, or anything else that could
   *  create a double-mistake situation that cancels out.
   */
  @noinline
  def testSomeElems[A](xs: Seq[A], expectedLength: Int)(elems: (Int, A)*): Unit = {
    assertEquals(expectedLength, xs.size)
    for ((i, elem) <- elems)
      assertEquals(s"at index $i", elem, xs(i))
  }

  @Test def byteArrayRaw(): Unit = {
    val xs: Array[Byte] = Array(
      -30.toByte, 34.toByte, -83.toByte, 74.toByte, 35.toByte, -30.toByte,
      -22.toByte, -117.toByte, 63.toByte, 16.toByte, 88.toByte, -32.toByte,
      72.toByte, -70.toByte, -127.toByte, -86.toByte, 83.toByte, -21.toByte,
      80.toByte, -71.toByte, -108.toByte, 6.toByte, 28.toByte, 46.toByte,
      42.toByte, 91.toByte, -69.toByte, 76.toByte, 3.toByte, 69.toByte,
      -12.toByte, -122.toByte, 69.toByte, 83.toByte, -91.toByte, 68.toByte,
      95.toByte, 25.toByte
    )

    testSomeElems(xs, 38)(
      0 -> -30,
      1 -> 34,
      6 -> -22,
      10 -> 88,
      37 -> 25
    )
  }
}
