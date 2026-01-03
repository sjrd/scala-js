package scala.scalajs.wasi.http.types

import scala.scalajs.wit
import scala.scalajs.wit.annotation._

@WitResourceImport("wasi:http/types@0.2.0", "incoming-request")
trait IncomingRequest {
  /** consume: func() -> result<incoming-body>; */
  @WitResourceMethod("consume")
  def consume(): wit.Result[IncomingBody, Unit] = wit.native
}