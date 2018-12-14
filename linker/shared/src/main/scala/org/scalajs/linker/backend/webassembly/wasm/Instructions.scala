package org.scalajs.linker.backend.webassembly.wasm

import Types._

object Instructions {

  final case class Expression(instrs: List[Instruction])

  sealed abstract class Instruction

  object i32 {
    final case class Const(value: Int) extends Instruction

    final case object Add extends Instruction
  }

}
