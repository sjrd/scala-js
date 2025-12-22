/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

object HelloWorld {
  def main(args: Array[String]): Unit = {
    println(Character.isDigit('\u0661'))
    println(Character.isAlphabetic('\u04F8'))
    println(Character.isAlphabetic('\u05DB'))
  }
}
