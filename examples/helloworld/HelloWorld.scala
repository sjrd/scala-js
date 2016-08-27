/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

@js.native
@JSImport("fs")
object fs extends js.Object {
  def readFileSync(fileName: String, encoding: String): String = js.native
}

object HelloWorld extends js.JSApp {
  def main() {
    println(js.Math.sin(1))
    println(fs.readFileSync("README.md", "utf-8"))
  }
}
