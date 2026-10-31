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

import org.scalajs.ir.Names._
import org.scalajs.ir.OriginalName.NoOriginalName
import org.scalajs.ir.Trees.{JSUnaryOp, JSBinaryOp, MemberNamespace}
import org.scalajs.ir.Types.{Type => _, ArrayType => _, _}
import org.scalajs.ir.{OriginalName, Position, Types => irtpe}
import org.scalajs.ir.WellKnownNames._

import org.scalajs.linker.interface.{CheckedBehavior, ModuleKind}
import org.scalajs.linker.standard.{CoreSpec, LinkedGlobalInfo}

import org.scalajs.linker.backend.webassembly._
import org.scalajs.linker.backend.webassembly.Instructions._
import org.scalajs.linker.backend.webassembly.Identitities._
import org.scalajs.linker.backend.webassembly.Modules._
import org.scalajs.linker.backend.webassembly.Types._

object WasmSupportLib {
  val moduleBytes: Array[Byte] = new Builder().build()

  private object Builder {
    private final case class SimpleFunctionID(name: String) extends FunctionID
  }

  private final class Builder {
    import Builder._

    private implicit val noPos: Position = Position.NoPosition

    private val moduleBuilder = new ModuleBuilder()

    def build(): Array[Byte] = {
      genFunctions()

      val buffer = BinaryWriter.write(moduleBuilder.build(), emitDebugInfo = false)
      val bytes = new Array[Byte](buffer.remaining())
      buffer.get(bytes)
      bytes
    }

    private object funcs {
      val floatToBits = SimpleFunctionID("floatToBits")
      val floatFromBits = SimpleFunctionID("floatFromBits")
    }

    private def newFunctionBuilder(functionID: FunctionID): FunctionBuilder =
      new FunctionBuilder(moduleBuilder, functionID, NoOriginalName, Position.NoPosition)

    private def newExportedFunctionBuilder(functionID: SimpleFunctionID): FunctionBuilder = {
      moduleBuilder.addExport(Export(functionID.name, ExportDesc.Func(functionID)))
      newFunctionBuilder(functionID)
    }

    private def genFunctions(): Unit = {
      genFloatToBits()
      genBitsToFloat()
    }

    private def genFloatToBits(): Unit = {
      val fb = newExportedFunctionBuilder(funcs.floatToBits)
      val x = fb.addParam("x", Float32)
      fb.setResultType(Int32)

      fb += LocalGet(x)
      fb += I32ReinterpretF32

      fb.buildAndAddToModule()
    }

    private def genBitsToFloat(): Unit = {
      val fb = newExportedFunctionBuilder(funcs.floatFromBits)
      val x = fb.addParam("x", Int32)
      fb.setResultType(Float32)

      fb += LocalGet(x)
      fb += F32ReinterpretI32

      fb.buildAndAddToModule()
    }
  }
}
