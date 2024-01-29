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

import scala.annotation.{switch, tailrec}

import java.util.Comparator

import scala.collection.mutable
import scala.reflect.ClassTag

import org.scalajs.ir._
import org.scalajs.ir.Names._
import org.scalajs.ir.OriginalName.NoOriginalName
import org.scalajs.ir.Position._
import org.scalajs.ir.Printers.IRTreePrinter
import org.scalajs.ir.Transformers._
import org.scalajs.ir.Traversers._
import org.scalajs.ir.Trees._
import org.scalajs.ir.Types._

import org.scalajs.linker.interface._
import org.scalajs.linker.interface.CheckedBehavior._
import org.scalajs.linker.backend.javascript.{Trees => js}
import org.scalajs.linker.standard.{LinkedClass, ModuleSet}

import org.scalajs.logging.Logger

import EmitterNames._
import PolyfillableBuiltin._
import Transients._

private[emitter] final class NameCompressor(val config: Emitter.Config) {
  import NameCompressor._

  private var entries: EntryMap = null
  private var ancestorEntries: mutable.AnyRefMap[ClassName, AncestorNameEntry] = null
  private var _dangerousGlobalRefs: Set[String] = null

  def startRun(moduleSet: ModuleSet, logger: Logger): Unit = {
    val traverser = new Traverser(config)

    logger.time("Name compressor: Collect names") {
      traverser.traverseModuleSet(moduleSet)
    }

    entries = traverser.entries
    ancestorEntries = traverser.ancestorEntries
    _dangerousGlobalRefs = traverser.dangerousGlobalRefs.toSet

    logger.time("Name compressor: Allocate property names") {
      allocatePropertyNames(traverser.propertyNamesToAvoid)
    }

    logger.time("Name compressor: Allocate ancestor names") {
      allocateAncestorNames()
    }
  }

  def endRun(logger: Logger): Unit = {
    var atLeastOneNotUsed = false
    for (entry <- entries.valuesIterator ++ ancestorEntries.valuesIterator) {
      if (!entry.checkWasActuallyUsed(logger))
        atLeastOneNotUsed = true
    }
    if (atLeastOneNotUsed)
      throw new AssertionError("There was at least one unused name allocated by the name compressor")
  }

  def dangerousGlobalRefs: Set[String] =
    _dangerousGlobalRefs

  def allocatedFor(className: ClassName, fieldName: FieldName): String =
    entries((className, fieldName)).useAllocatedName()

  def allocatedFor(methodName: MethodName): String =
    entries(methodName).useAllocatedName()

  def allocatedForAncestor(ancestor: ClassName): String =
    ancestorEntries(ancestor).useAllocatedName()

  private def allocatePropertyNames(namesToAvoid: collection.Set[String]): Unit =
    allocateNamesGeneric(entries, PropertyNameEntryComparator, namesToAvoid)

  private def allocateAncestorNames(): Unit =
    allocateNamesGeneric(ancestorEntries, AncestorNameEntryComparator, BasePropertyNamesToAvoid)

  private def allocateNamesGeneric[K <: AnyRef, E <: BaseEntry](
      map: mutable.AnyRefMap[K, E], comparator: Comparator[E],
      namesToAvoid: collection.Set[String])(
      implicit ct: ClassTag[E]): Unit = {
    val orderedEntries = map.values.toArray
    java.util.Arrays.sort(orderedEntries, comparator)

    val generator = new NameGenerator(namesToAvoid)

    for (entry <- orderedEntries)
      entry.setAllocatedName(generator.nextString())
  }
}

