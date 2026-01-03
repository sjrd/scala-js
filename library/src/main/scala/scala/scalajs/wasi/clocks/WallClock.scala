package scala.scalajs.wasi.clocks

import scala.scalajs.wit.annotation._
import scala.scalajs.wit.unsigned._
import scala.scalajs.wit

/** https://github.com/WebAssembly/WASI/blob/main/wasip2/clocks/wall-clock.wit */
object WallClock {

  @WitImport("wasi:clocks/wall-clock@0.2.0", "now")
  def now(): Datetime = wit.native

  @WitImport("wasi:clocks/wall-clock", "resolution")
  def resolution(): Datetime = wit.native

  @WitRecord
  final case class Datetime(seconds: ULong, nanoseconds: UInt)

}
