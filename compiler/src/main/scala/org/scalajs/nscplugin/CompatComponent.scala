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

package org.scalajs.nscplugin

import scala.collection.mutable

import scala.reflect.internal.Flags
import scala.tools.nsc._

/** Hacks to have our source code compatible with the compiler internals of all
 *  the versions of Scala that we support.
 *
 *  In general, it tries to provide the newer APIs on top of older APIs.
 *
 *  @author Sébastien Doeraene
 */
trait CompatComponent {
  import CompatComponent.infiniteLoop

  val global: Global

  import global._

  /* global.genBCode.bTypes.initialize()
   *
   * It used to be called initializeCoreBTypes() until 2.12.3.
   */

  implicit final class BTypesCompat(private val self: genBCode.bTypes.type) {
    def initialize(): Unit =
      self.initializeCoreBTypes()

    def initializeCoreBTypes(): Unit =
      infiniteLoop()
  }
}

object CompatComponent {
  private def infiniteLoop(): Nothing =
    throw new AssertionError("Infinite loop in Compat")
}