private[emitter] object NameCompressor {
  /** Base set of names that should be avoided when allocating property names.
   *
   *  These are the reserved JS identifier names and the `then` name.
   *  `then` is used to identify `Thenable`s, so it must not be used in any
   *  generated situation.
   */
  private val BasePropertyNamesToAvoid: Set[String] =
    NameGen.ReservedJSIdentifierNames + "then"

  private sealed abstract class BaseEntry {
    private var _occurrences: Int = 0
    private var allocatedName: String = null
    private var actuallyUsed: Boolean = false

    def incOccurrences(): Unit =
      _occurrences += 1

    def occurrences: Int = _occurrences

    def setAllocatedName(name: String): Unit =
      allocatedName = name

    def useAllocatedName(): String = {
      actuallyUsed = true
      allocatedName
    }

    def checkWasActuallyUsed(logger: Logger): Boolean = {
      if (!actuallyUsed) {
        logger.error(
            s"The name compressor allocated the name $allocatedName for $this " +
            s"based on a count of $occurrences occurrences but it was never used")
      }
      actuallyUsed
    }
  }

  /** Keys of this map are `(ClassName, FieldName) | MethodName`. */
  private type EntryMap = mutable.AnyRefMap[AnyRef, PropertyNameEntry]

  private sealed abstract class PropertyNameEntry extends BaseEntry

  private final class FieldNameEntry(val className: ClassName, val fieldName: FieldName)
      extends PropertyNameEntry {
    override def toString(): String = s"FieldNameEntry(${className.nameString}, ${fieldName.nameString})"
  }

  private final class MethodNameEntry(val methodName: MethodName)
      extends PropertyNameEntry {
    override def toString(): String = s"MethodNameEntry(${methodName.nameString})"
  }

  private object PropertyNameEntryComparator extends Comparator[PropertyNameEntry] {
    def compare(x: PropertyNameEntry, y: PropertyNameEntry): Int = {
      if (x.occurrences != y.occurrences) y.occurrences - x.occurrences // higher count comes first
      else tieBreak(x, y)
    }

    /** Tie break for stability. */
    private def tieBreak(x: PropertyNameEntry, y: PropertyNameEntry): Int = (x, y) match {
      case (x: FieldNameEntry, y: FieldNameEntry) =>
        val cmp1 = x.className.compareTo(y.className)
        if (cmp1 != 0) cmp1
        else x.fieldName.compareTo(y.fieldName)

      case (x: FieldNameEntry, y: MethodNameEntry) =>
        -1

      case (x: MethodNameEntry, y: MethodNameEntry) =>
        x.methodName.compareTo(y.methodName)

      case (x: MethodNameEntry, y: FieldNameEntry) =>
        1
    }
  }

  private final class AncestorNameEntry(val ancestor: ClassName) extends BaseEntry {
    override def toString(): String = s"AncestorNameEntry(${ancestor.nameString})"
  }

  private object AncestorNameEntryComparator extends Comparator[AncestorNameEntry] {
    def compare(x: AncestorNameEntry, y: AncestorNameEntry): Int =
      if (x.occurrences != y.occurrences) y.occurrences - x.occurrences // higher count comes first
      else x.ancestor.compareTo(y.ancestor) // tie break for stability
  }

  private final class Traverser(config: Emitter.Config)
      extends org.scalajs.ir.Traversers.Traverser {

    private val useBigIntForLongs = config.esFeatures.allowBigIntsForLongs

    private var interfaceClassNames: Set[ClassName] = null

    val dangerousGlobalRefs = mutable.Set.empty[String]

    /** Names that should be avoided when allocating property names. */
    val propertyNamesToAvoid: mutable.Set[String] =
      mutable.Set.empty ++= BasePropertyNamesToAvoid

    /** Keys are `MethodName`s or `(ClassName, FieldName)`. */
    val entries: EntryMap = mutable.AnyRefMap.empty

    val ancestorEntries: mutable.AnyRefMap[ClassName, AncestorNameEntry] = mutable.AnyRefMap.empty

    private def registerPropertyName(className: ClassName, fieldName: FieldName): Unit =
      entries.getOrElseUpdate((className, fieldName), new FieldNameEntry(className, fieldName)).incOccurrences()

    private def registerPropertyName(methodName: MethodName): Unit =
      entries.getOrElseUpdate(methodName, new MethodNameEntry(methodName)).incOccurrences()

    private def registerAncestorName(ancestor: ClassName): Unit = {
      if (ancestor != ObjectClass)
        ancestorEntries.getOrElseUpdate(ancestor, new AncestorNameEntry(ancestor)).incOccurrences()
    }

    private def registerGlobalRef(name: String): Unit = {
      if (GlobalRefUtils.isDangerousGlobalRef(name))
        dangerousGlobalRefs += name
    }

    def traverseModuleSet(moduleSet: ModuleSet): Unit = {
      interfaceClassNames = (for {
        module <- moduleSet.modules
        linkedClass <- module.classDefs
        if linkedClass.kind == ClassKind.Interface
      } yield {
        linkedClass.name.name
      }).toSet

      // Property names referenced from the CoreJSLib
      registerPropertyName(cloneMethodName)
      if (config.semantics.nullPointers == CheckedBehavior.Unchecked) {
        // See CoreJSLib.defineObjectGetClassFunctions()
        registerPropertyName(getClassMethodName)
        registerPropertyName(getNameMethodName)
      }

      // Ancestor names referenced from the CoreJSLib
      registerAncestorName(CloneableClass)
      registerAncestorName(SerializableClass)

      for (module <- moduleSet.modules) {
        for (linkedClass <- module.classDefs)
          traverseLinkedClass(linkedClass)
        for (topLevelExport <- module.topLevelExports)
          traverseTopLevelExportDef(topLevelExport.tree)
      }
    }

    def traverseLinkedClass(linkedClass: LinkedClass): Unit = {
      if (linkedClass.kind.isClass || linkedClass.kind == ClassKind.HijackedClass) {
        /* For Scala classes, we need to register the definition names of
         * fields and methods for allocation. We also need to register the
         * definition names of member exports as names to avoid, in order to
         * prevent collisions.
         *
         * Note: Methods of hijacked classes are referred to in a normal
         * `genApply` from the dispatchers generated by
         * `CoreJSLib.defineDispatchFunctions()`.
         */
        for (field <- linkedClass.fields)
          traverseAnyFieldDefInScalaClass(linkedClass.name.name, field)
        for (method <- linkedClass.methods)
          traverseMethodDefInScalaClass(method)
        for (exportedMember <- linkedClass.exportedMembers)
          traverseJSMethodPropDefInScalaClass(exportedMember)
      } else {
        /* For all other types, including Scala interfaces, we only need to
         * recurse inside the bodies. The definition names are not emitted.
         */
        for (field <- linkedClass.fields)
          traverseAnyFieldDef(field)
        for (method <- linkedClass.methods)
          traverseMethodDef(method)
        for (exportedMember <- linkedClass.exportedMembers)
          traverseJSMethodPropDef(exportedMember)
      }

      if (linkedClass.hasInstanceTests)
        registerAncestorName(linkedClass.name.name)
      if (linkedClass.hasRuntimeTypeInfo) {
        for (ancestor <- linkedClass.ancestors)
          registerAncestorName(ancestor)
      }
    }

    def traverseAnyFieldDefInScalaClass(className: ClassName, fieldDef: AnyFieldDef): Unit = {
      traverseAnyFieldDef(fieldDef)

      fieldDef match {
        case fieldDef: FieldDef =>
          if (!fieldDef.flags.namespace.isStatic)
            registerPropertyName(className, fieldDef.name.name)
        case _: JSFieldDef =>
          ()
      }
    }

    def traverseMethodDefInScalaClass(methodDef: MethodDef): Unit = {
      traverseMethodDef(methodDef)

      if (methodDef.flags.namespace == MemberNamespace.Public)
        registerPropertyName(methodDef.name.name)
    }

    def traverseJSMethodPropDefInScalaClass(jsMethodPropDef: JSMethodPropDef): Unit = {
      traverseJSMethodPropDef(jsMethodPropDef)

      jsMethodPropDef match {
        case JSMethodDef(_, StringLiteral(name), _, _, _) =>
          propertyNamesToAvoid += name
        case JSPropertyDef(_, StringLiteral(name), _, _) =>
          propertyNamesToAvoid += name
        case _ =>
          ()
      }
    }

    override def traverse(tree: Tree): Unit = {
      super.traverse(tree)

      tree match {
        case Select(_, className, FieldIdent(fieldName)) =>
          registerPropertyName(className, fieldName)

        case Apply(_, receiver, MethodIdent(methodName), _) =>
          // Ideally we should also ignore hijacked method calls
          if (receiver.tpe != AnyType || methodName.isReflectiveProxy)
            registerPropertyName(methodName)

        case ApplyStatically(flags, _, className, MethodIdent(methodName), _) =>
          if (!flags.isConstructor && !flags.isPrivate && !interfaceClassNames.contains(className))
            registerPropertyName(methodName)

        case UnaryOp(op, _) if !useBigIntForLongs =>
          import UnaryOp._
          (op: @switch) match {
            case IntToLong    => registerPropertyName(LongImpl.fromInt)
            case LongToInt    => registerPropertyName(LongImpl.toInt)
            case LongToDouble => registerPropertyName(LongImpl.toDouble)
            case DoubleToLong => registerPropertyName(LongImpl.fromDouble)
            case LongToFloat  => registerPropertyName(LongImpl.toFloat)
            case _            => ()
          }

        case BinaryOp(op, lhs, _) if !useBigIntForLongs =>
          import BinaryOp._
          (op: @switch) match {
            case Long_+ => registerPropertyName(LongImpl.+)
            case Long_- =>
              lhs match {
                case LongLiteral(0L) => registerPropertyName(LongImpl.UNARY_-)
                case _               => registerPropertyName(LongImpl.-)
              }
            case Long_* => registerPropertyName(LongImpl.*)
            case Long_/ => registerPropertyName(LongImpl./)
            case Long_% => registerPropertyName(LongImpl.%)
            case Long_| => registerPropertyName(LongImpl.|)
            case Long_& => registerPropertyName(LongImpl.&)
            case Long_^ =>
              lhs match {
                case LongLiteral(-1L) => registerPropertyName(LongImpl.UNARY_~)
                case _                => registerPropertyName(LongImpl.^)
              }
            case Long_<<  => registerPropertyName(LongImpl.<<)
            case Long_>>> => registerPropertyName(LongImpl.>>>)
            case Long_>>  => registerPropertyName(LongImpl.>>)
            case Long_==  => registerPropertyName(LongImpl.===)
            case Long_!=  => registerPropertyName(LongImpl.!==)
            case Long_<   => registerPropertyName(LongImpl.<)
            case Long_<=  => registerPropertyName(LongImpl.<=)
            case Long_>   => registerPropertyName(LongImpl.>)
            case Long_>=  => registerPropertyName(LongImpl.>=)
            case _        => ()
          }

        case Clone(expr) =>
          if (expr.tpe.isInstanceOf[ArrayType])
            registerPropertyName(cloneMethodName)

        case UnwrapFromThrowable(expr) =>
          registerPropertyName(JavaScriptExceptionClass, exceptionFieldName)

        case JSGlobalRef(name) =>
          registerGlobalRef(name)

        case Transient(ZeroOf(_)) =>
          registerPropertyName(ClassClass, dataFieldName)

        case Transient(NativeArrayWrapper(elemClass, _)) =>
          if (!elemClass.isInstanceOf[ClassOf])
            registerPropertyName(ClassClass, dataFieldName)

        case _ =>
          ()
      }
    }
  }

  // private[emitter] for tests
  private[emitter] final class NameGenerator(namesToAvoid: collection.Set[String]) {
    /* 6 because 52 * (62**5) > Int.MaxValue
     * i.e., to exceed this size we would need more than Int.MaxValue different names.
     */
    private val charArray = new Array[Char](6)
    charArray(0) = 'a'
    private var charCount = 1

    @tailrec
    private def incAtIndex(idx: Int): Unit = {
      (charArray(idx): @switch) match {
        case '9' =>
          charArray(idx) = 'a'
        case 'z' =>
          charArray(idx) = 'A'
        case 'Z' =>
          if (idx > 0) {
            charArray(idx) = '0'
            incAtIndex(idx - 1)
          } else {
            java.util.Arrays.fill(charArray, '0')
            charArray(0) = 'a'
            charCount += 1
          }
        case c =>
          charArray(idx) = (c + 1).toChar
      }
    }

    @tailrec
    final def nextString(): String = {
      val s = new String(charArray, 0, charCount)
      incAtIndex(charCount - 1)
      if (namesToAvoid.contains(s))
        nextString()
      else
        s
    }
  }
}
