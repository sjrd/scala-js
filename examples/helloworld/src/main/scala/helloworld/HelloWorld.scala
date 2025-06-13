/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

object HelloWorld {
  def main(args: Array[String]): Unit = {
    test(-63003L, -2L, 0L, -63003L)
  }

  @noinline
  def test(num: Long, div: Long, expectedQuot: Long, expectedRem: Long): Unit = {
    println(java.lang.Long.toUnsignedString(num))
    println(java.lang.Long.toUnsignedString(div))
    val approxNum = approx(num)
    val approxDiv = approx(div)
    val approxQuot = approxNum / approxDiv
    println(approxNum)
    println(approxDiv)
    println(approxQuot)
    val quot = java.lang.Long.divideUnsigned(num, div)
    val rem = java.lang.Long.remainderUnsigned(num, div)
    println(java.lang.Long.toUnsignedString(quot))
    println(java.lang.Long.toUnsignedString(rem))
    assert(expectedQuot == quot && expectedRem == rem)
  }

  def approx(x: Long): Double =
    (x >>> 32).toDouble * (1L << 32).toDouble + (x & 0xffffffffL).toDouble
}
