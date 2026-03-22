/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._
import java.util.Date

object HelloWorld {
  def main(args: Array[String]): Unit = {
    val d = new Date()
    println(d)
  }
}
