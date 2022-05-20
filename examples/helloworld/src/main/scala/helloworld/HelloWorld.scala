/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

@js.native
@JSImport("external.js", "Bar")
object Bar extends js.Object {
  def bar(x: Int, y: Int): Int = js.native
}

object Shared {
  @noinline
  def add(x: Int, y: Int): Int = x + y
}

/*@JSExportTopLevel("Foo", "main")
object Foo extends js.Object {
  def foo(x: Int): Int = Shared.add(x, 1)
}*/

object HelloWorld {
  def main(args: Array[String]): Unit = {
    val x = 5
    val y = 7
    println(Shared.add(x, y))

    println(Bar.bar(x, y))
  }
}
