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

package org.scalajs.linker.backend

import scala.concurrent.{ExecutionContext, Future}

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import org.scalajs.logging.Logger

import org.scalajs.linker._
import org.scalajs.linker.interface._
import org.scalajs.linker.interface.unstable._
import org.scalajs.linker.standard._
import org.scalajs.linker.standard.ModuleSet.ModuleID

import org.scalajs.linker.backend.javascript.{ByteArrayWriter, SourceMapWriter}
import org.scalajs.linker.backend.webassembly._

import org.scalajs.linker.backend.wasmemitter.Emitter

final class WebAssemblyLinkerBackend(config: LinkerBackendImpl.Config)
    extends LinkerBackendImpl(config) {

  require(
    coreSpec.moduleKind == ModuleKind.ESModule,
    s"The WebAssembly backend only supports ES modules; was ${coreSpec.moduleKind}."
  )
  require(
    coreSpec.esFeatures.useECMAScript2015Semantics,
    s"The WebAssembly backend only supports the ECMAScript 2015 semantics."
  )

  require(coreSpec.targetIsWebAssembly,
      s"A WebAssembly backend cannot be used with CoreSpec targeting JavaScript")

  val loaderJSFileName = OutputPatternsImpl.jsFile(config.outputPatterns, "__loader")

  private val fragmentIndex = new SourceMapWriter.Index

  private val emitter: Emitter = {
    val loaderModuleName = OutputPatternsImpl.moduleName(config.outputPatterns, "__loader")
    new Emitter(Emitter.Config(coreSpec, loaderModuleName))
  }

  val symbolRequirements: SymbolRequirement = emitter.symbolRequirements

  override def injectedIRFiles: Seq[IRFile] = emitter.injectedIRFiles

  def emit(moduleSet: ModuleSet, output: OutputDirectory, logger: Logger)(
      implicit ec: ExecutionContext): Future[Report] = {
    val emitterResult: Emitter.Result = emitter.emit(moduleSet, logger)

    val outputImpl = OutputDirectoryImpl.fromOutputDirectory(output)

    def watFileName(moduleID: ModuleID) = s"${moduleID.id}.wat"
    def wasmFileName(moduleID: ModuleID) = s"${moduleID.id}.wasm"
    def sourceMapFileName(wasmFileName: String) = s"$wasmFileName.map"
    def jsFileName(moduleID: ModuleID) = OutputPatternsImpl.jsFile(config.outputPatterns, moduleID.id)

    def filesToProduceFor(moduleID: ModuleID): Set[String] = {
      val wasmFile = wasmFileName(moduleID)
      val result1 = Set(wasmFile, jsFileName(moduleID))
      val result2 =
        if (config.sourceMap) result1 + sourceMapFileName(wasmFile)
        else result1
      val result3 =
        if (config.prettyPrint) result2 + watFileName(moduleID)
        else result2
      result3
    }

    val filesToProduce =
      Set(loaderJSFileName) ++ emitterResult.body.flatMap(mod => filesToProduceFor(mod._1))

    val ioThrottler = new IOThrottler(config.maxConcurrentWrites)

    def maybeWriteWatFiles(): Future[Unit] = {
      if (config.prettyPrint) {
        Future.traverse(emitterResult.body) { case (moduleID, content) =>
          ioThrottler.throttle {
            Future {
              TextWriter.write(content.wasmModule).getBytes(StandardCharsets.UTF_8)
            }.flatMap { textOutputBytes =>
              outputImpl.writeFull(watFileName(moduleID), ByteBuffer.wrap(textOutputBytes))
            }
          }
        }.map(_ => ())
      } else {
        Future.unit
      }
    }

    def writeModule(moduleID: ModuleID, content: Emitter.Result.Module): Future[Report.Module] = {
      val emitDebugInfo = !config.minify

      def writeWat(): Future[Unit] = {
        Future {
          TextWriter.write(content.wasmModule).getBytes(StandardCharsets.UTF_8)
        }.flatMap { textOutputBytes =>
          outputImpl.writeFull(watFileName(moduleID), ByteBuffer.wrap(textOutputBytes))
        }
      }

      val wasmFile = wasmFileName(moduleID)
      val sourceMapFile = sourceMapFileName(wasmFile)
      val jsFile = jsFileName(moduleID)

      def writeWasm(): Future[Unit] = if (config.sourceMap) {
        val sourceMapWriter = new ByteArrayWriter

        val wasmFileURI = s"./$wasmFile"
        val sourceMapURI = s"./$sourceMapFile"

        val smWriter = new SourceMapWriter(sourceMapWriter, wasmFileURI,
            config.relativizeSourceMapBase, fragmentIndex)
        val binaryOutput = BinaryWriter.writeWithSourceMap(
            content.wasmModule, emitDebugInfo, smWriter, sourceMapURI)
        smWriter.complete()

        outputImpl.writeFull(wasmFile, binaryOutput).flatMap { _ =>
          outputImpl.writeFull(sourceMapFile, sourceMapWriter.toByteBuffer())
        }
      } else {
        val binaryOutput = BinaryWriter.write(content.wasmModule, emitDebugInfo)
        outputImpl.writeFull(wasmFile, binaryOutput)
      }

      def writeJS(): Future[Unit] = {
        outputImpl.writeFull(jsFile, ByteBuffer.wrap(content.jsFileContent))
      }

      val writeFiles = ioThrottler.throttle {
        if (config.prettyPrint)
          writeWat().flatMap(_ => writeWasm()).flatMap(_ => writeJS())
        else
          writeWasm().flatMap(_ => writeJS())
      }

      writeFiles.map { _ =>
        new ReportImpl.ModuleImpl(moduleID.id, jsFile, None, coreSpec.moduleKind)
      }
    }

    def writeLoaderFile(): Future[Unit] = {
      ioThrottler.throttle {
        outputImpl.writeFull(loaderJSFileName, ByteBuffer.wrap(emitterResult.loaderContent))
      }
    }

    if (emitterResult.body.isEmpty) {
      // Don't even write the loader file if there is no module
      for {
        existingFiles <- outputImpl.listFiles()
        _ <- Future.sequence(existingFiles.map { f =>
          ioThrottler.throttle(outputImpl.delete(f))
        })
      } yield {
        new ReportImpl(Nil)
      }
    } else {
      for {
        existingFiles <- outputImpl.listFiles()
        _ <- writeLoaderFile()
        _ <- Future.sequence(existingFiles.filterNot(filesToProduce).map { f =>
          ioThrottler.throttle(outputImpl.delete(f))
        })
        reports <- Future.sequence(emitterResult.body.map { case (moduleID, content) =>
          writeModule(moduleID, content)
        })
      } yield {
        new ReportImpl(reports)
      }
    }
  }
}
