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

import scala.collection.mutable

import org.scalajs.ir.Names._
import org.scalajs.ir.Trees._
import org.scalajs.ir.Types._
import org.scalajs.ir.{ClassKind, Traversers}

import org.scalajs.linker.standard.{LinkedClass, LinkedTopLevelExport}

import EmbeddedConstants._
import WasmContext._

object Preprocessor {
  def preprocess(classes: List[LinkedClass], tles: List[LinkedTopLevelExport]): WasmContext = {
    val staticFieldMirrors = computeStaticFieldMirrors(tles)

    val abstractMethodCalls =
      AbstractMethodCallCollector.collectAbstractMethodCalls(classes, tles)

    val (itableBucketCount, itableBucketAssignments) =
      computeItableBuckets(classes)

    val classInfosBuilder = mutable.HashMap.empty[ClassName, ClassInfo]
    val definedReflectiveProxyNames = mutable.HashSet.empty[MethodName]

    for (clazz <- classes) {
      val classInfo = preprocess(
        clazz,
        staticFieldMirrors.getOrElse(clazz.className, Map.empty),
        itableBucketAssignments.getOrElse(clazz.className, -1),
        classInfosBuilder
      )
      classInfosBuilder += clazz.className -> classInfo

      // For Scala classes, collect the reflective proxy method names that it defines
      if (clazz.kind.isClass || clazz.kind == ClassKind.HijackedClass) {
        for (method <- clazz.methods if method.methodName.isReflectiveProxy)
          definedReflectiveProxyNames += method.methodName
      }
    }

    val classInfos = classInfosBuilder.toMap

    // sort for stability
    val reflectiveProxyIDs = definedReflectiveProxyNames.toList.sorted.zipWithIndex.toMap

    for (clazz <- classes) {
      classInfos(clazz.className).buildMethodTable(
          abstractMethodCalls.getOrElse(clazz.className, Set.empty))
    }

    new WasmContext(classInfos, reflectiveProxyIDs, itableBucketCount)
  }

  private def computeStaticFieldMirrors(
      tles: List[LinkedTopLevelExport]
  ): Map[ClassName, Map[FieldName, List[String]]] = {
    var result = Map.empty[ClassName, Map[FieldName, List[String]]]
    for (tle <- tles) {
      tle.tree match {
        case TopLevelFieldExportDef(_, exportName, FieldIdent(fieldName)) =>
          val className = tle.owningClass
          val mirrors = result.getOrElse(className, Map.empty)
          val newExportNames = exportName :: mirrors.getOrElse(fieldName, Nil)
          val newMirrors = mirrors.updated(fieldName, newExportNames)
          result = result.updated(className, newMirrors)

        case _ =>
      }
    }
    result
  }

