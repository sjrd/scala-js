/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

object HelloWorld {
  def main(args: Array[String]): Unit = {
    println((System.currentTimeMillis() / 1000L).toString())
    println((System.nanoTime() / 1000L).toString())

    val r = new java.util.Random()
    println(r.nextLong().toString())
    println(r.nextLong().toString())
  }
}
