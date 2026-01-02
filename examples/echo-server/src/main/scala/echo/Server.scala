/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package echo

import scala.scalajs.component.annotation._
import scala.scalajs.{component => cm}
import scala.scalajs.wasi.http.types._

import scala.scalajs.ComponentUtils._
import scala.collection.mutable

import echo.exports.wasi.http.v0_2_0.IncomingHandler

@ComponentImplementation
object Server extends IncomingHandler {
  override def handle(request: IncomingRequest, outParam: ResponseOutparam): Unit = {
    val inputBody = (for {
      body <- toEither(request.consume())
      inputStream <- toEither(body.stream())
    } yield {
      var eof = false

      val in = mutable.ArrayBuffer.empty[Byte]
      while (!eof) {
        toEither(inputStream.blockingRead(1024l)) match {
          case Right(bytes) =>
            if (bytes.length == 0)
              eof = true
            else
              in ++= bytes
          case Left(_) =>
            eof = true
        }
      }
      in.toArray
    }).getOrElse(throw new Error("failed to obtain request body"))


    val headers: Headers = Fields()
    val resp = OutgoingResponse(headers)
    val body: OutgoingBody = toEither(resp.body()).getOrElse(throw new Error("failed to obtain outgoing response"))

    ResponseOutparam.set(outParam, new cm.Ok(resp))

    val out = toEither(body.write()).getOrElse(throw new Error("failed to get outgoing stream"))
    out.blockingWriteAndFlush(inputBody)

    out.close()

    toEither(OutgoingBody.finish(body, java.util.Optional.empty[Trailers]())).getOrElse(
        throw new Error("failed to finish outgoing body"))
  }
}