  private def preprocess(
      clazz: LinkedClass,
      staticFieldMirrors: Map[FieldName, List[String]],
      itableIdx: Int,
      previousClassInfos: collection.Map[ClassName, ClassInfo]
  ): ClassInfo = {
    val className = clazz.className
    val kind = clazz.kind

    val allFieldDefs: List[FieldDef] =
      if (kind.isClass) {
        val inheritedFields = clazz.superClass match {
          case None      => Nil
          case Some(sup) => previousClassInfos(sup.name).allFieldDefs
        }
        val myFieldDefs = clazz.fields.collect {
          case fd: FieldDef if !fd.flags.namespace.isStatic =>
            fd
          case fd: JSFieldDef =>
            throw new AssertionError(s"Illegal $fd in Scala class $className")
        }
        inheritedFields ::: myFieldDefs
      } else {
        Nil
      }

    val classConcretePublicMethodNames = {
      if (kind.isClass || kind == ClassKind.HijackedClass) {
        for {
          m <- clazz.methods
          if m.body.isDefined && m.flags.namespace == MemberNamespace.Public
        } yield {
          m.methodName
        }
      } else {
        Nil
      }
    }

    val superClass = clazz.superClass.map(sup => previousClassInfos(sup.name))

    val strictClassAncestors =
      if (kind.isClass || kind == ClassKind.HijackedClass) clazz.ancestors.tail
      else Nil

    // Does this Scala class implement any interface?
    val classImplementsAnyInterface =
      strictClassAncestors.exists(a => previousClassInfos(a).isInterface)

    /* Should we emit a vtable/typeData global for this class?
     *
     * There are essentially three reasons for which we need them:
     *
     * - Because there is a `classOf[C]` somewhere in the program; if that is
     *   true, then `clazz.hasRuntimeTypeInfo` is true.
     * - Because it is the vtable of a class with direct instances; in that
     *   case `clazz.hasRuntimeTypeInfo` is also true, as guaranteed by the
     *   Scala.js frontend analysis.
     * - Because we generate a test of the form `isInstanceOf[Array[C]]`. In
     *   that case, `clazz.hasInstanceTests` is true.
     *
     * `clazz.hasInstanceTests` is also true if there is only `isInstanceOf[C]`,
     * in the program, so that is not *optimal*, but it is correct.
     */
    val hasRuntimeTypeInfo = clazz.hasRuntimeTypeInfo || clazz.hasInstanceTests

    val resolvedMethodInfos: Map[MethodName, ConcreteMethodInfo] = {
      if (kind.isClass || kind == ClassKind.HijackedClass) {
        val inherited: Map[MethodName, ConcreteMethodInfo] = superClass match {
          case Some(superClass) => superClass.resolvedMethodInfos
          case None             => Map.empty
        }

        for (methodName <- classConcretePublicMethodNames)
          inherited.get(methodName).foreach(_.markOverridden())

        classConcretePublicMethodNames.foldLeft(inherited) { (prev, methodName) =>
          prev.updated(methodName, new ConcreteMethodInfo(className, methodName))
        }
      } else {
        Map.empty
      }
    }

    val classInfo = {
      new ClassInfo(
        className,
        kind,
        clazz.jsClassCaptures,
        classConcretePublicMethodNames,
        allFieldDefs,
        superClass,
        classImplementsAnyInterface,
        clazz.hasInstances,
        !clazz.hasDirectInstances,
        hasRuntimeTypeInfo,
        clazz.jsNativeLoadSpec,
        clazz.jsNativeMembers.map(m => m.name.name -> m.jsNativeLoadSpec).toMap,
        staticFieldMirrors,
        resolvedMethodInfos,
        itableIdx
      )
    }

    // Update specialInstanceTypes for ancestors of hijacked classes
    if (clazz.kind == ClassKind.HijackedClass) {
      def addSpecialInstanceTypeOnAllAncestors(jsValueType: Int): Unit =
        strictClassAncestors.foreach(previousClassInfos(_).addSpecialInstanceType(jsValueType))

      clazz.className match {
        case BoxedBooleanClass =>
          addSpecialInstanceTypeOnAllAncestors(JSValueTypeFalse)
          addSpecialInstanceTypeOnAllAncestors(JSValueTypeTrue)
        case BoxedStringClass =>
          addSpecialInstanceTypeOnAllAncestors(JSValueTypeString)
        case BoxedDoubleClass =>
          addSpecialInstanceTypeOnAllAncestors(JSValueTypeNumber)
        case BoxedUnitClass =>
          addSpecialInstanceTypeOnAllAncestors(JSValueTypeUndefined)
        case _ =>
          ()
      }
    }

    classInfo
  }

  /** Collects virtual and interface method calls.
   *
   *  That information will be used to decide what entries are necessary in
   *  vtables and itables.
   *
   *  TODO Arguably this is a job for the `Analyzer`.
   */
  private object AbstractMethodCallCollector {
    def collectAbstractMethodCalls(classes: List[LinkedClass],
        tles: List[LinkedTopLevelExport]): Map[ClassName, Set[MethodName]] = {

      val collector = new AbstractMethodCallCollector
      for (clazz <- classes)
        collector.collectAbstractMethodCalls(clazz)
      for (tle <- tles)
        collector.collectAbstractMethodCalls(tle)
      collector.result()
    }
  }

  private class AbstractMethodCallCollector private () extends Traversers.Traverser {
    private val builder = new mutable.AnyRefMap[ClassName, mutable.HashSet[MethodName]]

    private def registerCall(className: ClassName, methodName: MethodName): Unit =
      builder.getOrElseUpdate(className, new mutable.HashSet) += methodName

    def collectAbstractMethodCalls(clazz: LinkedClass): Unit = {
      for (method <- clazz.methods)
        traverseMethodDef(method)
      for (jsConstructor <- clazz.jsConstructorDef)
        traverseJSConstructorDef(jsConstructor)
      for (export <- clazz.exportedMembers)
        traverseJSMethodPropDef(export)
    }

    def collectAbstractMethodCalls(tle: LinkedTopLevelExport): Unit = {
      tle.tree match {
        case TopLevelMethodExportDef(_, jsMethodDef) =>
          traverseJSMethodPropDef(jsMethodDef)
        case _ =>
          ()
      }
    }

    def result(): Map[ClassName, Set[MethodName]] =
      builder.toMap.map(kv => kv._1 -> kv._2.toSet)

