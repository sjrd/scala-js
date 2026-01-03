package scala.scalajs.wasi.http.types

import scala.scalajs.wit
import scala.scalajs.wit.annotation._

/** Represents the ability to send an HTTP Response.
 *
 *  This resource is used by the `wasi:http/incoming-handler` interface to
 *  allow a Response to be sent corresponding to the Request provided as the
 *  other argument to `incoming-handler.handle`.
 */
@WitResourceImport("wasi:http/types@0.2.0", "response-outparam")
trait ResponseOutparam {
}
object ResponseOutparam {
  @WitResourceStaticMethod("set")
  def set(
    param: ResponseOutparam,
    response: wit.Result[OutgoingResponse, ErrorCode]
  ): Unit = wit.native
}