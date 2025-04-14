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

package org.scalajs.linker.backend.wasmemitter

import scala.annotation.tailrec

import scala.collection.mutable

import org.scalajs.ir.{Position, UTF8String}
import org.scalajs.ir.Names._
import org.scalajs.ir.OriginalName.NoOriginalName
import org.scalajs.ir.Transformers.LocalScopeTransformer
import org.scalajs.ir.Traversers.LocalScopeTraverser
import org.scalajs.ir.Trees._
import org.scalajs.ir.Types._

import org.scalajs.linker.backend.emitter.Transients._

import EmbeddedConstants._
import WasmTransients._

object GeneratorDesugaring {
  private val FreshLocalBase = UTF8String("z")
  private val FreshLabelBase = UTF8String("state")

  private val LocalsVarNameBase = UTF8String("locals")
  private val GeneratorOpNameBase = UTF8String("op")
  private val OpArgumentNameBase = UTF8String("arg")
  private val ResultVarNameBase = UTF8String("result")
  private val StateVarNameBase = UTF8String("state")
  private val HandlerVarNameBase = UTF8String("handler")
  private val ExceptionVarNameBase = UTF8String("exception")

  private val ReturnLabelBase = UTF8String("return")
  private val LoopLabelBase = UTF8String("loop")

  // TODO deal with Records

  def desugarGenerator(tree: Closure): Closure = {
    val Closure(flags, captureParams, params, restParam, resultType, body, captureValues) = tree

    assert(flags.generator, tree)
    assert(resultType == AnyType, tree)

    if (flags.async)
      throw new IllegalArgumentException(s"Unsupported async function* in WebAssembly: $tree")

    val allParams = captureParams ::: params ::: restParam.toList

    val (originalLocalNames, originalLabelNames) = collectOriginalLocals(allParams, body)
    val freshNameGen = new FreshLocalNameGenerator(originalLocalNames)
    val freshLabelGen = new FreshLabelNameGenerator(originalLabelNames)

    val normalizedBody = new Normalizer(freshNameGen, freshLabelGen)
      .normalizeBody(body)
    println(normalizedBody.show)
    println("=====================")

    val (localsInit, resumeFun) = new Desugar(freshNameGen, freshLabelGen)
      .buildResumeFun(allParams, normalizedBody)

    val result = {
      implicit val pos = tree.pos
      Closure(flags.withGenerator(false), captureParams, params, restParam,
          resultType, Transient(JSStartGenerator(localsInit, resumeFun)), captureValues)
    }

    println("=====================")
    println(result.show)
    result
  }

  private def collectOriginalLocals(allParams: List[ParamDef],
      tree: Tree): (Seq[LocalName], Seq[LabelName]) = {

    val b = List.newBuilder[LocalName]
    val labels = List.newBuilder[LabelName]

    b += LocalName.This
    for (param <- allParams)
      b += param.name.name

    new LocalScopeTraverser {
      override def traverse(tree: Tree): Unit = {
        tree match {
          case VarDef(LocalIdent(name), _, _, _, _) =>
            b += name
          case Labeled(label, _, _) =>
            labels += label
          case _ =>
            ()
        }
        super.traverse(tree)
      }
    }.traverse(tree)

    (b.result(), labels.result())
  }

