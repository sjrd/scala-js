package scala.scalajs.wasi.http.types

import scala.scalajs.{component => cm}
import scala.scalajs.component.annotation._

@ComponentResourceImport("wasi:http/types@0.2.0", "incoming-request")
trait IncomingRequest {
  /** consume: func() -> result<incoming-body>; */
  @ComponentResourceMethod("consume")
  def consume(): cm.Result[IncomingBody, Unit] = cm.native
}