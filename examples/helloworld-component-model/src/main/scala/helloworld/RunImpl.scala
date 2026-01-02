package helloworld

import scala.scalajs.component.annotation._
import helloworld.exports.wasi.cli.v0_2_0.Run
import scala.scalajs.{component => cm}
import helloworld.scala_wasm.helloworld.greeter.greet

/** Implementation using the new @ComponentImplementation pattern */
@ComponentImplementation
object RunImpl extends Run {

  override def run(): cm.Result[Unit,Unit] = {
    val greeting = greet("Scala")
    println(greeting)
    new cm.Ok(())
  }

}

