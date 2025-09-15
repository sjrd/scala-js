package helloworld

import scalajs.component.annotation._
import scalajs.{component => cm}

object HelloWorld {
  @ComponentExport("wasi:cli/run@0.2.0", "run")
  def run(): cm.Result[Unit, Unit] = {
    println("Hello world!")
    new cm.Ok(())
  }
}
