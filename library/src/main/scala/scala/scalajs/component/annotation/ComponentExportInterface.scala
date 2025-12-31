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

package scala.scalajs.component.annotation

import scala.annotation.meta._

/** Marks a trait as a Wasm Component Model export interface.
 *
 *  Traits annotated with this annotation must:
 *  - Only contain abstract methods (no concrete implementations)
 *  - All methods must be annotated with @ComponentExport
 *  - Be implemented in objects marked with @ComponentImplementation.
 *
 *  Example:
 *  {{{
 *  @ComponentExportInterface
 *  trait Run {
 *    @ComponentExport("wasi:cli/run@0.2.0", "run")
 *    def run(): Result[Unit, Unit]
 *  }
 *
 *  @ComponentImplementation
 *  object RunImpl extends Run {
 *    def run(): Result[Unit, Unit] = {
 *      println("Hello!")
 *      new Ok(())
 *    }
 *  }
 *  }}}
 */
@getter
class ComponentExportInterface extends scala.annotation.StaticAnnotation
