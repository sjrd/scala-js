package scala.scalajs.wasi.http.types

import scala.scalajs.wit
import scala.scalajs.wit.annotation._

/** Represents an outgoing HTTP Response. */
@WitResourceImport("wasi:http/types@0.2.0", "outgoing-response")
trait OutgoingResponse {
  @WitResourceMethod("status-code")
  def statusCode(): StatusCode = wit.native

  @WitResourceMethod("set-status-code")
  def setStatusCode(statusCode: StatusCode): Unit = wit.native

  @WitResourceMethod("headers")
  def headers(): Headers = wit.native

  // body: func() -> result<outgoing-body>;
  @WitResourceMethod("body")
  def body(): wit.Result[OutgoingBody, Unit] = wit.native
}
object OutgoingResponse {
  @WitResourceConstructor
  def apply(headers: Headers): OutgoingResponse = wit.native
}