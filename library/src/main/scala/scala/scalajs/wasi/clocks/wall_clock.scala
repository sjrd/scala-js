package scala.scalajs.wasi.clocks

package object wall_clock {

  // Type definitions
  @scala.scalajs.wit.annotation.WitRecord
  final case class Datetime(seconds: scala.scalajs.wit.unsigned.ULong, nanoseconds: scala.scalajs.wit.unsigned.UInt)

  // Functions
  @scala.scalajs.wit.annotation.WitImport("wasi:clocks/wall-clock@0.2.0", "now")
  def now(): Datetime = scala.scalajs.wit.native

  @scala.scalajs.wit.annotation.WitImport("wasi:clocks/wall-clock@0.2.0", "resolution")
  def resolution(): Datetime = scala.scalajs.wit.native

}
