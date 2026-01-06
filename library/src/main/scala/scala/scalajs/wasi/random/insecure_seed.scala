package scala.scalajs.wasi.random

package object insecure_seed {

  // Functions
  @scala.scalajs.wit.annotation.WitImport("wasi:random/insecure-seed@0.2.0", "insecure-seed")
  def insecureSeed(): scala.scalajs.wit.Tuple2[scala.scalajs.wit.unsigned.ULong, scala.scalajs.wit.unsigned.ULong] = scala.scalajs.wit.native

}
