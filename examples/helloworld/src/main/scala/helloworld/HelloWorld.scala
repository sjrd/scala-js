/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

object HelloWorld {
  def main(args: Array[String]): Unit = {
    val r = new java.util.SplittableRandom()

    println(r.nextLong() * 2)
    println(r.nextLong())
  }

  @noinline def println(x: Long): Unit =
    Predef.println(x)
}
