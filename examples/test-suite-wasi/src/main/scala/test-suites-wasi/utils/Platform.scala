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

package org.scalajs.testsuite.utils

// TODO: BuildInfo depends on Formatter that has JS interop
object Platform {

  def scalaVersion: String = "2.12.19" // stub

  final val executingInJVM = false

  def executingInJVMOnLowerThanJDK(version: Int): Boolean = false

  def executingInJVMWithJDKIn(range: Range): Boolean = false

  final val executingInWebAssembly = true

  def usesClosureCompiler: Boolean = false

  def hasMinifiedNames: Boolean = false

  def hasCompliantAsInstanceOfs: Boolean = false // TODO
  def hasCompliantArrayIndexOutOfBounds: Boolean = true
  def hasCompliantArrayStores: Boolean = true
  def hasCompliantNegativeArraySizes: Boolean = true
  def hasCompliantNullPointers: Boolean = true
  def hasCompliantStringIndexOutOfBounds: Boolean = true
  def hasCompliantModule: Boolean = false // TODO
  def hasDirectBuffers: Boolean = true

  def regexSupportsUnicodeCase: Boolean = true
  def regexSupportsUnicodeCharacterClasses: Boolean = true
  def regexSupportsLookBehinds: Boolean = true
}
