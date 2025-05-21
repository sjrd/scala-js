package java.lang.wasi.cli

import scala.scalajs.component.annotation._

import wasi.io.Streams.OutputStream
import scala.scalajs.{component => cm}

/** https://github.com/WebAssembly/WASI/blob/main/wasip2/cli/stdio.wit */
object Stdout {
  @ComponentImport("wasi:cli/stdout@0.2.0", "get-stdout")
  def getStdout(): OutputStream = cm.native
}
