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

import scala.collection.mutable
import scala.concurrent._

import org.scalajs.logging._

import org.scalajs.linker.interface._
import org.scalajs.linker.standard._
import org.scalajs.linker.checker._

import org.scalajs.ir
import org.scalajs.ir.Names._
import org.scalajs.ir.Printers.IRTreePrinter
import org.scalajs.ir.Transformers._
import org.scalajs.ir.Traversers._
import org.scalajs.ir.Trees._
import org.scalajs.ir.{Position, Version}

/** Desugars a linking unit. */
final class Desugarer(config: CommonPhaseConfig, checkIRFor: Option[CheckingPhase]) {
  import Desugarer._

  private val desugarTransformer = new DesugarTransformer(config.coreSpec)

  def desugar(unit: LinkingUnit, logger: Logger): LinkingUnit = {
    val result = logger.time("Desugarer: Desugar") {
      val desugaredClasses = unit.classDefs.map(desugarClass(_))
      val desugaredTopLevelExports = unit.topLevelExports.map(desugarTopLevelExport(_))

      new LinkingUnit(desugaredClasses, desugaredTopLevelExports,
          unit.moduleInitializers, unit.globalInfo)
    }

    for (nextPhase <- checkIRFor) {
      logger.time("Desugarer: Check IR") {
        val errorCount = IRChecker.check(result, logger, nextPhase)
        if (errorCount != 0) {
          throw new AssertionError(
              s"There were $errorCount IR checking errors after desugaring (this is a Scala.js bug)")
        }
      }
    }

    result
  }

  private def desugarClass(linkedClass: LinkedClass): LinkedClass = {
    import linkedClass._

    val newMethods = methods.mapConserve { method =>
      if (!desugaringInfo.methods(method.flags.namespace.ordinal).contains(method.methodName))
        method
      else
        desugarTransformer.transformMethodDef(method)
    }

    val newJSConstructorDef =
      if (!desugaringInfo.exportedMembers) jsConstructorDef
      else jsConstructorDef.map(desugarTransformer.transformJSConstructorDef(_))

    val newExportedMembers =
      if (!desugaringInfo.exportedMembers) exportedMembers
      else exportedMembers.map(desugarTransformer.transformJSMethodPropDef(_))

    if ((newMethods eq methods) && (newJSConstructorDef eq jsConstructorDef) &&
        (newExportedMembers eq exportedMembers)) {
      linkedClass
    } else {
      new LinkedClass(
        name,
        kind,
        jsClassCaptures,
        superClass,
        interfaces,
        jsSuperClass,
        jsNativeLoadSpec,
        fields,
        methods = newMethods,
        jsConstructorDef = newJSConstructorDef,
        exportedMembers = newExportedMembers,
        jsNativeMembers,
        optimizerHints,
        pos,
        ancestors,
        hasInstances,
        hasDirectInstances,
        hasInstanceTests,
        hasRuntimeTypeInfo,
        fieldsRead,
        staticFieldsRead,
        staticDependencies,
        externalDependencies,
        dynamicDependencies,
        LinkedClass.DesugaringInfo.Empty,
        version
      )
    }
  }

  private def desugarTopLevelExport(tle: LinkedTopLevelExport): LinkedTopLevelExport = {
    import tle._
    if (!tle.needsDesugaring) {
      tle
    } else {
      val newTree = desugarTransformer.transformTopLevelExportDef(tree)
      new LinkedTopLevelExport(owningClass, newTree, staticDependencies,
          externalDependencies, needsDesugaring = false)
    }
  }
}

private[linker] object Desugarer {

  private final class DesugarTransformer(coreSpec: CoreSpec)
      extends ClassTransformer {

    override def transform(tree: Tree): Tree = {
      tree match {
        case prop: LinkTimeProperty =>
          coreSpec.linkTimeProperties.transformLinkTimeProperty(prop)

        case _ =>
          super.transform(tree)
      }
    }

    /* Transfer Version from old members to transformed members.
     * We can do this because the transformation only depends on the
     * `coreSpec`, which is immutable.
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

    override def transformTopLevelExportDef(exportDef: TopLevelExportDef): TopLevelExportDef = {
      exportDef match {
        case TopLevelMethodExportDef(exportName, jsMethodDef) =>
          val newJSMethodDef = transformJSMethodDef(jsMethodDef)
          TopLevelMethodExportDef(exportName, newJSMethodDef)(exportDef.pos)
        case _ =>
          exportDef
      }
    }
  }
}