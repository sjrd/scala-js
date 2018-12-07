package org.scalajs.linker.backend.webassembly

import scala.collection.mutable

import org.scalajs.linker.LinkerOutput

import org.scalajs.linker.backend.webassembly.wasm.Definitions._
import org.scalajs.linker.backend.webassembly.wasm.{Label, Module}

class WasmModuleBuilder(output: LinkerOutput) {
  val imports = mutable.ListBuffer.empty[Import]
  val globals = mutable.ListBuffer.empty[ValDef]
  val table = mutable.ListBuffer.empty[Label]
  val funs = mutable.ListBuffer.empty[FunDef]

  def complete(): Unit = {
    val name = output.jsFileURI.fold("output.wasm")(_.getPath)
    val module = Module(name, imports, globals, Table(table), funs)
    new wasm.FileWriter(module, Set("main")).writeFiles()
  }
}
