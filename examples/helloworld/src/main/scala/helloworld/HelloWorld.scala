/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

object HelloWorld {
  def main(args: Array[String]): Unit = {
    val epsilon = Math.ulp(1.0)
    println(Math.fma(-(1.0 - epsilon), Double.MinValue, Double.MinValue))
  }
}
