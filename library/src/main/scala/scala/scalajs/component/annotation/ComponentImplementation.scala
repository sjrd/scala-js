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

/** Marks a static object as an implementation of Wasm component exports.
 *
 *  The annotated object must:
 *  - Be a static object (top-level or nested in another static object)
 *  - Extend a trait that is annotated with @ComponentExportInterface
 *
 *  Example:
 *  {{{
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
class ComponentImplementation extends scala.annotation.StaticAnnotation
