package scala.scalajs.wasi.http.types

import scala.scalajs.{component => cm}
import scala.scalajs.component.annotation._

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
@ComponentResourceImport("wasi:http/types@0.2.0", "incoming-body")
trait IncomingBody {
  // %stream: func() -> result<input-stream>;
  @ComponentResourceMethod("stream")
  def stream(): cm.Result[InputStream, Unit] = cm.native

  // finish: static func(this: incoming-body) -> future-trailers;
  // TODO
}