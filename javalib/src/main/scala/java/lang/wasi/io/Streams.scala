package java.lang.wasi.io

import scala.scalajs.{component => cm}
import scala.scalajs.component.annotation._
import scala.scalajs.component.unsigned._

import wasi.io.Error.{Error => WasiIOError}

/** https://github.com/WebAssembly/WASI/blob/main/wasip2/io/streams.wit */
object Streams {

  @ComponentResourceImport("wasi:io/streams@0.2.0", "output-stream")
  trait OutputStream {

    @ComponentResourceMethod("blocking-write-and-flush")
    def blockingWriteAndFlush(contents: Array[UByte]): cm.Result[Unit, StreamError]

    @ComponentResourceDrop
    def close(): Unit = cm.native
  }

  sealed trait StreamError extends cm.Variant
  object StreamError {
    final class LastOperationFailed(val value: WasiIOError) extends StreamError {
      type T = WasiIOError
      val _index: Int = 0
    }

    final object Closed extends StreamError {
      type T = Unit
      val value = ()
      val _index = 1
    }
  }
}
