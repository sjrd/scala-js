package scala.scalajs.wasi.io

package object streams {

  // Type definitions
  type Error = scala.scalajs.wasi.io.error.Error

  type Pollable = scala.scalajs.wasi.io.poll.Pollable

  @scala.scalajs.wit.annotation.WitVariant
  sealed trait StreamError
  object StreamError {
    final case class LastOperationFailed(value: Error) extends StreamError
    case object Closed extends StreamError
  }

  // Resources
  @scala.scalajs.wit.annotation.WitResourceImport("wasi:io/streams@0.2.0", "input-stream")
  trait InputStream {
    @scala.scalajs.wit.annotation.WitResourceMethod("read")
    def read(len: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[Array[scala.scalajs.wit.unsigned.UByte], StreamError] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("blocking-read")
    def blockingRead(len: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[Array[scala.scalajs.wit.unsigned.UByte], StreamError] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("skip")
    def skip(len: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.ULong, StreamError] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("blocking-skip")
    def blockingSkip(len: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.ULong, StreamError] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("subscribe")
    def subscribe(): Pollable = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceDrop
    def close(): Unit = scala.scalajs.wit.native
  }
  object InputStream {
  }

  @scala.scalajs.wit.annotation.WitResourceImport("wasi:io/streams@0.2.0", "output-stream")
  trait OutputStream {
    @scala.scalajs.wit.annotation.WitResourceMethod("check-write")
    def checkWrite(): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.ULong, StreamError] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("write")
    def write(contents: Array[scala.scalajs.wit.unsigned.UByte]): scala.scalajs.wit.Result[Unit, StreamError] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("blocking-write-and-flush")
    def blockingWriteAndFlush(contents: Array[scala.scalajs.wit.unsigned.UByte]): scala.scalajs.wit.Result[Unit, StreamError] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("flush")
    def flush(): scala.scalajs.wit.Result[Unit, StreamError] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("blocking-flush")
    def blockingFlush(): scala.scalajs.wit.Result[Unit, StreamError] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("subscribe")
    def subscribe(): Pollable = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("write-zeroes")
    def writeZeroes(len: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[Unit, StreamError] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("blocking-write-zeroes-and-flush")
    def blockingWriteZeroesAndFlush(len: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[Unit, StreamError] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("splice")
    def splice(src: InputStream, len: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.ULong, StreamError] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("blocking-splice")
    def blockingSplice(src: InputStream, len: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.ULong, StreamError] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceDrop
    def close(): Unit = scala.scalajs.wit.native
  }
  object OutputStream {
  }

}
