/* Copyright 2009-2018 EPFL, Lausanne */

package org.scalajs.linker.backend.webassembly.wasm

import Definitions._

// A WebAssembly module
case class Module(
  name: Label,
  imports: Seq[Import],
  globals: Seq[ValDef],
  table: Table,
  functions: Seq[FunDef]
)
