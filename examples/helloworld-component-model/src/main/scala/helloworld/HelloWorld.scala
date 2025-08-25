package helloworld

import scalajs.component.annotation._
import scalajs.{component => cm}

object HelloWorld {
  @ComponentExport("wasi:cli/run@0.2.0", "run")
  def run(): cm.Result[Unit, Unit] = {
    val greeting = greet("Scala")
    println(greeting)
    new cm.Ok(())
  }

  @ComponentImport("scala-wasm:helloworld/greeter", "greet")
  def greet(name: String): String = cm.native
}
