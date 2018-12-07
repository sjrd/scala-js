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

import org.scalajs.logging.Logger

import org.scalajs.linker.LinkerOutput
import org.scalajs.linker.standard._
import org.scalajs.linker.backend.LinkerBackendImpl

final class WasmLinkerBackend(config: LinkerBackendImpl.Config)
    extends LinkerBackendImpl(config) {

  private[this] val emitter = new Emitter(config.commonConfig)

  val symbolRequirements: SymbolRequirement = emitter.symbolRequirements

  /** Emit the given [[standard.LinkingUnit LinkingUnit]] to the target output.
   *
   *  @param unit [[standard.LinkingUnit LinkingUnit]] to emit
   *  @param output File to write to
   */
  def emit(unit: LinkingUnit, output: LinkerOutput, logger: Logger): Unit = {
    verifyUnit(unit)

    val builder = newBuilder(output)
    try {
      logger.time("Emitter (write output)") {
        emitter.emitAll(unit, builder, logger)
      }
    } finally {
      builder.complete()
    }
  }

  private def newBuilder(output: LinkerOutput): WasmModuleBuilder = {
    new WasmModuleBuilder(output)
  }

}
