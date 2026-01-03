package scala.scalajs.wasi.cli

import scala.scalajs.wit.annotation._
import scala.scalajs.wit

import scala.scalajs.wasi.io.Streams.OutputStream

/** https://github.com/WebAssembly/WASI/blob/main/wasip2/cli/stdio.wit */
object Stdout {
  @WitImport("wasi:cli/stdout@0.2.0", "get-stdout")
  def getStdout(): OutputStream = {
    wit.native
  }
}

case class Foo(x: Int) {
  def foo: Option[Int] = None
}
