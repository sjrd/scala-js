package scala.scalajs.wasi.random

package object random {

  // Functions
  @scala.scalajs.wit.annotation.WitImport("wasi:random/random@0.2.0", "get-random-bytes")
  def getRandomBytes(len: scala.scalajs.wit.unsigned.ULong): Array[scala.scalajs.wit.unsigned.UByte] = scala.scalajs.wit.native

  @scala.scalajs.wit.annotation.WitImport("wasi:random/random@0.2.0", "get-random-u64")
  def getRandomU64(): scala.scalajs.wit.unsigned.ULong = scala.scalajs.wit.native

}
