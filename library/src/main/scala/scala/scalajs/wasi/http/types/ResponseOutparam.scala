package scala.scalajs.wasi.http.types

import scala.scalajs.{component => cm}
import scala.scalajs.component.annotation._

/** Represents the ability to send an HTTP Response.
 *
 *  This resource is used by the `wasi:http/incoming-handler` interface to
 *  allow a Response to be sent corresponding to the Request provided as the
 *  other argument to `incoming-handler.handle`.
 */
@ComponentResourceImport("wasi:http/types@0.2.0", "response-outparam")
trait ResponseOutparam {
}
object ResponseOutparam {
  @ComponentResourceStaticMethod("set")
  def set(
    param: ResponseOutparam,
    response: cm.Result[OutgoingResponse, ErrorCode]
  ): Unit = cm.native
}