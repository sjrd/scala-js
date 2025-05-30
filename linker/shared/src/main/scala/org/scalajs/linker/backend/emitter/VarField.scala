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

package org.scalajs.linker.backend.emitter

/** Namespace for generated fields.
 *
 *  Mainly to avoid duplicate strings in memory.
 *
 *  Also gives us additional compile-time safety against typos.
 */
private[emitter] final class VarField private (val str: String) extends AnyVal

private[emitter] object VarField {
  private def mk(str: String): VarField = {
    require(str(0) == '$')
    new VarField(str)
  }

  // Scala class related fields.

  /** Scala classes (constructor functions). */
  final val c = mk("$c")

  /** Inheritable constructor functions for Scala classes. */
  final val h = mk("$h")

  /** Scala class constructors (<init>). */
  final val ct = mk("$ct")

  /** Scala class initializers (<clinit>). */
  final val sct = mk("$sct")

  /** Private (instance) methods.
   *
   *  Also used for the `prototype` of the current class when minifying.
   */
  final val p = mk("$p")

  /** Public static methods. */
  final val s = mk("$s")

  /** Private static methods. */
  final val ps = mk("$ps")

  /** Interface default and hijacked public methods. */
  final val f = mk("$f")

  /** Static fields. */
  final val t = mk("$t")

  /** Scala module accessor. */
  final val m = mk("$m")

  /** Var / let to store Scala module instance.
   *
   * Also used for null check in CoreJSLib.
   */
  final val n = mk("$n")

  // JS class related fields.

  /** JS Class acessor / factories. */
  final val a = mk("$a")

  /** Var / let to store (top-level) JS Class. */
  final val b = mk("$b")

  /** Names for private JS fields. */
  final val r = mk("$r")

  // Reflection

  /** Class data. */
  final val d = mk("$d")

  /** isInstanceOf functions.
   *
   *  Also used as Object.is polyfill.
   */
  final val is = mk("$is")

  /** asInstanceOf functions. */
  final val as = mk("$as")

  /** isInstanceOf for array functions. */
  final val isArrayOf = mk("$isArrayOf")

  /** asInstanceOf for array functions. */
  final val asArrayOf = mk("$asArrayOf")

  // Modules

  /** External ES module imports. */
  final val i = mk("$i")

  /** Internal ES module imports. */
  final val j = mk("$j")

  /** ES module const export names. */
  final val e = mk("$e")

  /** Setters for globally mutable vars (for ES Modules). */
  final val u = mk("$u")

  // Local fields: Used to generate non-clashing *local* identifiers.

  /** Synthetic vars for the FunctionEmitter. */
  final val x = mk("$x")

  /** Dummy inheritable constructors for JS classes. */
  final val hh = mk("$hh")

  /** Local field for class captures. */
  final val cc = mk("$cc")

  /** Local field for super class. */
  final val superClass = mk("$superClass")

  /** Local field for this replacement. */
  final val thiz = mk("$thiz")

  /** Local field for dynamic imports. */
  final val module = mk("$module")

  /** Local field for the magic `data` argument to the constructor of `jl.Class`. */
  final val data = mk("$data")

  // Core fields: Generated by the CoreJSLib

  /** The alias to file level `this` in the generated JS file. */
  final val fileLevelThis = mk("$fileLevelThis")

  /** The TypeData class. */
  final val TypeData = mk("$TypeData")

  /** Long zero. */
  final val L0 = mk("$L0")

  /** DataView for floating point bit manipulation. */
  final val fpBitsDataView = mk("$fpBitsDataView")

  /** Dispatchers. */
  final val dp = mk("$dp")

  // Char

  /** The Char class. */
  final val Char = mk("$Char")

  /** Boxed Char zero. */
  final val bC0 = mk("$bC0")

  /** Box char. */
  final val bC = mk("$bC")

  final val charToString = mk("$cToS")

  final val charAt = mk("$charAt")

  // Object helpers

  final val objectClone = mk("$objectClone")

  final val objectOrArrayClone = mk("$objectOrArrayClone")

  final val objectGetClass = mk("$objectGetClass")

  final val objectClassName = mk("$objectClassName")

  final val throwNullPointerException = mk("$throwNullPointerException")

  final val throwModuleInitError = mk("$throwModuleInitError")

  final val valueDescription = mk("$valueDescription")

  // ID hash subsystem

  final val systemIdentityHashCode = mk("$systemIdentityHashCode")

  final val lastIDHash = mk("$lastIDHash")

  final val idHashCodeMap = mk("$idHashCodeMap")

  // Cast helpers

  final val isByte = mk("$isByte")

  final val isShort = mk("$isShort")

  final val isInt = mk("$isInt")

  final val isLong = mk("$isLong")

  final val isFloat = mk("$isFloat")

  final val throwClassCastException = mk("$throwClassCastException")

  final val noIsInstance = mk("$noIsInstance")

  // Unboxes
  final val uV = mk("$uV")
  final val uZ = mk("$uZ")
  final val uC = mk("$uC")
  final val uB = mk("$uB")
  final val uS = mk("$uS")
  final val uI = mk("$uI")
  final val uJ = mk("$uJ")
  final val uF = mk("$uF")
  final val uD = mk("$uD")
  final val uT = mk("$uT")

  // Arrays

  /** Array constructors. */
  final val ac = mk("$ac")

  /** Inheritable array constructors. */
  final val ah = mk("$ah")

  final val arraycopyGeneric = mk("$arraycopyGeneric")

  final val arraycopyCheckBounds = mk("$arraycopyCheckBounds")

  final val systemArraycopy = mk("$systemArraycopy")

  final val systemArraycopyRefs = mk("$systemArraycopyRefs")

  final val systemArraycopyFull = mk("$systemArraycopyFull")

  final val throwArrayCastException = mk("$throwArrayCastException")

  final val throwArrayIndexOutOfBoundsException = mk("$throwArrayIndexOutOFBoundsException")

  final val throwArrayStoreException = mk("$throwArrayStoreException")

  final val throwNegativeArraySizeException = mk("$throwNegativeArraySizeException")

  // JS helpers

  final val newJSObjectWithVarargs = mk("$newJSObjectWithVarargs")

  final val superGet = mk("$superGet")

  final val superSet = mk("$superSet")

  final val resolveSuperRef = mk("$resolveSuperRef")

  final val moduleDefault = mk("$moduleDefault")

  // Arithmetic Call Helpers

  final val checkIntDivisor = mk("$checkIntDivisor")

  final val checkLongDivisor = mk("$checkLongDivisor")

  final val longClz = mk("$longClz")

  final val longToFloat = mk("$longToFloat")

  final val doubleToLong = mk("$doubleToLong")

  final val doubleToInt = mk("$doubleToInt")

  final val floatToBits = mk("$floatToBits")
  final val floatFromBits = mk("$floatFromBits")
  final val doubleToBits = mk("$doubleToBits")
  final val doubleFromBits = mk("$doubleFromBits")

  // Polyfills

  final val imul = mk("$imul")
  final val clz32 = mk("$clz32")
  final val fround = mk("$fround")
  final val privateJSFieldSymbol = mk("$privateJSFieldSymbol")
  final val getOwnPropertyDescriptors = mk("$getOwnPropertyDescriptors")
}
