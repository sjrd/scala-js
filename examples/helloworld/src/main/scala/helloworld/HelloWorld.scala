/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

object HelloWorld {
  def main(args: Array[String]): Unit = {
    val p = java.util.regex.Pattern.compile("a")
    println(p.matcher("a").matches())
  }
}
