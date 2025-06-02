package scala.scalajs.wasi.random

import scala.scalajs.component.annotation._
import scala.scalajs.component.unsigned._
import scala.scalajs.{component => cm}

/** https://github.com/WebAssembly/WASI/blob/main/wasip2/random/insecure.wit */
object Insecure {
  @ComponentImport("wasi:random/insecure", "get-insecure-random-u64")
  def getInsecureRandomU64(): ULong = cm.native
}
