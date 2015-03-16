package org.scalajs.core.tools.optimizer

import org.scalajs.core.tools.logging._

import java.io.PrintStream

class IncOptBenchmarkLogger(underlying: Logger,
    measurementsWriter: PrintStream) extends Logger {

  import IncOptBenchmarkLogger._

  def log(level: Level, message: => String): Unit = {
    val msg = message
    underlying.log(level, msg)
    measurementsWriter.println(s"[${level.toString.toLowerCase}] $msg")
  }

  def success(message: => String): Unit =
    underlying.success(message)
  def trace(t: => Throwable): Unit =
    underlying.trace(t)

  val prefix = "++ "

  private def writeMeasurements(line: String): Unit = {
    measurementsWriter.println(prefix +
        "%1$tH:%1$tM:%1$tS ".format(new java.util.Date()) + line)
    underlying.debug(prefix + line)
  }

  private var state: Int = NoState

  def startRun(): Unit = {
    writeMeasurements("#### START RUN")
    state = RunState
  }

  def startIncremental(): Unit = {
    writeMeasurements("## Start Incremental")
    state = IncState
  }

  def startBatch(): Unit = {
    writeMeasurements("## Start Batch")
    state = BatchState
  }

  def startBatchSingleThread(): Unit = {
    writeMeasurements("## Start Batch Single Thread")
    state = BatchSingleThreadState
  }

  def endRun(): Unit = {
    writeMeasurements("#### END RUN")
    state = NoState
  }

  override def time(title: String, nanos: Long): Unit = {
    writeMeasurements(s"$state $title: ${nanos / 1000} us")
  }
}

object IncOptBenchmarkLogger {
  final val NoState = 0
  final val RunState = 1
  final val IncState = 2
  final val BatchState = 3
  final val BatchSingleThreadState = 4
}
