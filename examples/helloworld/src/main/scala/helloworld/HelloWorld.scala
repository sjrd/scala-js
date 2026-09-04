/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

object HelloWorld {
  def main(args: Array[String]): Unit = {
    // Comment so that scalafmt keeps the braces

    try {
      println("Hello world!")

      printBool(doEq(hide("foo"), "foo"))
      printBool(doEq(hide("foo"), hide[Any]("foo")))
      printBool(doEq(hide("foo"), hide[Any]("bar")))
      printBool(doEq(hide("foo"), 5))
      printBool(doEq(hide("foo"), 5.5))
      printBool(doEq(hide("foo"), hide(5)))
      printBool(doEq(hide("foo"), hide(5.5)))

      println(clamp(hide(5.0), 0.0, -0.0))
    } catch {
      case t: Throwable =>
        println(t.toString())
    }
  }

  @noinline def hide[T](x: T): T = x

  @inline def doEq(x: Any, y: Any): Boolean =
    (x.asInstanceOf[AnyRef] eq y.asInstanceOf[AnyRef])

  @noinline def printBool(x: Boolean): Unit =
    println(x)

  @inline def clamp(value: scala.Double, min: scala.Double, max: scala.Double): scala.Double = {
    if (!(min < max)) // in particular, true if either bound is NaN
      validateClampSlowPath(min, max)

    Math.max(min, Math.min(max, value))
  }

  @inline def clamp(value: scala.Float, min: scala.Float, max: scala.Float): scala.Float = {
    if (!(min < max)) // in particular, true if either bound is NaN
      validateClampSlowPath(min.toDouble, max.toDouble)

    Math.max(min, Math.min(max, value))
  }

  private def validateClampSlowPath(min: scala.Double, max: scala.Double): Unit = {
    if (min != max || (min.equals(+0.0) && max.equals(-0.0))) {
      val msg = {
        if (java.lang.Double.isNaN(min)) "min is NaN"
        else if (java.lang.Double.isNaN(max)) "max is NaN"
        else s"$min > $max"
      }
      throw new IllegalArgumentException(msg)
    }
  }

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
      doWriteLine(0, codeUnits)
    } {
      System.out.println(x)
    }
  }

  @scala.scalajs.wasm.annotation.WasmImport("scalajs:core", "doWriteLine")
  def doWriteLine(isErr: scala.Int, line: Array[Short]): Unit =
    scala.scalajs.wasm.native
}
