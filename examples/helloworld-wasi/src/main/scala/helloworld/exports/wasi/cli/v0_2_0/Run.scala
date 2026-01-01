package helloworld.exports.wasi.cli.v0_2_0

import scala.scalajs.component.annotation._

/** Export interface for wasi:cli/run@0.2.0 */
@ComponentExportInterface
trait Run {
  /** Run the program. */
  @ComponentExport("wasi:cli/run@0.2.0", "run")
  def run(): scala.scalajs.component.Result[Unit, Unit]
}
