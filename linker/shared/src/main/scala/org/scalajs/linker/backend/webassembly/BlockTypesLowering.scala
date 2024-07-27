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

    val blockResultLocals = mutable.HashMap.empty[LabelID, List[LocalID]]

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

    private def processInstructionList(instrs: List[Instr]): List[Instr] = {
      instrs match {
        case Nil =>
          // Done at the top-level
          Nil

        case (End | Else | _:Catch) :: rest =>
          // Done within the current instruction list
          instrs

        case (instr: StructuredLabeledInstr) :: rest =>
          instr match {
            case instr: TryTable =>
              assert(instr.i.isInstanceOf[BlockType.ValueType],
                  s"Unexpected try_table with function type: $instr")
              newInstrs += instr
              processInstructionList(rest) match {
                case End :: afterBlock =>
                  newInstrs += End
                  processInstructionList(afterBlock)
                case other =>
                  throw new AssertionError(
                      other.mkString("Malformed end of block:\n", "\n", ""))
              }

            case instr: BlockTypeLabeledInstr =>
              instr.blockTypeArgument match {
                case BlockType.ValueType(ty) =>
                  // No need to transform, but we need to dive into the block

                  @tailrec
                  def loop(rest: List[Instr]): List[Instr] = {
                    processInstructionList(rest) match {
                      case End :: tail =>
                        newInstrs += End
                        tail
                      case (head @ (Else | _:Catch)) :: tail =>
                        newInstrs += head
                        loop(tail)
                      case other =>
                        throw new AssertionError(
                            other.mkString("Malformed end of block:\n", "\n", ""))
                    }
                  }

                  newInstrs += instr
                  val afterBlock = loop(rest)
                  processInstructionList(afterBlock)

                case BlockType.FunctionType(funTypeID) =>
                  val FunctionType(paramTypes, resultTypes) = functionTypes(funTypeID)

                  /* For simplicity, we only handle input param types for `block`s.
                   * Our codegen never generates input param types for the other
                   * types of structured instructions.
                   */
                  assert(paramTypes.isEmpty || instr.isInstanceOf[Block],
                      s"Unexpected $instr with input params $paramTypes")

                  val stashedResultTypes = resultTypes.drop(1) // no-op for empty list
                  val remainingBlockType = BlockType.ValueType(resultTypes.headOption)

                  val inputLocals = paramTypes.map(newLocal(_))
                  val stashedResultLocals = stashedResultTypes.map(newLocal(_))

                  if (stashedResultLocals.nonEmpty) {
                    for (label <- instr.label)
                      blockResultLocals(label) = stashedResultLocals
                  }

                  // store the inputs
                  newInstrs ++= inputLocals.reverse.map(LocalSet(_))

                  // open the block with the amended block type
                  newInstrs += (instr match {
                    case Block(_, label) => Block(remainingBlockType, label)
                    case Loop(_, label)  => Loop(remainingBlockType, label)
                    case If(_, label)    => If(remainingBlockType, label)
                    case Try(_, label)   => Try(remainingBlockType, label)
                  })

                  // load back the inputs
                  newInstrs ++= inputLocals.map(LocalGet(_))

                  // process the body, up to the `end`

                  @tailrec
                  def loop(rest: List[Instr]): List[Instr] = {
                    processInstructionList(rest) match {
                      case End :: tail =>
                        newInstrs ++= stashedResultLocals.reverse.map(LocalSet(_))
                        newInstrs += End
                        tail
                      case (head @ (Else | _:Catch)) :: tail =>
                        newInstrs ++= stashedResultLocals.reverse.map(LocalSet(_))
                        newInstrs += head
                        // no need to handle input params here, thanks to the restriction
                        loop(tail)
                      case other =>
                        throw new AssertionError(
                            other.mkString("Malformed end of block:\n", "\n", ""))
                    }
                  }

                  val afterBlock = loop(rest)

                  // load back the stashed results
                  newInstrs ++= stashedResultLocals.map(LocalGet(_))

                  processInstructionList(afterBlock)
              }
          }

          case (instr @ Br(targetLabel)) :: rest =>
            blockResultLocals.get(targetLabel) match {
              case None =>
                newInstrs += instr

              case Some(stashedResultLocals) =>
                newInstrs ++= stashedResultLocals.reverse.map(LocalSet(_))
                newInstrs += instr
            }

            processInstructionList(rest)

          case (instr @ BrIf(targetLabel)) :: rest =>
            blockResultLocals.get(targetLabel) match {
              case None =>
                newInstrs += instr

              case Some(stashedResultLocals) =>
                val condLocal = newLocal(Int32)
                newInstrs += LocalSet(condLocal)
                newInstrs ++= stashedResultLocals.reverse.map(LocalSet(_))
                newInstrs += LocalGet(condLocal)
                newInstrs += instr
                newInstrs ++= stashedResultLocals.map(LocalGet(_))
            }

            processInstructionList(rest)

          case (instr @ BrTable(dispatchVector, defaultLabel)) :: rest =>
            // We assume that all the target labels have the same result types
            blockResultLocals.get(defaultLabel) match {
              case None =>
                newInstrs += instr

              case Some(stashedResultLocals) =>
                throw new AssertionError(
                    s"Unexpected $instr with multiple result types")
            }

            processInstructionList(rest)

          case instr :: rest =>
            newInstrs += instr
            processInstructionList(rest)
      }
    }
  }

  private final class BTLoweringLocalID extends LocalID
}
