package scala.scalajs.wasi.io

import scala.scalajs.wit.annotation._
import scala.scalajs.wit

/** https://github.com/WebAssembly/WASI/blob/main/wasip2/io/error.wit */
object Error {
  @WitResourceImport("wasi:io/error", "error")
  trait Error {
    @WitResourceMethod("to-debug-string")
    def toDebugString(): String = wit.native
  }
}