  private final class Normalizer(freshNameGen: FreshLocalNameGenerator,
      freshLabelGen: FreshLabelNameGenerator) {

    def normalizeBody(body: Tree): Tree =
      transform(body)

    private def transform(tree: Tree): Tree = {
      implicit val pos = tree.pos

      tree match {
        case JSYield(arg, star) =>
          /* This is why we're here in the first place.
           * Always create a new node, to start the chain of unnesting.
           */
          val newArg = transform(arg)
          JSYield(newArg, star)

        case tree @ VarDef(ident @ LocalIdent(name), originalName, vtpe, mutable, rhs) =>
          val newRhs = transform(rhs)
          if (newRhs eq rhs)
            tree
          else
            VarDef(ident, originalName, vtpe, mutable, newRhs)

        case Block(stats) =>
          val newStats = stats.mapConserve(transform(_))
          if (newStats eq stats)
            tree
          else
            Block(newStats)

        case Assign(lhs: VarRef, rhs) =>
          transformMaybeUnnest(rhs) { newRhs =>
            if (newRhs eq rhs)
              tree
            else
              Assign(lhs, newRhs)
          }

        case Labeled(label, tpe, body) =>
          val newBody = transform(body)
          if (newBody eq body)
            tree
          else
            Labeled(label, tpe, newBody)

        case Return(expr, label) =>
          transformMaybeUnnest(expr) { newExpr =>
            if (newExpr eq expr)
              tree
            else
              Return(newExpr, label)
          }

        case If(cond, thenp, elsep) =>
          transformMaybeUnnest(cond) { newCond =>
            val newThenp = transform(thenp)
            val newElsep = transform(elsep)
            if ((newCond eq cond) && (newThenp eq thenp) && (newElsep eq elsep))
              tree
            else
              If(newCond, newThenp, newElsep)(tree.tpe)
          }

        case While(cond @ BooleanLiteral(true), body) =>
          val newBody = transform(body)
          if (newBody eq body)
            tree
          else
            While(cond, newBody)

        case While(cond, body) =>
          assert(tree.tpe == VoidType)
          val newCond = transform(cond)
          val newBody = transform(body)
          if ((newCond eq cond) && (newBody eq body)) {
            tree
          } else {
            val (condPrep, condRef) = if (newCond eq cond) {
              (Skip(), cond)
            } else {
              val condVarDef = makeTempLocal(newCond)
              (condVarDef, condVarDef.ref)
            }

            val breakLabel = freshLabelGen.freshName()
            Labeled(breakLabel, VoidType, {
              While(BooleanLiteral(true), {
                Block(
                  condPrep,
                  If(UnaryOp(UnaryOp.Boolean_!, condRef),
                      Return(Skip(), breakLabel), Skip())(VoidType),
                  newBody
                )
              })
            })
          }

        case TryCatch(block, errVar, errVarOriginalName, handler) =>
          val newBlock = transform(block)
          val newHandler = transform(handler)
          if ((newBlock eq block) && (newHandler eq handler))
            tree
          else
            TryCatch(newBlock, errVar, errVarOriginalName, newHandler)(tree.tpe)

        case New(cls, ctor, args) =>
          transformMaybeUnnestArgs(args) { newArgs =>
            if (newArgs eq args)
              tree
            else
              New(cls, ctor, newArgs)
          }

        case Select(qualifier, field) =>
          transformMaybeUnnest(qualifier) { newQualifier =>
            if (newQualifier eq qualifier)
              tree
            else
              Select(newQualifier, field)(tree.tpe)
          }

        case Apply(flags, receiver, method, args) =>
          transformMaybeUnnestReceiverAndArgs(receiver, args) { (newReceiver, newArgs) =>
            if ((newReceiver eq receiver) && (newArgs eq args))
              tree
            else
              Apply(flags, newReceiver, method, newArgs)(tree.tpe)
          }

        case ApplyStatically(flags, receiver, cls, method, args) =>
          transformMaybeUnnestReceiverAndArgs(receiver, args) { (newReceiver, newArgs) =>
            if ((newReceiver eq receiver) && (newArgs eq args))
              tree
            else
              ApplyStatically(flags, newReceiver, cls, method, newArgs)(tree.tpe)
          }

        case ApplyStatic(flags, cls, method, args) =>
          transformMaybeUnnestArgs(args) { newArgs =>
            if (newArgs eq args)
              tree
            else
              ApplyStatic(flags, cls, method, newArgs)(tree.tpe)
          }

        case UnaryOp(op, lhs) =>
          transformMaybeUnnest(lhs) { newLhs =>
            if (newLhs eq lhs)
              tree
            else
              UnaryOp(op, newLhs)
          }

        case BinaryOp(op, lhs, rhs) =>
          transformMaybeUnnestArgs(List(lhs, rhs)) { case List(newLhs, newRhs) =>
            if ((newLhs eq lhs) && (newRhs eq rhs))
              tree
            else
              BinaryOp(op, newLhs, newRhs)
          }

        case IsInstanceOf(expr, testType) =>
          transformMaybeUnnest(expr) { newExpr =>
            if (newExpr eq expr)
              tree
            else
              IsInstanceOf(newExpr, testType)
          }

        case AsInstanceOf(expr, tpe) =>
          transformMaybeUnnest(expr) { newExpr =>
            if (newExpr eq expr)
              tree
            else
              AsInstanceOf(newExpr, tpe)
          }

        case _:Skip | _:LoadModule | _:Literal | _:VarRef =>
          tree

        case tree @ Transient(value) =>
          value match {
            case Cast(expr, tpe) =>
              transformMaybeUnnest(expr) { newExpr =>
                if (newExpr eq expr)
                  tree
                else
                  Transient(Cast(newExpr, tpe))
              }

            case _ =>
              throw new UnsupportedOperationException(
                  s"Transient not yet supported: ${value.getClass().getSimpleName()} at ${tree.pos}\n${tree.show}")
          }

        case _ =>
          throw new UnsupportedOperationException(
              s"Not yet supported: ${tree.getClass().getSimpleName()} at ${tree.pos}\n${tree.show}")
      }
    }

    private def transformMaybeUnnestReceiverAndArgs(receiver: Tree, args: List[Tree])(
        body: (Tree, List[Tree]) => Tree): Tree = {
      val receiverNotNull =
        if (receiver.tpe.isNullable) UnaryOp(UnaryOp.CheckNotNull, receiver)(receiver.pos)
        else receiver

      val args1 = receiverNotNull :: args
      transformMaybeUnnestArgs(args1) { newArgs1 =>
        if (newArgs1 eq args1)
          body(receiver, args) // preserve `eq` of original receiver
        else
          body(newArgs1.head, newArgs1.tail)
      }
    }

    private def transformMaybeUnnestArgs(args: List[Tree])(body: List[Tree] => Tree): Tree = {
      val newArgs1 = args.mapConserve(transform(_))
      if (newArgs1 eq args) {
        body(newArgs1)
      } else {
        var preps: List[Tree] = Nil
        var newArgs2: List[Tree] = Nil
        var sameSoFar: Boolean = true

        for ((arg, newArg1) <- args.zip(newArgs1).reverse) {
          if (newArg1 ne arg)
            sameSoFar = false

          if (sameSoFar) {
            newArgs2 ::= newArg1
          } else {
            implicit val pos = newArg1.pos
            val tempVarDef = makeTempLocal(newArg1)
            preps ::= tempVarDef
            newArgs2 ::= tempVarDef.ref
          }
        }

        val newBody = body(newArgs2)
        Block(preps, newBody)(newBody.pos)
      }
    }

    private def transformMaybeUnnest(tree: Tree)(body: Tree => Tree): Tree = {
      val newTree = transform(tree)
      if (newTree eq tree) {
        body(tree)
      } else {
        implicit val pos = newTree.pos
        val tempVarDef = makeTempLocal(newTree)
        Block(tempVarDef, body(tempVarDef.ref))
      }
    }

    private def makeTempLocal(tree: Tree): VarDef = {
      implicit val pos = tree.pos

      VarDef(LocalIdent(freshNameGen.freshName()),
          NoOriginalName, tree.tpe, mutable = false, tree)
    }
  }

