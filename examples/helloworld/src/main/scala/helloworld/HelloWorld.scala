/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import java.lang.{Float => JFloat}

object HelloWorld {
  def main(args: Array[String]): Unit = {
    // Comment so that scalafmt keeps the braces

    println("Hello world!")
    val x = hide(54.5f)
    val bits = JFloat.floatToIntBits(x)
    println(bits)
    println(JFloat.intBitsToFloat(bits))
  }

  @noinline def hide[A](x: A): A = x

  @noinline
  def println(x: Any): Unit = {
    import scala.scalajs.LinkingInfo

    LinkingInfo.linkTimeIf(LinkingInfo.moduleKind == LinkingInfo.ModuleKind.WasmModule) {
      val s = String.valueOf(x)
      val len = s.length()
      val codeUnits = new Array[Short](len)
      var i = 0
      while (i != len) {
        codeUnits(i) = s.charAt(i).toShort
        i += 1
      }
      wasmPrintln(codeUnits)
    } {
      System.out.println(x)
    }
  }

  @scala.scalajs.wasm.annotation.WasmImport("scalajs:non-standard", "println")
  def wasmPrintln(line: Array[Short]): Unit =
    scala.scalajs.wasm.native
}
