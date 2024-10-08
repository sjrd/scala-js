/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

object HelloWorld {
  def main(args: Array[String]): Unit = {
    val c: java.util.Comparator[String] = (a, b) => a.length() - b.length()

    println(c.compare("foo", "babar"))

    val f: Int => Int = _ + 1
    println(f(5))
  }
}
