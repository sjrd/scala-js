package scala.scalajs.wasi.io

import scala.scalajs.{component => cm}
import scala.scalajs.component.annotation._
import scala.scalajs.component.unsigned._

import scala.scalajs.wasi.io.Error.{Error => WasiIOError}

/** WASI I/O is an I/O abstraction API which is currently focused on providing
 *  stream types.
 *
 *  In the future, the component model is expected to add built-in stream types;
 *  when it does, they are expected to subsume this API.
 *  @see https://github.com/WebAssembly/WASI/blob/main/wasip2/io/streams.wit */
object Streams {

  /** An input bytestream.
   *
   *  `input-stream`s are *non-blocking* to the extent practical on underlying
   *  platforms. I/O operations always return promptly; if fewer bytes are
   *  promptly available than requested, they return the number of bytes promptly
   *  available, which could even be zero. To wait for data to be available,
   *  use the `subscribe` function to obtain a `pollable` which can be polled
   *  for using `wasi:io/poll`.
   */
  @ComponentResourceImport("wasi:io/streams@0.2.0", "input-stream")
  trait InputStream {
    // /// Read bytes from a stream, after blocking until at least one byte can
    // /// be read. Except for blocking, behavior is identical to `read`.
    // @since(version = 0.2.0)
    // blocking-read: func(
    //     /// The maximum number of bytes to read
    //     len: u64
    // ) -> result<list<u8>, stream-error>;
    @ComponentResourceMethod("blocking-read")
    def blockingRead(len: ULong): cm.Result[Array[UByte], StreamError] = cm.native
  }

  /** An output bytestream.
   *
   *  `output-stream`s are *non-blocking* to the extent practical on
   *  underlying platforms. Except where specified otherwise, I/O operations also
   *  always return promptly, after the number of bytes that can be written
   *  promptly, which could even be zero. To wait for the stream to be ready to
   *  accept data, the `subscribe` function to obtain a `pollable` which can be
   *  polled for using `wasi:io/poll`.
   *
   *  Dropping an `output-stream` while there's still an active write in
   *  progress may result in the data being lost. Before dropping the stream,
   *  be sure to fully flush your writes.
   */
  @ComponentResourceImport("wasi:io/streams@0.2.0", "output-stream")
  trait OutputStream {

    @ComponentResourceMethod("blocking-write-and-flush")
    def blockingWriteAndFlush(contents: Array[UByte]): cm.Result[Unit, StreamError]

    @ComponentResourceDrop
    def close(): Unit = cm.native
  }

  @ComponentVariant
  sealed trait StreamError
  object StreamError {
    final case class LastOperationFailed(value: WasiIOError) extends StreamError
    final case object Closed extends StreamError
  }
}