    override def traverse(tree: Tree): Unit = {
      super.traverse(tree)

      tree match {
        case Apply(flags, receiver, MethodIdent(methodName), _) if !methodName.isReflectiveProxy =>
          receiver.tpe match {
            case ClassType(className) =>
              registerCall(className, methodName)
            case AnyType =>
              registerCall(ObjectClass, methodName)
            case _ =>
              // For all other cases, including arrays, we will always perform a static dispatch
              ()
          }

        case _ =>
          ()
      }
    }
  }

  /** Group interface types and types that implement any interfaces into buckets,
   *  ensuring that no two types in the same bucket have common subtypes.
   *
   *  For example, given the following type hierarchy (with upper types as
   *  supertypes), types will be assigned to the following buckets:
   *
   *  {{{
   *      A __
   *    / |\  \
   *   /  | \  \
   *  B   C  E  G
   *      | /| /
   *      |/ |/
   *      D  F
   *  }}}
   *
   *  - bucket0: [A]
   *  - bucket1: [B, C, G]
   *  - bucket2: [D, F]
   *  - bucket3: [E]
   *
   *  In the original paper, within each bucket, types are given unique indices
   *  that are local to each bucket. A gets index 0. B, C, and G are assigned
   *  0, 1, and 2 respectively. Similarly, D=0, F=1, and E=0.
   *
   *  This method (called packed encoding) compresses the interface tables
   *  compared to a global 1-1 mapping from interface to index. With the 1-1
   *  mapping strategy, the length of the itables would be 7 (for interfaces
   *  A-G). In contrast, using a packed encoding strategy, the length of the
   *  interface tables is reduced to the number of buckets, which is 4 in this
   *  case.
   *
   *  Each element in the interface tables array corresponds to the interface
   *  table of the type in the respective bucket that the object implements.
   *  For example, an object that implements G (and A) would have an interface
   *  table structured as: [(itable of A), (itable of G), null, null], because
   *  A is in bucket 0 and G is in bucket 1.
   *
   *  {{{
   *      Object implements G
   *                |
   *     +----------+---------+
   *     |  ...class metadata |
   *     +--------------------+             1-1 mapping strategy version
   *     |      vtable        |  +----> [(itable of A), null, null, null, null, null, (itable of G)]
   *     +--------------------+ /
   *     |      itables       +/
   *     +--------------------+\            packed encoding version
   *     |       ...          + +-----> [(itable of A), (itable of G), null, null]
   *     +--------------------+
   *  }}}
   *
   *  To perform an interface dispatch, we can use bucket IDs and indices to
   *  locate the appropriate interface table. For instance, suppose we need to
   *  dispatch for interface G. Knowing that G belongs to bucket 1, we retrieve
   *  the itable for G from i-th element of the itables.
   *
   *  @note
   *    Why the types in the example are assigned to the buckets like that?
   *    - bucket0: [A]
   *      - A is placed alone in the first bucket.
   *      - It cannot be grouped with any of its subtypes as that would violate
   *        the "no common subtypes" rule.
   *    - bucket1: [B, C, G]
   *      - B, C, and G cannot be in the same bucket with A since they are all
   *        direct subtypes of A.
   *      - They are grouped together because they do not share any common subtype.
   *    - bucket2: [D, F]
   *      - D cannot be assigned to neither bucket 0 or 1 because it shares the
   *        same subtype (D itself) with A (in bucket 0) and C (in bucket 1).
   *      - D and F are grouped together because they do not share any common subtype.
   *    - bucket3: [E]
   *     - E shares its subtype with all the other buckets, so it gets assigned
   *       to a new bucket.
   *
   *  @return
   *    The total number of buckets and a map from interface name to
   *    (the index of) the bucket it was assigned to.
   *
   *  @see
   *    The algorithm is based on the "packed encoding" presented in the paper
   *    "Efficient Type Inclusion Tests"
   *    [[https://www.researchgate.net/publication/2438441_Efficient_Type_Inclusion_Tests]]
   */
  private def computeItableBuckets(
      allClasses: List[LinkedClass]): (Int, Map[ClassName, Int]) = {

    val classes = allClasses.filter(c => !c.kind.isJSType && c.hasInstances)

    val isInterface: Set[ClassName] =
      classes.withFilter(_.kind == ClassKind.Interface).map(_.className).toSet

    /* The algorithm separates the type hierarchy into three disjoint subsets:
     *
     * - join types: types with multiple parents (direct supertypes) that have
     *   only single subtyping descendants:
     *   `join(T) = {x ∈ multis(T) | ∄ y ∈ multis(T) : y <: x}`
     *   where multis(T) means types with multiple direct supertypes.
     * - spine types: all ancestors of join types:
     *   `spine(T) = {x ∈ T | ∃ y ∈ join(T) : x ∈ ancestors(y)}`
     * - plain types: types that are neither join nor spine types
     *
     * The bucket assignment process consists of two parts:
     *
     * **1. Assign buckets to spine types**
     *
     * Two spine types can share the same bucket only if they do not have any
     * common join type descendants.
     *
     * Visit spine types in reverse topological order (from leaves to root)
     * because when assigning a spine type to a bucket, the algorithm already
     * has complete information about the join/spine type descendants of that
     * spine type.
     *
     * Assign a bucket to a spine type if adding it does not violate the bucket
     * assignment rule, namely: two spine types can share a bucket only if they
     * do not have any common join type descendants. If no existing bucket
     * satisfies the rule, create a new bucket.
     *
     * **2. Assign buckets to non-spine types (plain and join types)**
     *
     * Visit these types in level order (from root to leaves). For each type,
     * compute the set of buckets already used by its ancestors. Assign the
     * type to any available bucket not in this set. If no available bucket
     * exists, create a new one.
     */

    def getAllInterfaces(clazz: LinkedClass): List[ClassName] =
      clazz.ancestors.filter(isInterface)

    val buckets = new mutable.ListBuffer[Bucket]()

    def findOrCreateBucketSuchThat(p: Bucket => Boolean): Bucket = {
      buckets.find(p).getOrElse {
        val newBucket = new Bucket()
        buckets += newBucket
        newBucket
      }
    }

    /* All join type descendants of the class.
     * Invariant: sets are non-empty when present.
     */
    val joinsOf = new mutable.HashMap[ClassName, mutable.HashSet[ClassName]]()

    val spines = new mutable.HashSet[ClassName]()

    // Phase 1: Assign buckets to spine types
    for {
      clazz <- classes.reverseIterator
      ifaces = getAllInterfaces(clazz)
      if ifaces.nonEmpty
    } {
      val className = clazz.className

      joinsOf.get(className) match {
        case Some(joins) =>
          // This type is a spine type
          assert(joins.nonEmpty, s"Found empty joins set for $className")

          /* Look for an existing bucket to add the spine type to.
           * Two spine types can share a bucket only if they don't have any
           * common join type descendants.
           */
          val bucket = findOrCreateBucketSuchThat(!_.joins.exists(joins))
          bucket.add(className)
          bucket.joins ++= joins

          for (iface <- ifaces)
            joinsOf.getOrElseUpdate(iface, new mutable.HashSet()) ++= joins
          spines.add(className)

        case None if ifaces.length > 1 =>
          // This type is a join type: add to joins map, bucket assignment is done later
          for (iface <- ifaces)
            joinsOf.getOrElseUpdate(iface, new mutable.HashSet()) += className

        case None =>
          // This type is a plain type. Do nothing.
      }
    }

    // The buckets that have been assigned to any of the ancestors of the class
    val usedOf = new mutable.HashMap[ClassName, mutable.HashSet[Bucket]]()

    // Phase 2: Assign buckets to non-spine types (plain and join types)
    for {
      clazz <- classes
      ifaces = getAllInterfaces(clazz)
      if ifaces.nonEmpty && !spines.contains(clazz.name.name)
    } {
      val used = usedOf.getOrElse(clazz.name.name, new mutable.HashSet())
      for {
        iface <- ifaces
        parentUsed <- usedOf.get(iface)
      } {
        used ++= parentUsed
      }

      val bucket = findOrCreateBucketSuchThat(b => !used.contains(b))
      bucket.add(clazz.name.name)
      used.add(bucket)
    }

    // Build result data structure
    val totalBucketCount = buckets.size
    val interfaceNameToBucketBuilder = Map.newBuilder[ClassName, Int]
    for ((bucket, index) <- buckets.toList.zipWithIndex)
      bucket.interfaces.foreach(className => interfaceNameToBucketBuilder += className -> index)

    (totalBucketCount, interfaceNameToBucketBuilder.result())
  }

  private final class Bucket {
    /** The list of interfaces that belong to this bucket. */
    var interfaces: List[ClassName] = Nil

    /** Add an interface to this bucket. */
    def add(clazz: ClassName): Unit =
      interfaces ::= clazz

    /** A set of join types that are descendants of the types assigned to that bucket */
    val joins = new mutable.HashSet[ClassName]()
  }

}
