package helloworld.scala_wasm.helloworld

package object greeter {

  // Functions
  @scala.scalajs.component.annotation.ComponentImport("scala-wasm:helloworld/greeter", "greet")
  def greet(name: String): String = scala.scalajs.component.native

}
