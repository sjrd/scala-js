package org.scalajs.linker.backend.webassembly.wasm

import java.io.Writer

import org.scalajs.ir.Printers.IndentationManager

object Printers {
  class TextPrinter(protected val out: Writer) extends IndentationManager {
    def printModule(module: Module): Unit = ()
  }
}
