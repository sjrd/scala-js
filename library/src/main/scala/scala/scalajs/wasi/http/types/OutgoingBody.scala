package scala.scalajs.wasi.http.types

import scala.scalajs.wit
import scala.scalajs.wit.annotation._

import scala.scalajs.wasi.io.Streams.OutputStream

/** Represents an outgoing HTTP Request or Response's Body.
 *
 *  A body has both its contents - a stream of bytes - and a (possibly
 *  empty) set of trailers, inducating the full contents of the body
 *  have been sent. This resource represents the contents as an
 *  `output-stream` child resource, and the completion of the body (with
 *  optional trailers) with a static function that consumes the
 *  `outgoing-body` resource, and ensures that the user of this interface
 *  may not write to the body contents after the body has been finished.
 *
 *  If the user code drops this resource, as opposed to calling the static
 *  method `finish`, the implementation should treat the body as incomplete,
 *  and that an error has occurred. The implementation should propagate this
 *  error to the HTTP protocol by whatever means it has available,
 *  including: corrupting the body on the wire, aborting the associated
 *  Request, or sending a late status code for the Response.
 */
@WitResourceImport("wasi:http/types@0.2.0", "outgoing-body")
trait OutgoingBody {
  // write: func() -> result<output-stream>;
  @WitResourceMethod("write")
  def write(): wit.Result[OutputStream, Unit] = wit.native
}
object OutgoingBody {
  /// Finalize an outgoing body, optionally providing trailers. This must be
  /// called to signal that the response is complete. If the `outgoing-body`
  /// is dropped without calling `outgoing-body.finalize`, the implementation
  /// should treat the body as corrupted.
  ///
  /// Fails if the body's `outgoing-request` or `outgoing-response` was
  /// constructed with a Content-Length header, and the contents written
  /// to the body (via `write`) does not match the value given in the
  /// Content-Length.
  // finish: static func(
  //   this: outgoing-body,
  //   trailers: option<trailers>
  // ) -> result<_, error-code>;
  @WitResourceStaticMethod("finish")
  def finish(
    `this`: OutgoingBody,
    trailers: java.util.Optional[Trailers]
  ): wit.Result[Unit, ErrorCode] = wit.native

}