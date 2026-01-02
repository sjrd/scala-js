package echo.exports.wasi.http.v0_2_0

import scala.scalajs.component.annotation._
import scala.scalajs.wasi.http.types._

/** Export interface for wasi:http/incoming-handler@0.2.0 */
@ComponentExportInterface
trait IncomingHandler {
  /**
   * This function is invoked with an incoming HTTP Request, and a resource
   * `response-outparam` which provides the capability to reply with an HTTP
   * Response. The response is sent by calling the `response-outparam.set`
   * method, which allows execution to continue after the response has been
   * sent. This enables both streaming to the response body, and performing other
   * work.
   *
   * The implementor of this function must write a response to the
   * `response-outparam` before returning, or else the caller will respond
   * with an error on its behalf.
   */
  @ComponentExport("wasi:http/incoming-handler@0.2.0", "handle")
  def handle(request: IncomingRequest, responseOut: ResponseOutparam): Unit
}
