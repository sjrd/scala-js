package scala.scalajs.wasi.random

import scala.scalajs.wit.annotation._
import scala.scalajs.wit.unsigned._
import scala.scalajs.wit

/** https://github.com/WebAssembly/WASI/blob/main/wasip2/random/insecure.wit */
object Insecure {
  @WitImport("wasi:random/insecure", "get-insecure-random-u64")
  def getInsecureRandomU64(): ULong = wit.native
}
