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

package org.scalajs.linker.frontend

import scala.concurrent._

import org.scalajs.logging._

import org.scalajs.linker.interface._
import org.scalajs.linker.standard._
import org.scalajs.linker.standard.ModuleSet.ModuleID
import org.scalajs.linker.checker._
import org.scalajs.linker.analyzer._

import org.scalajs.ir
import org.scalajs.ir.Names.ClassName
import org.scalajs.ir.Trees.{ClassDef, MethodDef, NewLambda}
import org.scalajs.ir.Version

import Analysis._

/** Links the information from [[interface.IRFile IRFile]]s into
 *  [[standard.LinkedClass LinkedClass]]es. Does a dead code elimination pass.
 */
final class BaseLinker(config: CommonPhaseConfig, checkIR: Boolean) {
  import BaseLinker._

  private val irLoader = new FileIRLoader
  private val analyzer =
    new Analyzer(config, initial = true, checkIR = checkIR, failOnError = true, irLoader)
  private val methodSynthesizer = new MethodSynthesizer(irLoader)

  def link(irInput: Seq[IRFile],
      moduleInitializers: Seq[ModuleInitializer], logger: Logger,
      symbolRequirements: SymbolRequirement)(
      implicit ec: ExecutionContext): Future[LinkingUnit] = {

    val result = for {
      _ <- irLoader.update(irInput)
      analysis <- logger.timeFuture("Linker: Compute reachability") {
        analyzer.computeReachability(moduleInitializers, symbolRequirements, logger)
      }
      linkResult <- logger.timeFuture("Linker: Assemble LinkedClasses") {
        assemble(moduleInitializers, analysis)
      }
    } yield {
      if (checkIR) {
        logger.time("Linker: Check IR") {
          val errorCount = IRChecker.check(linkResult, logger)
          if (errorCount != 0) {
            throw new LinkingException(
                s"There were $errorCount IR checking errors.")
          }
        }
      }

      linkResult
    }

    result.andThen { case _ =>
      irLoader.cleanAfterRun()
    }
  }

  private def assemble(moduleInitializers: Seq[ModuleInitializer],
      analysis: Analysis)(implicit ec: ExecutionContext): Future[LinkingUnit] = {

    val synthesizedClasses = irLoader.getAllSynthesizedClass()

    def assembleClass(info: ClassInfo) = {
      val (version, classDefFuture) =
        (irLoader.irFileVersion(info.className), irLoader.loadClassDef(info.className))
      val syntheticMethodsFuture = methodSynthesizer.synthesizeMembers(info, analysis)

      for {
        classDef <- classDefFuture
        syntheticMethods <- syntheticMethodsFuture
      } yield {
        BaseLinker.linkClassDef(classDef, version, syntheticMethods, analysis, synthesizedClasses)
      }
    }

    for {
      assembled <- Future.traverse(analysis.classInfos.values)(assembleClass)
    } yield {
      val (linkedClassDefs, linkedTopLevelExports) = assembled.unzip

      val globalInfo = new LinkedGlobalInfo(
        analysis.isClassSuperClassUsed
      )

      new LinkingUnit(config.coreSpec, linkedClassDefs.toList,
          linkedTopLevelExports.flatten.toList,
          moduleInitializers.toList,
          globalInfo)
    }
  }
}

