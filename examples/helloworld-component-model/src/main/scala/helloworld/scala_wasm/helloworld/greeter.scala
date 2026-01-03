package helloworld.scala_wasm.helloworld

package object greeter {

  // Functions
  @scala.scalajs.wit.annotation.WitImport("scala-wasm:helloworld/greeter", "greet")
  def greet(name: String): String = scala.scalajs.wit.native

}
