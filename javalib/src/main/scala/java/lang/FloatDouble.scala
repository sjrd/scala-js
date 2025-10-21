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

package java.lang

import java.util.function.Function

/** Common algorithms between `Float` and `Double`. */
object FloatDouble {

  @inline
  def toHexString[I, F](x: F, mantissaToHexString: Function[I, String])(
      implicit ops: IntFloatBits[I, F]): String = {
    import ops._

    val bits = floatToBits(x)
    val m = mantissaBitsOf(bits)
    val e = exponentOf(bits) // biased

    val posResult = if (e > 0) {
      if (e == emask) {
        // Special
        if (m !== zero) "NaN"
        else "Infinity"
      } else {
        // Normalized
        "0x1." + mantissaToHexString(m) + "p" + (e - bias)
      }
    } else {
      if (m !== zero) {
        // Subnormal
        "0x0." + mantissaToHexString(m) + ("p" + (1 - bias))
      } else {
        // Zero
        "0x0.0p0"
      }
    }

    if (bits < zero) "-" + posResult else posResult
  }

}
