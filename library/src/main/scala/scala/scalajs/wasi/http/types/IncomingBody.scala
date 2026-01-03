package scala.scalajs.wasi.http.types

import scala.scalajs.wit
import scala.scalajs.wit.annotation._

import scala.scalajs.wasi.io.Streams.InputStream

/** Represents an incoming HTTP Request or Response's Body.
 *
 *  A body has both its contents - a stream of bytes - and a (possibly
 *  empty) set of trailers, indicating that the full contents of the
 *  body have been received. This resource represents the contents as
 *  an `input-stream` and the delivery of trailers as a `future-trailers`,
 *  and ensures that the user of this interface may only be consuming either
 *  the body contents or waiting on trailers at any given time.
 */
@WitResourceImport("wasi:http/types@0.2.0", "incoming-body")
trait IncomingBody {
  // %stream: func() -> result<input-stream>;
  @WitResourceMethod("stream")
  def stream(): wit.Result[InputStream, Unit] = wit.native

  // finish: static func(this: incoming-body) -> future-trailers;
  // TODO
}