/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

//@JSImport("./foo.js", "Foo")
@JSGlobal
@js.native
class Foo extends js.Object

@JSExportTopLevel("Bar")
class Bar extends js.Object

object HelloWorld {
  def main(args: Array[String]): Unit = {
    println(new Bar())
    println(new Foo())
  }
}