private[frontend] object BaseLinker {

  private final class NewLambdaTransformer(
      synthesizedClasses: Map[SyntheticClassKind, ClassName])
      extends ir.Transformers.ClassTransformer {

    import ir.Trees._

    override def transform(tree: Tree): Tree = {
      tree match {
        case NewLambda(descriptor, fun) =>
          implicit val pos = tree.pos

          synthesizedClasses.get(SyntheticClassKind.Lambda(descriptor)) match {
            case Some(className) =>
              val closureType = ir.Types.ClosureType(
                  descriptor.paramTypes, descriptor.resultType, nullable = false)
              val ctorName = ir.Names.MethodName.constructor(
                  List(ir.Types.TransientTypeRef(closureType)))

              New(className, MethodIdent(ctorName), List(transform(fun)))

            case None =>
              super.transform(tree)
          }

        case _ =>
          super.transform(tree)
      }
    }

    /* Transfer Version from old members to transformed members.
     * We can do this because the transformation only depends on the
     * `synthesizedClasses` mapping, which is deterministic.
     */

    override def transformMethodDef(methodDef: MethodDef): MethodDef = {
      val newMethodDef = super.transformMethodDef(methodDef)
      newMethodDef.copy()(newMethodDef.optimizerHints, methodDef.version)(newMethodDef.pos)
    }

    override def transformJSConstructorDef(jsConstructor: JSConstructorDef): JSConstructorDef = {
      val newJSConstructor = super.transformJSConstructorDef(jsConstructor)
      newJSConstructor.copy()(newJSConstructor.optimizerHints, jsConstructor.version)(
          newJSConstructor.pos)
    }

    override def transformJSMethodDef(jsMethodDef: JSMethodDef): JSMethodDef = {
      val newJSMethodDef = super.transformJSMethodDef(jsMethodDef)
      newJSMethodDef.copy()(newJSMethodDef.optimizerHints, jsMethodDef.version)(
          newJSMethodDef.pos)
    }

    override def transformJSPropertyDef(jsPropertyDef: JSPropertyDef): JSPropertyDef = {
      val newJSPropertyDef = super.transformJSPropertyDef(jsPropertyDef)
      newJSPropertyDef.copy()(jsPropertyDef.version)(newJSPropertyDef.pos)
    }
  }

  /** Takes a ClassDef and DCE infos to construct a stripped down LinkedClass.
   */
  private[frontend] def linkClassDef(classDef0: ClassDef, version: Version,
      syntheticMethodDefs: List[MethodDef], analysis: Analysis,
      synthesizedClasses: Map[SyntheticClassKind, ClassName]): (LinkedClass, List[LinkedTopLevelExport]) = {
    import ir.Trees._

    val transformer = new NewLambdaTransformer(synthesizedClasses)
    val classDef = transformer.transformClassDef(classDef0)

    val classInfo = analysis.classInfos(classDef.className)

    val fields = classDef.fields.filter {
      case field: FieldDef =>
        if (field.flags.namespace.isStatic)
          classInfo.staticFieldsRead(field.name.name) || classInfo.staticFieldsWritten(field.name.name)
        else if (classInfo.kind.isJSClass || classInfo.isAnySubclassInstantiated)
          classInfo.fieldsRead(field.name.name) || classInfo.fieldsWritten(field.name.name)
        else
          false

      case field: JSFieldDef =>
        classInfo.isAnySubclassInstantiated
    }

    val methods = classDef.methods.filter { m =>
      val methodInfo =
        classInfo.methodInfos(m.flags.namespace)(m.methodName)

      val reachable = methodInfo.isReachable
      assert(m.body.isDefined || !reachable,
          s"The abstract method ${classDef.name.name}.${m.methodName} " +
          "is reachable.")

      reachable
    }

    val jsConstructor =
      if (classInfo.isAnySubclassInstantiated) classDef.jsConstructor
      else None

    val jsMethodProps =
      if (classInfo.isAnySubclassInstantiated) classDef.jsMethodProps
      else Nil

    val jsNativeMembers = classDef.jsNativeMembers
      .filter(m => classInfo.jsNativeMembersUsed.contains(m.name.name))

    val allMethods = methods ++ syntheticMethodDefs

    val ancestors = classInfo.ancestors.map(_.className)

    val linkedClass = new LinkedClass(
        classDef.name,
        classDef.kind,
        classDef.jsClassCaptures,
        classDef.superClass,
        classDef.interfaces,
        classDef.jsSuperClass,
        classDef.jsNativeLoadSpec,
        fields,
        allMethods,
        jsConstructor,
        jsMethodProps,
        jsNativeMembers,
        classDef.optimizerHints,
        classDef.pos,
        ancestors.toList,
        hasInstances = classInfo.isAnySubclassInstantiated,
        hasDirectInstances = classInfo.isInstantiated,
        hasInstanceTests = classInfo.areInstanceTestsUsed,
        hasRuntimeTypeInfo = classInfo.isDataAccessed,
        fieldsRead = classInfo.fieldsRead.toSet,
        staticFieldsRead = classInfo.staticFieldsRead.toSet,
        staticDependencies = classInfo.staticDependencies.toSet,
        externalDependencies = classInfo.externalDependencies.toSet,
        dynamicDependencies = classInfo.dynamicDependencies.toSet,
        version)

    val linkedTopLevelExports = for {
      topLevelExport <- classDef.topLevelExportDefs
    } yield {
      val infos = analysis.topLevelExportInfos(
        (ModuleID(topLevelExport.moduleID), topLevelExport.topLevelExportName))
      new LinkedTopLevelExport(classDef.className, topLevelExport,
          infos.staticDependencies.toSet, infos.externalDependencies.toSet)
    }

    (linkedClass, linkedTopLevelExports)
  }
}
