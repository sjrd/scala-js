package scala.scalajs.wasi.wasi.cli

package object stdin {

  // Type definitions
  type InputStream = scala.scalajs.wasi.wasi.io.streams.InputStream

  // Functions
  @scala.scalajs.wit.annotation.WitImport("wasi:cli/stdin@0.2.0", "get-stdin")
  def getStdin(): InputStream = scala.scalajs.wit.native

}
