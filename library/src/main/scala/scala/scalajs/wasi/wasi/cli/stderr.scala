package scala.scalajs.wasi.wasi.cli

package object stderr {

  // Type definitions
  type OutputStream = scala.scalajs.wasi.wasi.io.streams.OutputStream

  // Functions
  @scala.scalajs.wit.annotation.WitImport("wasi:cli/stderr@0.2.0", "get-stderr")
  def getStderr(): OutputStream = scala.scalajs.wit.native

}
