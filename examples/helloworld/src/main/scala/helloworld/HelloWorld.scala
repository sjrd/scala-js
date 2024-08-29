/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

object HelloWorld {
  def main(args: Array[String]): Unit = {
    val obj = js.Dynamic.literal(a = 1, b = 2)
    js.Dynamic.global.console.log(obj)
  }
}
