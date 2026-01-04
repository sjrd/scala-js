/*
 * Scala.js (https://www.scala-js.org/)
 *
 * Copyright EPFL.
 *
 * Licensed under Apache License 2.0
 * (https://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package org.scalajs.linker.backend.webassembly

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process._

import java.nio.file.Path

import org.scalajs.linker.interface.unstable.OutputDirectoryImpl
import org.scalajs.logging.Logger

private[backend] object WasmComponentModelProcessorPlatform {
  def create(): WasmComponentModelProcessor = new WasmComponentModelProcessorImpl
}

private final class WasmComponentModelProcessorImpl extends WasmComponentModelProcessor {
  override def processComponentModel(
      outputDirectory: OutputDirectoryImpl,
      wasmFileName: String,
      witDirectory: Path,
      worldName: Option[String],
      logger: Logger
  )(implicit ec: ExecutionContext): Future[Unit] = Future {
    checkWasmToolsInstalled()

    val wasmFilePath = outputDirectory.getAbsolutePath(wasmFileName)

    // Step 1: wasm-tools component embed (in-place)
    val baseEmbedCmd = Seq(
      "wasm-tools", "component", "embed",
      witDirectory.toString,
      wasmFilePath,
      "-o", wasmFilePath
    )

    // Add world name if specified, otherwise wasm-tools will auto-detect
    val embedCmd = worldName match {
      case Some(world) => baseEmbedCmd ++ Seq("-w", world, "--encoding", "utf16")
      case None => baseEmbedCmd ++ Seq("--encoding", "utf16")
    }

    logger.info(s"Running wasm-tools component embed for $wasmFileName")

    val embedProcess = Process(embedCmd)
    val embedOutput = new StringBuilder
    val embedError = new StringBuilder

    val embedLogger = ProcessLogger(
      line => embedOutput.append(line).append("\n"),
      line => embedError.append(line).append("\n")
    )

    val embedResult = embedProcess.!(embedLogger)
    if (embedResult != 0) {
      val errorMsg =
        s"wasm-tools component embed failed with exit code $embedResult:\n${embedError.toString}"
      throw new WasmToolsExecutionException(errorMsg)
    }

    // Step 2: wasm-tools component new (in-place)
    val newCmd = Seq(
      "wasm-tools", "component", "new",
      wasmFilePath,
      "-o", wasmFilePath
    )

    logger.info(s"Running wasm-tools component new for $wasmFileName")

    val newProcess = Process(newCmd)
    val newOutput = new StringBuilder
    val newError = new StringBuilder

    val newLogger = ProcessLogger(
      line => newOutput.append(line).append("\n"),
      line => newError.append(line).append("\n")
    )

    val newResult = newProcess.!(newLogger)
    if (newResult != 0) {
      val errorMsg =
        s"wasm-tools component new failed with exit code $newResult:\n${newError.toString}"
      throw new WasmToolsExecutionException(errorMsg)
    }
  }

  private def checkWasmToolsInstalled(): Unit = {
    try {
      val checkProcess = Process(Seq("wasm-tools", "--version"))
      val checkResult = checkProcess.!(ProcessLogger(_ => (), _ => ()))
      if (checkResult != 0) {
        throw new WasmToolsNotFoundException(
          "wasm-tools is installed but returned non-zero exit code"
        )
      }
    } catch {
      case _: java.io.IOException =>
        throw new WasmToolsNotFoundException(
          """wasm-tools is not installed or not in PATH.
            |
            |To use component model builds, please install wasm-tools:
            |  https://github.com/bytecodealliance/wasm-tools
            |
            |Installation options:
            |  - cargo install wasm-tools
            |  - brew install wasm-tools (macOS)
            |  - Download from GitHub releases
            |""".stripMargin
        )
    }
  }
}
