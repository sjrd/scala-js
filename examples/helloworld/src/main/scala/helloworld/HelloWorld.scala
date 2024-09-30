/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

object HelloWorld {
  def main(args: Array[String]): Unit = {
    val list = 1 :: 3 :: 6 :: Nil
    val xs = list.map(i => i * 2)
    xs.foreach(println(_))
  }
}
