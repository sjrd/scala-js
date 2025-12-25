package scala.scalajs.wasi.clocks

import scala.scalajs.component.annotation._
import scala.scalajs.component.unsigned._
import scala.scalajs.{component => cm}

/** https://github.com/WebAssembly/WASI/blob/main/wasip2/clocks/wall-clock.wit */
object WallClock {

  @ComponentImport("wasi:clocks/wall-clock@0.2.0", "now")
  def now(): Datetime = cm.native

  @ComponentImport("wasi:clocks/wall-clock", "resolution")
  def resolution(): Datetime = cm.native

  @ComponentRecord
  final case class Datetime(seconds: ULong, nanoseconds: UInt)

}