  private final class Desugar(freshNameGen: FreshLocalNameGenerator,
      freshLabelGen: FreshLabelNameGenerator) {

    private val localsVarName = freshNameGen.freshName(LocalsVarNameBase)
    private val generatorOpName = freshNameGen.freshName(GeneratorOpNameBase)
    private val opArgumentName = freshNameGen.freshName(OpArgumentNameBase)
    private val resultVarName = freshNameGen.freshName(ResultVarNameBase)
    private val stateVarName = freshNameGen.freshName(StateVarNameBase)
    private val handlerVarName = freshNameGen.freshName(HandlerVarNameBase)
    private val exceptionVarName = freshNameGen.freshName(ExceptionVarNameBase)

    private val returnLabel = freshLabelGen.freshName(ReturnLabelBase)
    private val continueNextStateLabel = freshLabelGen.freshName(LoopLabelBase)

    private var currentCatchTarget: Option[ReentryBasicBlock] = None
    private var hasAnyTryCatch: Boolean = false

    private var lastState = -1

    private def newBasicBlock(label: LabelName, inputVarDef: Option[VarDef]): BasicBlock =
      new BasicBlock(label, inputVarDef)

    private def newBasicBlock(inputVarDef: Option[VarDef]): BasicBlock =
      newBasicBlock(freshLabelGen.freshName(), inputVarDef)

    private def newReentryBasicBlock(): ReentryBasicBlock = {
      lastState += 1
      new ReentryBasicBlock(freshLabelGen.freshName(), lastState)
    }

    private val basicBlocks = mutable.ListBuffer.empty[BasicBlock]
    private var currentBasicBlock: BasicBlock = _

    private def startBasicBlock(block: BasicBlock): Unit = {
      basicBlocks += block
      currentBasicBlock = block
    }

    private def makeReturnEntry(value: Tree, done: Boolean)(implicit pos: Position): Tree = {
      Return(JSObjectConstr(List(
        StringLiteral("value") -> value,
        StringLiteral("done") -> BooleanLiteral(done)
      )), returnLabel)
    }

    private def setState(blockNumber: Int)(implicit pos: Position): Tree =
      Assign(VarRef(stateVarName)(IntType), IntLiteral(blockNumber))

    private def setState(block: ReentryBasicBlock)(implicit pos: Position): Tree =
      setState(block.reentryState)

    private def jump(block: BasicBlock)(implicit pos: Position): Tree =
      Return(Skip(), block.label)

    def buildResumeFun(allParams: List[ParamDef], body: Tree): (Tree, Tree) = {
      implicit val pos = body.pos

      val resultVarDef = VarDef(LocalIdent(resultVarName), NoOriginalName,
          AnyType, mutable = false, Null()) // rhs will be discarded

      // We must create the initial block first! It must have state number 0.
      val initialBlock = newReentryBasicBlock()
      val resultBlock = newBasicBlock(Some(resultVarDef))
      val completedBlock = newReentryBasicBlock()

      startBasicBlock(initialBlock)
      unroll(body)

      startBasicBlock(resultBlock)
      currentBasicBlock.stats += setState(completedBlock)
      currentBasicBlock.stats += makeReturnEntry(resultVarDef.ref, done = true)

      startBasicBlock(completedBlock)
      currentBasicBlock.stats += makeReturnEntry(Undefined(), done = true)

      val finalBasicBlocks = basicBlocks.toList

      val varsCrossingBlocks = computeVarsCrossingBlocks(allParams, finalBasicBlocks)

      val paramNames = allParams.map(_.name.name).toSet
      val localsInitFields = varsCrossingBlocks.toList.map { case (name, tpe) =>
        val initValue: Tree = {
          if (paramNames.contains(name)) VarRef(name)(tpe)
          else if (name == stateVarName) IntLiteral(0)
          else if (name == handlerVarName) IntLiteral(-1)
          else Null()
        }
        StringLiteral(name.nameString) -> initValue
      }
      val localsInit = JSObjectConstr(localsInitFields)

      val catchDispatch: Tree = {
        val default = Block(
          setState(completedBlock),
          UnaryOp(UnaryOp.Throw, VarRef(opArgumentName)(AnyType))
        )
        if (hasAnyTryCatch) {
          Block(
            If(BinaryOp(BinaryOp.Int_==, VarRef(handlerVarName)(IntType), IntLiteral(-1)), {
              default
            }, {
              Skip()
            })(VoidType),
            Assign(VarRef(stateVarName)(IntType), VarRef(handlerVarName)(IntType))
          )
        } else {
          default
        }
      }

      val entryChecks: Tree = {
        If(BinaryOp(BinaryOp.Int_!=, VarRef(generatorOpName)(IntType), IntLiteral(GeneratorNext)), {
          If(BinaryOp(BinaryOp.Int_==, VarRef(generatorOpName)(IntType), IntLiteral(GeneratorThrow)), {
            catchDispatch
          }, {
            Block(
              setState(completedBlock),
              makeReturnEntry(VarRef(opArgumentName)(AnyType), done = true)
            )
          })(VoidType)
        }, {
          Skip()
        })(VoidType)
      }

      val theBigStateMatching: Tree = {
        val stateDispatch: Tree = {
          val cases = finalBasicBlocks.collect {
            case reentryBlock: ReentryBasicBlock =>
              List(IntLiteral(reentryBlock.reentryState)) -> Return(Skip(), reentryBlock.label)
          }
          val default = UnaryOp(UnaryOp.Throw, Null())
          Match(VarRef(stateVarName)(IntType), cases, default)(NothingType)
        }

        finalBasicBlocks.foldLeft(stateDispatch) { (inner, block) =>
          val labeled = block.inputVarDef match {
            case None =>
              Labeled(block.label, VoidType, inner)
            case Some(varDef @ VarDef(name, originalName, vtpe, mutable, _)) =>
              VarDef(name, originalName, vtpe, mutable, Labeled(block.label, vtpe, inner))
          }
          val stats = block.stats.toList
          val fullBlockBody = block.explicitTarget match {
            case None                 => stats
            case Some(explicitTarget) => Return(Block(stats), explicitTarget.label) :: Nil
          }
          Block(labeled :: fullBlockBody)
        }
      }

      val body0: Tree = if (!hasAnyTryCatch) {
        theBigStateMatching
      } else {
        TryCatch({
          theBigStateMatching
        }, LocalIdent(exceptionVarName), NoOriginalName, {
          Block(
            Assign(VarRef(opArgumentName)(AnyType), VarRef(exceptionVarName)(AnyType)),
            catchDispatch
          )
        })(VoidType)
      }

      val body1 = {
        Labeled(returnLabel, AnyType, {
          Block(
            entryChecks,
            While(BooleanLiteral(true), {
              Labeled(continueNextStateLabel, VoidType, {
                body0
              })
            })
          )
        })
      }

      def rawLocalRef(name: LocalName)(implicit pos: Position): AssignLhs =
        JSSelect(VarRef(localsVarName)(AnyType), StringLiteral(name.nameString))

      def localRef(name: LocalName)(implicit pos: Position): Tree = {
        varsCrossingBlocks(name) match {
          case AnyType => rawLocalRef(name)
          case tpe     => Transient(Cast(rawLocalRef(name), tpe))
        }
      }

      val resumeFunBody = new LocalScopeTransformer {
        override def transform(tree: Tree): Tree = tree match {
          case VarDef(LocalIdent(name), _, _, _, rhs) if varsCrossingBlocks.contains(name) =>
            implicit val pos = tree.pos
            Assign(rawLocalRef(name), transform(rhs))
          case Assign(VarRef(name), rhs) if varsCrossingBlocks.contains(name) =>
            implicit val pos = tree.pos
            Assign(rawLocalRef(name), transform(rhs))
          case VarRef(name) if varsCrossingBlocks.contains(name) =>
            implicit val pos = tree.pos
            localRef(name)
          case _ =>
            super.transform(tree)
        }
      }.transform(body1)

      val resumeFun = Closure(
        ClosureFlags.arrow,
        Nil,
        List(
          ParamDef(LocalIdent(localsVarName), NoOriginalName, AnyType, mutable = false),
          ParamDef(LocalIdent(generatorOpName), NoOriginalName, IntType, mutable = false), // hack
          ParamDef(LocalIdent(opArgumentName), NoOriginalName, AnyType, mutable = false)
        ),
        None,
        AnyType,
        resumeFunBody,
        Nil
      )

      (localsInit, resumeFun)
    }

    private def computeVarsCrossingBlocks(allParams: List[ParamDef],
        basicBlocks: List[BasicBlock]): mutable.LinkedHashMap[LocalName, Type] = {

      val declSites = mutable.LinkedHashMap.empty[LocalName, (BasicBlock, Type)]
      val crossVars = mutable.HashSet.empty[LocalName]

      val excludedVarNames = Set(opArgumentName)

      val fakeBasicBlock: BasicBlock = null

      for (param <- allParams)
        declSites += param.name.name -> (fakeBasicBlock, param.ptpe)
      declSites += stateVarName -> (fakeBasicBlock, IntType)
      declSites += handlerVarName -> (fakeBasicBlock, IntType)

      for (basicBlock <- basicBlocks) {
        for (inputVarDef <- basicBlock.inputVarDef)
          declSites += inputVarDef.name.name -> (basicBlock, inputVarDef.vtpe)

        val traverser = new LocalScopeTraverser {
          override def traverse(tree: Tree): Unit = {
            tree match {
              case tree @ VarDef(LocalIdent(name), _, vtpe, _, _) =>
                declSites += name -> (basicBlock, vtpe)
              case VarRef(name) =>
                if (!excludedVarNames(name) && declSites(name)._1 != basicBlock)
                  crossVars += name
              case _ =>
                ()
            }
            super.traverse(tree)
          }
        }
        basicBlock.stats.foreach(traverser.traverse(_))
      }

      for {
        (name, (_, tpe)) <- declSites
        if crossVars.contains(name)
      } yield {
        name -> tpe
      }
    }

    private def unroll(tree: Tree): Unit = {
      implicit val pos = tree.pos

      tree match {
        case Block(stats) =>
          stats.foreach(unroll(_))

        case VarDef(name, originalName, vtpe, mutable, Block(stats :+ expr)) =>
          stats.foreach(unroll(_))
          unroll(VarDef(name, originalName, vtpe, mutable, expr))

        case _ if !containsYield(tree) => // FIXME this is likely exponential to check
          currentBasicBlock.stats += tree

        case MaybeVarDefAround(outputVarDef, JSYield(yieldArg, star)) =>
          if (star)
            throw new UnsupportedOperationException(s"yield* not supported: ${tree.show}")

          val resumeBlock = newReentryBasicBlock()
          currentBasicBlock.stats += setState(resumeBlock)
          currentBasicBlock.stats += makeReturnEntry(yieldArg, done = false)
          startBasicBlock(resumeBlock)

          for (varDef <- outputVarDef)
            currentBasicBlock.stats += varDef.copy(rhs = VarRef(opArgumentName)(AnyType))

        case MaybeVarDefAround(outputVarDef, Labeled(label, tpe, body)) =>
          val labeledEndBlock = newBasicBlock(label, outputVarDef)
          unroll(body)
          startBasicBlock(labeledEndBlock)

        case If(cond, thenp, Skip()) =>
          val endBlock = newBasicBlock(inputVarDef = None)
          currentBasicBlock.stats +=
            If(UnaryOp(UnaryOp.Boolean_!, cond), jump(endBlock), Skip())(VoidType)
          unroll(thenp)
          startBasicBlock(endBlock)

        case MaybeVarDefAround(outputVarDef, If(cond, thenp, elsep)) =>
          val endBlock = newBasicBlock(outputVarDef)
          val elsepBlock = newBasicBlock(inputVarDef = None)
          currentBasicBlock.stats +=
            If(UnaryOp(UnaryOp.Boolean_!, cond), jump(elsepBlock), Skip())(VoidType)
          unroll(thenp)
          currentBasicBlock.setExplicitTarget(endBlock)
          startBasicBlock(elsepBlock)
          unroll(elsep)
          startBasicBlock(endBlock)

        case MaybeVarDefAround(outputVarDef, While(BooleanLiteral(true), body)) =>
          val loopStartBlock = newReentryBasicBlock()
          startBasicBlock(loopStartBlock)
          unroll(body)
          currentBasicBlock.stats += setState(loopStartBlock)
          currentBasicBlock.stats += Return(Skip(), continueNextStateLabel)

          if (outputVarDef.isDefined) {
            // This produces dead code, but we need to define the local variable
            startBasicBlock(newBasicBlock(outputVarDef))
          }

        case MaybeVarDefAround(outputVarDef, TryCatch(block, errVar, errVarOriginalName, handler)) =>
          hasAnyTryCatch = true

          val handlerBlock = newReentryBasicBlock()
          val endBlock = newBasicBlock(outputVarDef)

          val savedCatchTarget = currentCatchTarget
          currentCatchTarget = Some(handlerBlock)

          currentBasicBlock.stats +=
            Assign(VarRef(handlerVarName)(IntType), IntLiteral(handlerBlock.reentryState))
          unroll(block)

          currentCatchTarget = savedCatchTarget

          currentBasicBlock.stats +=
            Assign(VarRef(handlerVarName)(IntType), IntLiteral(currentCatchTarget.fold(-1)(_.reentryState)))
          currentBasicBlock.setExplicitTarget(endBlock)

          startBasicBlock(handlerBlock)
          currentBasicBlock.stats +=
            Assign(VarRef(handlerVarName)(IntType), IntLiteral(currentCatchTarget.fold(-1)(_.reentryState)))
          currentBasicBlock.stats += {
            VarDef(errVar, errVarOriginalName, AnyType, mutable = false,
                VarRef(opArgumentName)(AnyType))
          }
          unroll(handler)

          startBasicBlock(endBlock)

        case _ =>
          throw new UnsupportedOperationException(
              s"Tree with yield not supported yet:\n${tree.show}")
      }
    }

    private def containsYield(tree: Tree): Boolean = tree match {
      case JSYield(_, _) =>
        true
      case VarDef(_, _, _, _, rhs) =>
        containsYield(rhs)
      case Block(stats) =>
        stats.exists(containsYield(_))
      case Labeled(_, _, body) =>
        containsYield(body)
      case If(cond, thenp, elsep) =>
        containsYield(thenp) || containsYield(elsep)
      case While(BooleanLiteral(true), body) =>
        containsYield(body)
      case TryCatch(block, _, _, handler) =>
        containsYield(block) || containsYield(handler)
      case TryFinally(block, finalizer) =>
        containsYield(block) || containsYield(finalizer)
      case _ =>
        false
    }
  }

