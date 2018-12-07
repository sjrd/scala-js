/* Copyright 2009-2018 EPFL, Lausanne */

package org.scalajs.linker.backend.webassembly.wasm

object Types {
  trait Type {
    val size: Int
    def bitSize: Int = size * 8
  }
  case object i32 extends Type { val size = 4 }
  case object i64 extends Type { val size = 8 }
  case object f32 extends Type { val size = 4 }
  case object f64 extends Type { val size = 8 }
  case object void extends Type {
    val size = 0
    override def toString = ""
  }
}
