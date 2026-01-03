package helloworld.exports.wasi.cli

@scala.scalajs.wit.annotation.WitExportInterface
trait Run {

  // Functions
  /** Run the program.
   */
  @scala.scalajs.wit.annotation.WitExport("wasi:cli/run@0.2.0", "run")
  def run(): scala.scalajs.wit.Result[Unit, Unit]

}
