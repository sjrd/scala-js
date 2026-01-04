/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

object HelloWorld {
  def main(args: Array[String]): Unit = {
    val a = Array(5, 24, 67, 0x03b3, 8987, 9320)
    for (x <- a)
      println(s"$x -> ${Character.isLetter(x)}")

    val b = Array(78, 20, -77, 84, -3,
        -33, 58, -9, 11, 57, -118, 40, -74, -86, 78, 123, 58)
    for (x <- b)
      println(x)
  }
}