  private object MaybeVarDefAround {
    def unapply(tree: Tree): Some[(Option[VarDef], Tree)] = tree match {
      case tree @ VarDef(_, _, _, _, rhs) => Some((Some(tree), rhs))
      case _                              => Some((None, tree))
    }
  }

  private def zeroableTypeOf(tpe: Type): Type = tpe match {
    case _ if tpe.isNullable => tpe
    case tpe: ClassType      => tpe.toNullable
    case tpe: ArrayType      => tpe.toNullable
    case tpe: ClosureType    => tpe.toNullable
    case _                   => tpe
  }

  private sealed class BasicBlock(
    val label: LabelName,
    val inputVarDef: Option[VarDef]
  ) {
    val stats = mutable.ListBuffer.empty[Tree]
    var explicitTarget: Option[BasicBlock] = None

    def setExplicitTarget(explicitTarget: BasicBlock): Unit = {
      assert(this.explicitTarget.isEmpty)
      this.explicitTarget = Some(explicitTarget)
    }
  }

  private final class ReentryBasicBlock(
    label: LabelName,
    val reentryState: Int
  ) extends BasicBlock(label, None)

  private abstract class FreshNameGenerator[N <: Name](frozenNames: Seq[N], freshBase: UTF8String) {
    private val usedNames = mutable.HashSet(frozenNames: _*)
    private var lastIndex = 0

    protected def makeName(encoded: UTF8String): N

    @tailrec
    final def freshName(): N = {
      lastIndex += 1
      val result = makeName(freshBase ++ UTF8String(lastIndex.toString()))
      if (usedNames.add(result))
        result
      else
        freshName()
    }

    final def freshName(base: UTF8String): N = {
      @tailrec
      def loop(i: Int): N = {
        val result = makeName(base ++ UTF8String(i.toString()))
        if (usedNames.add(result))
          result
        else
          loop(i + 1)
      }

      loop(1)
    }
  }

  private final class FreshLocalNameGenerator(frozenNames: Seq[LocalName])
      extends FreshNameGenerator[LocalName](frozenNames, FreshLocalBase) {

    protected def makeName(encoded: UTF8String): LocalName =
      LocalName(encoded)
  }

  private final class FreshLabelNameGenerator(frozenNames: Seq[LabelName])
      extends FreshNameGenerator[LabelName](frozenNames, FreshLabelBase) {

    protected def makeName(encoded: UTF8String): LabelName =
      LabelName(encoded)
  }
}
