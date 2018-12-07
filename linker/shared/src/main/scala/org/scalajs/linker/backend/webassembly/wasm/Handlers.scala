/* Copyright 2009-2018 EPFL, Lausanne */

package org.scalajs.linker.backend.webassembly.wasm

import Types.Type
import Definitions.ValDef

case class GlobalsHandler(globals: Seq[ValDef]) {
  def getType(l: Label): Type = globals.find(_.name == l).get.tpe
}

class LocalsHandler(args: Seq[ValDef]) {
  private var locals_ = args

  def getFreshLocal(l: Label, tpe: Type): Label = {
    locals_ :+= ValDef(l, tpe)
    l
  }

  def getType(l: Label): Type = locals_.find(_.name == l).get.tpe

  private[wasm] def locals: Seq[ValDef] = {
    locals_.drop(args.size)
  }
}

