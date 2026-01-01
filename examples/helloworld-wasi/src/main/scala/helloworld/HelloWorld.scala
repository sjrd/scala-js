package helloworld

import scala.scalajs.component.annotation._
import scala.scalajs.{component => cm}
import helloworld.exports.wasi.cli.v0_2_0.Run

@ComponentImplementation
object HelloWorld extends Run {
  override def run(): cm.Result[Unit, Unit] = {
    println("Hello world!")
    new cm.Ok(())
  }
}
