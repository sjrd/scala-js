package helloworld.exports.wasi.cli.v0_2_0

import scala.scalajs.wit.annotation._

/** Export interface for wasi:cli/run@0.2.0 */
@WitExportInterface
trait Run {
  /** Run the program. */
  @WitExport("wasi:cli/run@0.2.0", "run")
  def run(): scala.scalajs.wit.Result[Unit, Unit]
}
