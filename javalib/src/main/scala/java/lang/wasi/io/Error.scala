package java.lang.wasi.io

import scala.scalajs.component.annotation._
import scala.scalajs.{component => cm}

/** https://github.com/WebAssembly/WASI/blob/main/wasip2/io/error.wit */
object Error {
  @ComponentResourceImport("wasi:io/error", "error")
  trait Error {
    @ComponentResourceMethod("to-debug-string")
    def toDebugString(): String = cm.native
  }
}
