package scala.scalajs.wasi.random

package object insecure {

  // Functions
  @scala.scalajs.wit.annotation.WitImport("wasi:random/insecure@0.2.0", "get-insecure-random-bytes")
  def getInsecureRandomBytes(len: scala.scalajs.wit.unsigned.ULong): Array[scala.scalajs.wit.unsigned.UByte] = scala.scalajs.wit.native

  @scala.scalajs.wit.annotation.WitImport("wasi:random/insecure@0.2.0", "get-insecure-random-u64")
  def getInsecureRandomU64(): scala.scalajs.wit.unsigned.ULong = scala.scalajs.wit.native

}
