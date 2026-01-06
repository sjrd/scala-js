package scala.scalajs.wasi.clocks

package object monotonic_clock {

  // Type definitions
  type Pollable = scala.scalajs.wasi.io.poll.Pollable

  type Instant = scala.scalajs.wit.unsigned.ULong

  type Duration = scala.scalajs.wit.unsigned.ULong

  // Functions
  @scala.scalajs.wit.annotation.WitImport("wasi:clocks/monotonic-clock@0.2.0", "now")
  def now(): scala.scalajs.wit.unsigned.ULong = scala.scalajs.wit.native

  @scala.scalajs.wit.annotation.WitImport("wasi:clocks/monotonic-clock@0.2.0", "resolution")
  def resolution(): scala.scalajs.wit.unsigned.ULong = scala.scalajs.wit.native

  @scala.scalajs.wit.annotation.WitImport("wasi:clocks/monotonic-clock@0.2.0", "subscribe-instant")
  def subscribeInstant(when: scala.scalajs.wit.unsigned.ULong): Pollable = scala.scalajs.wit.native

  @scala.scalajs.wit.annotation.WitImport("wasi:clocks/monotonic-clock@0.2.0", "subscribe-duration")
  def subscribeDuration(when: scala.scalajs.wit.unsigned.ULong): Pollable = scala.scalajs.wit.native

}
