package scala.scalajs.wasi.http.types

import scala.scalajs.{component => cm}
import scala.scalajs.component.annotation._

/** Represents an outgoing HTTP Response. */
@ComponentResourceImport("wasi:http/types@0.2.0", "outgoing-response")
trait OutgoingResponse {
  @ComponentResourceMethod("status-code")
  def statusCode(): StatusCode = cm.native

  @ComponentResourceMethod("set-status-code")
  def setStatusCode(statusCode: StatusCode): Unit = cm.native

  @ComponentResourceMethod("headers")
  def headers(): Headers = cm.native

  // body: func() -> result<outgoing-body>;
  @ComponentResourceMethod("body")
  def body(): cm.Result[OutgoingBody, Unit] = cm.native
}
object OutgoingResponse {
  @ComponentResourceConstructor
  def apply(headers: Headers): OutgoingResponse = cm.native
}