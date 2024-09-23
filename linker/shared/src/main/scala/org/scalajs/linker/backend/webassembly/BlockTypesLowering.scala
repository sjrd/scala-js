package org.scalajs.linker.backend.webassembly

import scala.annotation.tailrec

import scala.collection.mutable

import org.scalajs.ir.OriginalName.NoOriginalName

import Identitities._
import Instructions._
import Modules._
import Types._

object BlockTypesLowering {
  def lowerBlockTypes(module: Module): Module = {
    val functionTypes: Map[TypeID, FunctionType] = (for {
      recType <- module.types
      SubType(id, _, _, _, funType: FunctionType) <- recType.subTypes
    } yield {
      id -> funType
    }).toMap

    val newFuncs = module.funcs.map(func => new FuncTransformer(functionTypes, func).tranform())

    new Module(
      module.types,
      module.imports,
      newFuncs,
      module.tags,
      module.globals,
      module.exports,
      module.start,
      module.elems,
      module.datas
    )
  }

  private final class FuncTransformer(functionTypes: Map[TypeID, FunctionType], func: Function) {
    val newInstrs = mutable.ListBuffer.empty[Instr]
    val newLocals = mutable.ListBuffer.empty[Local]

    def tranform(): Function = {
      try {
        processInstructionList(func.body.instr)
      } catch {
        case th: AssertionError =>
          throw new AssertionError(
              s"In ${func.originalName.toString()}: ${th.getMessage()}", th)
      }

      Function(
        func.id,
        func.originalName,
        func.typeID,
        func.params,
        func.results,
        func.locals ::: newLocals.toList,
        Expr(newInstrs.toList),
        func.pos
      )
    }

    private def newLocal(tpe: Type): LocalID = {
      val id = new BTLoweringLocalID
      newLocals += Local(id, NoOriginalName, tpe)
      id
    }

    private def processInstructionList(instrs: List[Instr]): Unit = {
      for (instr <- instrs) {
        def assertNoBlockTypeParams(blockType: BlockType): Unit = blockType match {
          case BlockType.ValueType(ty) =>
            // ok
          case BlockType.FunctionType(funTypeID) =>
            val FunctionType(paramTypes, resultTypes) = functionTypes(funTypeID)
            assert(paramTypes.isEmpty, s"Unexpected $instr with input params $paramTypes")
        }

        instr match {
          case instr: Block =>
            instr.blockTypeArgument match {
              case BlockType.ValueType(ty) =>
                newInstrs += instr

              case BlockType.FunctionType(funTypeID) =>
                val FunctionType(paramTypes, resultTypes) = functionTypes(funTypeID)

                if (paramTypes.isEmpty) {
                  newInstrs += instr
                } else {
                  /* For simplicity, we only handle cases where the resultTypes
                   * has at most length 1. This means that the new block type
                   * need not be a function type.
                   * Our codegen never generates blocks that have params *and*
                   * multiple results.
                   */
                  assert(resultTypes.isEmpty || resultTypes.tail.isEmpty,
                      s"Unexpected block type $paramTypes -> $resultTypes")

                  val remainingBlockType = BlockType.ValueType(resultTypes.headOption)

                  val inputLocals = paramTypes.map(newLocal(_))

                  // store the inputs
                  newInstrs ++= inputLocals.reverse.map(LocalSet(_))

                  // open the block with the amended block type
                  newInstrs += Block(remainingBlockType, instr.label)

                  // load back the inputs
                  newInstrs ++= inputLocals.map(LocalGet(_))
                }
            }

          /* For simplicity, we only handle input param types for `block`s.
           * Our codegen never generates input param types for the other
           * types of structured instructions.
           */
          case instr: BlockTypeLabeledInstr =>
            assertNoBlockTypeParams(instr.blockTypeArgument)
            newInstrs += instr
          case instr: TryTable =>
            assertNoBlockTypeParams(instr.i)
            newInstrs += instr

          case _ =>
            newInstrs += instr
        }
      }
    }
  }

  private final class BTLoweringLocalID extends LocalID
}
