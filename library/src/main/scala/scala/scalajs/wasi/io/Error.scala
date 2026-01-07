package scala.scalajs.wasi.io

package object error {

  // Resources
  @scala.scalajs.wit.annotation.WitResourceImport("wasi:io/error@0.2.0", "error")
  trait Error {
    @scala.scalajs.wit.annotation.WitResourceMethod("to-debug-string")
    def toDebugString(): String = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceDrop
    def close(): Unit = scala.scalajs.wit.native
  }
  object Error {
  }

}
