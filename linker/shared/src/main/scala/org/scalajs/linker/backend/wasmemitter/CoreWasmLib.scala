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

import org.scalajs.ir.Names._
import org.scalajs.ir.OriginalName.NoOriginalName
import org.scalajs.ir.Trees.{JSUnaryOp, JSBinaryOp, MemberNamespace}
import org.scalajs.ir.Types.{Type => _, ArrayType => _, _}
import org.scalajs.ir.{OriginalName, Position, Types => irtpe}
import org.scalajs.ir.WellKnownNames._

import org.scalajs.linker.interface.CheckedBehavior
import org.scalajs.linker.standard.{CoreSpec, LinkedGlobalInfo}

import org.scalajs.linker.backend.webassembly._
import org.scalajs.linker.backend.webassembly.Instructions._
import org.scalajs.linker.backend.webassembly.Identitities._
import org.scalajs.linker.backend.webassembly.Modules._
import org.scalajs.linker.backend.webassembly.Types._

import EmbeddedConstants._
import VarGen._
import SWasmGen._
import TypeTransformer._

final class CoreWasmLib(coreSpec: CoreSpec, globalInfo: LinkedGlobalInfo) {
  import RefType.anyref
  import coreSpec.semantics
  import coreSpec.wasmFeatures.targetPureWasm

  val stringType = if (targetPureWasm) RefType(genTypeID.wasmString) else RefType.extern
  val nullableStringType =
    if (targetPureWasm) RefType.nullable(genTypeID.wasmString)
    else RefType.externref

  private implicit val noPos: Position = Position.NoPosition

  private val primRefsWithKinds = List(
    VoidRef -> KindVoid,
    BooleanRef -> KindBoolean,
    CharRef -> KindChar,
    ByteRef -> KindByte,
    ShortRef -> KindShort,
    IntRef -> KindInt,
    LongRef -> KindLong,
    FloatRef -> KindFloat,
    DoubleRef -> KindDouble
  )

  private val arrayBaseRefs: List[NonArrayTypeRef] = List(
    BooleanRef,
    CharRef,
    ByteRef,
    ShortRef,
    IntRef,
    LongRef,
    FloatRef,
    DoubleRef,
    ClassRef(ObjectClass)
  )

  private def charCodeForOriginalName(baseRef: NonArrayTypeRef): Char = baseRef match {
    case baseRef: PrimRef => baseRef.charCode
    case _: ClassRef      => 'O'
  }

  /** Fields of the `typeData` struct definition.
   *
   *  They are accessible as a public list because they must be repeated in every vtable type
   *  definition.
   *
   *  @see
   *    [[VarGen.genFieldID.typeData]], which contains documentation of what is in each field.
   */
  val typeDataStructFields: List[StructField] = {
    import genFieldID.typeData._
    import RefType.nullable

    def make(id: FieldID, tpe: Type, isMutable: Boolean): StructField =
      StructField(id, OriginalName(id.toString()), tpe, isMutable)

    val nameFields =
      if (targetPureWasm)
        List(
          make(nameOffset, Int32, isMutable = false),
          make(nameSize, Int32, isMutable = false),
          make(nameStringIndex, Int32, isMutable = false),
          make(name, RefType.nullable(genTypeID.wasmString), isMutable = true)
        )
      else List(make(name, RefType.externref, isMutable = true))

    nameFields :::
    List(
      make(kind, Int32, isMutable = false),
      make(specialInstanceTypes, Int32, isMutable = false),
      make(strictAncestors, nullable(genTypeID.typeDataArray), isMutable = false),
      make(componentType, nullable(genTypeID.typeData), isMutable = false),
      make(classOfValue, nullable(genTypeID.ClassStruct), isMutable = true),
      make(arrayOf, nullable(genTypeID.ObjectVTable), isMutable = true),
      make(cloneFunction, nullable(genTypeID.cloneFunctionType), isMutable = false),
      make(
        isJSClassInstance,
        nullable(genTypeID.isJSClassInstanceFuncType),
        isMutable = false
      ),
      make(
        reflectiveProxies,
        RefType(genTypeID.reflectiveProxies),
        isMutable = false
      )
    )
  }

  /** Generates a sequence of instructions that throw the `anyref` on the stack.
   *
   *  When exception handling is supported, this emits a real `Throw`. When it
   *  is not supported, it sets up the `thrownException`/`isThrowing` pair then
   *  returns (there is no `try_table` inside the functions of the
   *  CoreWasmLib). In that case, we need to return a fake result that aligns
   *  with the enclosing function's result type.
   */
  private def genThrow(fb: FunctionBuilder, fakeResult: List[Instr]): Unit = {
    if (!targetPureWasm)
      fb += ExternConvertAny

    if (coreSpec.wasmFeatures.exceptionHandling) {
      fb += Throw(genTagID.exception)
    } else {
      fb += GlobalSet(genGlobalID.thrownException)
      fb += I32Const(1)
      fb += GlobalSet(genGlobalID.isThrowing)
      fb ++= fakeResult
      fb += Return
    }
  }

  /** Generates code that forwards an exception from a function call that always throws.
   *
   *  After this codegen, the stack is in a stack-polymorphic context.
   */
  private def genForwardThrowAlways(fb: FunctionBuilder, fakeResult: List[Instr])(
      implicit ctx: WasmContext): Unit = {
    SWasmGen.genForwardThrowAlwaysAsReturn(fb, fakeResult)
  }

  /** Generates code that possibly forwards an exception from the previous function call.
   *
   *  The stack is not altered by this codegen.
   */
  private def genForwardThrow(fb: FunctionBuilder, fakeResult: List[Instr])(
      implicit ctx: WasmContext): Unit = {
    SWasmGen.genForwardThrowAsReturn(fb, fakeResult)
  }

  /** Generates definitions that must come *before* the code generated for regular classes.
   *
   *  This notably includes the `typeData` definitions, since the vtable of `jl.Object` is a subtype
   *  of `typeData`.
   */
  def genPreClasses()(implicit ctx: WasmContext): Unit = {
    genPreMainRecTypeDefinitions()
    ctx.moduleBuilder.addRecTypeBuilder(ctx.mainRecType)
    genCoreTypesInRecType()

    if (!targetPureWasm) genImports()
    else {
      if (!coreSpec.wasmFeatures.componentModel)
        genWasmEssentialsImports()

      if (coreSpec.wasmFeatures.exceptionHandling) {
        val exceptionSig = FunctionType(List(RefType.anyref), Nil)
        val typeID = ctx.moduleBuilder.functionTypeToTypeID(exceptionSig)
        ctx.moduleBuilder.addTag(
          Tag(
            genTagID.exception,
            OriginalName(genTagID.exception.toString()),
            typeID
          )
        )
      } else {
        ctx.moduleBuilder.addGlobal(
          Global(
            genGlobalID.thrownException,
            OriginalName(genGlobalID.thrownException.toString()),
            isMutable = true,
            RefType.anyref,
            Expr(List(RefNull(HeapType.None)))
          )
        )
        ctx.moduleBuilder.addGlobal(
          Global(
            genGlobalID.isThrowing,
            OriginalName(genGlobalID.isThrowing.toString()),
            isMutable = true,
            Int32,
            Expr(List(I32Const(0)))
          )
        )
      }

      genScalaValueType()
      genBoxUnboxEquals()

      // component model
      genMemoryAndAllocator()
      genCABIHelpers()
    }

    genPrimitiveTypeDataGlobals()

    genHelperDefinitions()
  }

  /** Generates definitions that must come *after* the code generated for regular classes.
   *
   *  This notably includes the array class definitions, since they are subtypes of the `jl.Object`
   *  struct type.
   */
  def genPostClasses()(implicit ctx: WasmContext): Unit = {
    genArrayClassTypes()

    genBoxedZeroGlobals()

    if (targetPureWasm) {
      genUndefinedAndIsUndef()
      genNaiveFmod()
      genLtoa()
      genItoa()
      genHijackedValueToString()
      // genPrintlnInt()
      // genPrintMemory()
    }
  }

  // --- Type definitions ---

  private def genPreMainRecTypeDefinitions()(implicit ctx: WasmContext): Unit = {
    val b = ctx.moduleBuilder

    def genUnderlyingArrayType(id: TypeID, elemType: StorageType): Unit =
      b.addRecType(id, OriginalName(id.toString()), ArrayType(FieldType(elemType, true)))

    genUnderlyingArrayType(genTypeID.i8Array, Int8)
    genUnderlyingArrayType(genTypeID.i16Array, Int16)
    genUnderlyingArrayType(genTypeID.i32Array, Int32)
    genUnderlyingArrayType(genTypeID.i64Array, Int64)
    genUnderlyingArrayType(genTypeID.f32Array, Float32)
    genUnderlyingArrayType(genTypeID.f64Array, Float64)
    genUnderlyingArrayType(genTypeID.anyArray, anyref)

    genUnderlyingArrayType(genTypeID.externrefArray, RefType.externref)
  }

  private def genCoreTypesInRecType()(implicit ctx: WasmContext): Unit = {
    def genCoreType(id: TypeID, compositeType: CompositeType): Unit =
      ctx.mainRecType.addSubType(id, OriginalName(id.toString()), compositeType)

    genCoreType(
      genTypeID.cloneFunctionType,
      FunctionType(
        List(RefType(genTypeID.ObjectStruct)),
        List(RefType(genTypeID.ObjectStruct))
      )
    )

    genCoreType(
      genTypeID.isJSClassInstanceFuncType,
      FunctionType(List(RefType.anyref), List(Int32))
    )

    genCoreType(
      genTypeID.typeDataArray,
      ArrayType(FieldType(RefType(genTypeID.typeData), isMutable = false))
    )

    genCoreType(
      genTypeID.reflectiveProxies,
      ArrayType(FieldType(RefType(genTypeID.reflectiveProxy), isMutable = false))
    )

    ctx.mainRecType.addSubType(
      SubType(
        genTypeID.typeData,
        OriginalName(genTypeID.typeData.toString()),
        isFinal = false,
        None,
        StructType(typeDataStructFields)
      )
    )

    genCoreType(
      genTypeID.reflectiveProxy,
      StructType(
        List(
          StructField(
            genFieldID.reflectiveProxy.methodID,
            OriginalName(genFieldID.reflectiveProxy.methodID.toString()),
            Int32,
            isMutable = false
          ),
          StructField(
            genFieldID.reflectiveProxy.funcRef,
            OriginalName(genFieldID.reflectiveProxy.funcRef.toString()),
            RefType(HeapType.Func),
            isMutable = false
          )
        )
      )
    )

    if (targetPureWasm) {
      genCoreType(
        genTypeID.wasmString,
        StructType(
          List(
            StructField(
              genFieldID.wasmString.chars,
              OriginalName(genFieldID.wasmString.chars.toString()),
              RefType(genTypeID.i16Array),
              isMutable = true
            ),
            StructField(
              genFieldID.wasmString.length,
              OriginalName(genFieldID.wasmString.length.toString()),
              Int32,
              isMutable = false
            ),
            StructField(
              genFieldID.wasmString.left,
              OriginalName(genFieldID.wasmString.left.toString()),
              RefType.nullable(genTypeID.wasmString),
              isMutable = true
            )
          )
        )
      )
    }
  }

  private def genArrayClassTypes()(implicit ctx: WasmContext): Unit = {
    // The vtable type is always the same as j.l.Object
    val vtableTypeID = genTypeID.ObjectVTable
    val vtableField = StructField(
      genFieldID.objStruct.vtable,
      OriginalName(genFieldID.objStruct.vtable.toString()),
      RefType(vtableTypeID),
      isMutable = false
    )
    val idHashCodeFieldOpt = if (targetPureWasm)
      Some(StructField(
        genFieldID.objStruct.idHashCode,
        OriginalName(genFieldID.objStruct.idHashCode.toString()),
        Int32,
        isMutable = true
      )) else None

    val typeRefsWithArrays: List[(TypeID, TypeID)] =
      List(
        (genTypeID.BooleanArray, genTypeID.i8Array),
        (genTypeID.CharArray, genTypeID.i16Array),
        (genTypeID.ByteArray, genTypeID.i8Array),
        (genTypeID.ShortArray, genTypeID.i16Array),
        (genTypeID.IntArray, genTypeID.i32Array),
        (genTypeID.LongArray, genTypeID.i64Array),
        (genTypeID.FloatArray, genTypeID.f32Array),
        (genTypeID.DoubleArray, genTypeID.f64Array),
        (genTypeID.ObjectArray, genTypeID.anyArray)
      )

    for ((structTypeID, underlyingArrayTypeID) <- typeRefsWithArrays) {
      val origName = OriginalName(structTypeID.toString())

      val underlyingArrayField = StructField(
        genFieldID.objStruct.arrayUnderlying,
        OriginalName(genFieldID.objStruct.arrayUnderlying.toString()),
        RefType(underlyingArrayTypeID),
        isMutable = false
      )

      val superType = genTypeID.ObjectStruct
      val structType = StructType(
        idHashCodeFieldOpt.fold(List(vtableField, underlyingArrayField)) { idHashCodeField =>
          List(vtableField, idHashCodeField, underlyingArrayField)
        }
      )
      val subType = SubType(structTypeID, origName, isFinal = true, Some(superType), structType)
      ctx.mainRecType.addSubType(subType)
    }
  }

  // --- Imports ---

  private def genImports()(implicit ctx: WasmContext): Unit = {
    assert(!targetPureWasm)
    if (ctx.coreSpec.wasmFeatures.exceptionHandling) genTagImports()
    genGlobalImports()
    genStringBuiltinImports()
    genHelperImports()
  }

  private def genTagImports()(implicit ctx: WasmContext): Unit = {
    val exceptionSig = FunctionType(List(RefType.externref), Nil)
    val typeID = ctx.moduleBuilder.functionTypeToTypeID(exceptionSig)
    ctx.moduleBuilder.addImport(
      Import(
        CoreHelpersModule,
        "JSTag",
        ImportDesc.Tag(
          genTagID.exception,
          OriginalName(genTagID.exception.toString()),
          typeID
        )
      )
    )
  }

  private def genGlobalImports()(implicit ctx: WasmContext): Unit = {
    def addGlobalHelperImport(id: genGlobalID.JSHelperGlobalID, tpe: Type): Unit = {
      ctx.moduleBuilder.addImport(
        Import(
          CoreHelpersModule,
          id.toString(), // import name, guaranteed by JSHelperGlobalID
          ImportDesc.Global(id, OriginalName(id.toString()), isMutable = false, tpe)
        )
      )
    }

    addGlobalHelperImport(genGlobalID.undef, RefType.any)
    addGlobalHelperImport(genGlobalID.bFalse, RefType.any)
    addGlobalHelperImport(genGlobalID.bTrue, RefType.any)
    addGlobalHelperImport(genGlobalID.idHashCodeMap, RefType.extern)
  }

  private def genStringBuiltinImports()(implicit ctx: WasmContext): Unit = {
    import RefType.{extern, externref}

    def addHelperImport(id: genFunctionID.JSHelperFunctionID,
        params: List[Type], results: List[Type]): Unit = {
      val sig = FunctionType(params, results)
      val typeID = ctx.moduleBuilder.functionTypeToTypeID(sig)
      ctx.moduleBuilder.addImport(
        Import(
          JSStringBuiltinsModule,
          id.toString(), // import name, guaranteed by JSHelperFunctionID
          ImportDesc.Func(id, OriginalName(id.toString()), typeID)
        )
      )
    }

    addHelperImport(genFunctionID.stringBuiltins.test, List(externref), List(Int32))
    addHelperImport(genFunctionID.stringBuiltins.fromCharCode, List(Int32), List(extern))
    addHelperImport(genFunctionID.stringBuiltins.fromCodePoint, List(Int32), List(extern))
    addHelperImport(genFunctionID.stringBuiltins.charCodeAt, List(externref, Int32), List(Int32))
    addHelperImport(genFunctionID.stringBuiltins.codePointAt, List(externref, Int32), List(Int32))
    addHelperImport(genFunctionID.stringBuiltins.length, List(externref), List(Int32))
    addHelperImport(genFunctionID.stringBuiltins.concat, List(externref, externref), List(extern))
    addHelperImport(genFunctionID.stringBuiltins.substring, List(externref, Int32, Int32), List(extern))
    addHelperImport(genFunctionID.stringBuiltins.equals, List(externref, externref), List(Int32))
  }

  private def genBoxUnboxEquals()(implicit ctx: WasmContext): Unit = {
    locally {
      // Generate `box` and `unbox` functions for CharType and LongType here.
      // These're used for boxing/unboxing from CanonicalABI interopration.
      val prims = List(CharType, LongType)
      for (primType <- prims) {
        val primRef = primType.primRef
        genBox(genFunctionID.box(primRef), primType)
        genUnbox(genFunctionID.unbox(primRef), primType)
      }
    }

    // integer
    genBox(genFunctionID.bIFallback, IntType)
    genUnbox(genFunctionID.uIFallback, IntType)
    genTestInteger()

    // boolean
    // box boolean should be generated elsewhere
    genUnbox(genFunctionID.unbox(BooleanRef), BooleanType)
    locally {
      val fb = newFunctionBuilder(genFunctionID.typeTest(BooleanRef))
      val xParam = fb.addParam("x", RefType.anyref)
      fb.setResultType(Int32)
      fb += LocalGet(xParam)
      fb += RefTest(RefType(genTypeID.forClass(SpecialNames.BooleanBoxClass)))
      fb.buildAndAddToModule()
    }

    locally {
      val prims = List(FloatType, DoubleType)
      for (primType <- prims) {
        val primRef = primType.primRef
        val wasmType = transformPrimType(primType)
        genBox(genFunctionID.box(primRef), primType)
      }
      genUnboxFloat()
      genUnboxDouble()

      genTestFloat()
      genTestDouble()
    }

    locally {
      // Handle IntegerBoxClass, FloatBoxClass, and i16array (string), otherwise ref.eq
      // Double, Long, Char should be handled by BoxedRuntime
      // Boolean, Byte, Short (and 31bit int) should be i31ref and handled by ref.eq.
      val fb = newFunctionBuilder(genFunctionID.is)
      val aParam = fb.addParam("a", anyref)
      val bParam = fb.addParam("b", anyref)
      fb.setResultType(Int32)

      val a = fb.addLocal("a", RefType.any)
      val b = fb.addLocal("b", RefType.any)
      val doubleA = fb.addLocal("doubleA", Float64)
      val doubleB = fb.addLocal("doubleB", Float64)
      val valueType = fb.addLocal("valueType", Int32)

      def genRefTestBoth(ref: RefType): Unit = {
        fb += LocalGet(a)
        fb += RefTest(ref)
        fb += LocalGet(b)
        fb += RefTest(ref)
        fb += I32And
      }

      // aParam == bParam == null
      fb += LocalGet(aParam)
      fb += RefIsNull
      fb += LocalGet(bParam)
      fb += RefIsNull
      fb += I32And
      fb.ifThen() {
        fb += I32Const(1)
        fb += Return
      }

      // aParam or bParam is null, another one is not
      fb += LocalGet(aParam)
      fb += RefIsNull
      fb += LocalGet(bParam)
      fb += RefIsNull
      fb += I32Or
      fb.ifThen() {
        fb += I32Const(0)
        fb += Return
      }

      fb += LocalGet(aParam)
      fb += RefAsNonNull
      fb += LocalSet(a)
      fb += LocalGet(bParam)
      fb += RefAsNonNull
      fb += LocalSet(b)

      fb += LocalGet(a)
      fb += Call(genFunctionID.scalaValueType)
      fb += LocalGet(b)
      fb += Call(genFunctionID.scalaValueType)
      fb += LocalTee(valueType)
      fb += I32Eq
      fb.ifThenElse() {
        fb.switch() { () =>
          fb += LocalGet(valueType)
        }(
          List(JSValueTypeFalse, JSValueTypeTrue) -> { () =>
            fb += LocalGet(a)
            fb += Call(genFunctionID.unbox(BooleanRef))
            fb += LocalGet(b)
            fb += Call(genFunctionID.unbox(BooleanRef))
            fb += I32Eq
            fb += Return
          },
          List(JSValueTypeString) -> { () =>
            fb += LocalGet(a)
            fb += RefCast(RefType(genTypeID.wasmString))
            fb += LocalGet(b)
            fb += RefCast(RefType(genTypeID.wasmString))
            fb += Call(genFunctionID.wasmString.stringEquals)
            fb += Return
          },
          // case JSValueTypeNumber => ...
          List(JSValueTypeNumber) -> { () =>
            fb += LocalGet(a)
            fb += Call(genFunctionID.unbox(DoubleRef))
            fb += LocalTee(doubleA)
            fb += LocalGet(b)
            fb += Call(genFunctionID.unbox(DoubleRef))
            fb += LocalTee(doubleB)
            fb += F64Eq
            fb.ifThenElse(Int32) {
              // Object.is(+0.0, -0.0) -> false
              fb += LocalGet(doubleA)
              fb += I64ReinterpretF64
              fb += LocalGet(doubleB)
              fb += I64ReinterpretF64
              fb += I64Eq
            } {
              // both of a and b are NaN -> true
              // because JS Object.is(NaN, NaN) -> true
              fb += LocalGet(doubleA)
              fb += LocalGet(doubleA)
              fb += F64Ne
              fb += LocalGet(doubleB)
              fb += LocalGet(doubleB)
              fb += F64Ne
              fb += I32And
            }
            fb += Return
          },
        ) { () =>
          genRefTestBoth(RefType.eqref)
          fb.ifThenElse(Int32) {
            fb += LocalGet(a)
            fb += RefCast(RefType.eqref)
            fb += LocalGet(b)
            fb += RefCast(RefType.eqref)
            fb += RefEq
          } {
            fb += I32Const(0)
          }
          fb += Return
        }
      } {
        fb += I32Const(0)
        fb += Return
      }
      fb += Unreachable
      fb.buildAndAddToModule()
    }
  }

  private def genHelperImports()(implicit ctx: WasmContext): Unit = {
    def addHelperImport(id: genFunctionID.JSHelperFunctionID,
        params: List[Type], results: List[Type]): Unit = {
      val sig = FunctionType(params, results)
      val typeID = ctx.moduleBuilder.functionTypeToTypeID(sig)
      ctx.moduleBuilder.addImport(
        Import(
          CoreHelpersModule,
          id.toString(), // import name, guaranteed by JSHelperFunctionID
          ImportDesc.Func(id, OriginalName(id.toString()), typeID)
        )
      )
    }

    addHelperImport(genFunctionID.is, List(anyref, anyref), List(Int32))

    addHelperImport(genFunctionID.isUndef, List(anyref), List(Int32))

    for (primType <- List(BooleanType, FloatType, DoubleType)) {
      val primRef = primType.primRef
      val wasmType = transformPrimType(primType)

      if (primType != BooleanType)
        addHelperImport(genFunctionID.box(primRef), List(wasmType), List(RefType.any))
      addHelperImport(genFunctionID.unbox(primRef), List(anyref), List(wasmType))
      addHelperImport(genFunctionID.typeTest(primRef), List(anyref), List(Int32))
    }

    addHelperImport(genFunctionID.bIFallback, List(Int32), List(RefType.any))
    addHelperImport(genFunctionID.uIFallback, List(anyref), List(Int32))
    addHelperImport(genFunctionID.typeTest(IntRef), List(anyref), List(Int32))

    addHelperImport(genFunctionID.jsValueToString, List(RefType.any), List(RefType.extern))
    addHelperImport(genFunctionID.jsValueToStringForConcat, List(anyref), List(RefType.extern))
    addHelperImport(genFunctionID.booleanToString, List(Int32), List(RefType.extern))
    addHelperImport(genFunctionID.intToString, List(Int32), List(RefType.extern))
    addHelperImport(genFunctionID.longToString, List(Int64), List(RefType.extern))
    addHelperImport(genFunctionID.doubleToString, List(Float64), List(RefType.extern))

    addHelperImport(genFunctionID.jsValueType, List(RefType.any), List(Int32))
    addHelperImport(genFunctionID.jsValueDescription, List(anyref), List(RefType.extern))
    addHelperImport(genFunctionID.bigintHashCode, List(RefType.any), List(Int32))
    addHelperImport(
      genFunctionID.symbolDescription,
      List(RefType.any),
      List(RefType.externref)
    )
    addHelperImport(
      genFunctionID.idHashCodeGet,
      List(RefType.extern, RefType.any),
      List(Int32)
    )
    addHelperImport(
      genFunctionID.idHashCodeSet,
      List(RefType.extern, RefType.any, Int32),
      Nil
    )

    addHelperImport(genFunctionID.makeTypeError, List(RefType.extern), List(RefType.extern))

    addHelperImport(genFunctionID.jsNewArray, Nil, List(RefType.any))
    addHelperImport(genFunctionID.jsNewObject, Nil, List(RefType.any))
    addHelperImport(genFunctionID.jsSelect, List(anyref, anyref), List(anyref))
    addHelperImport(genFunctionID.jsSelectSet, List(anyref, anyref, anyref), Nil)
    addHelperImport(genFunctionID.jsNewNoArg, List(anyref), List(anyref))
    addHelperImport(genFunctionID.jsImportCall, List(anyref), List(anyref))
    addHelperImport(genFunctionID.jsImportMeta, Nil, List(anyref))
    addHelperImport(genFunctionID.jsAwait, List(anyref), List(anyref))
    addHelperImport(genFunctionID.jsDelete, List(anyref, anyref), Nil)
    addHelperImport(genFunctionID.jsForInStart, List(anyref), List(anyref))
    addHelperImport(genFunctionID.jsForInNext, List(anyref), List(anyref, Int32))
    addHelperImport(genFunctionID.jsIsTruthy, List(anyref), List(Int32))

    addHelperImport(genFunctionID.newSymbol, Nil, List(anyref))
    addHelperImport(
      genFunctionID.jsSuperSelect,
      List(anyref, anyref, anyref),
      List(anyref)
    )
    addHelperImport(
      genFunctionID.jsSuperSelectSet,
      List(anyref, anyref, anyref, anyref),
      Nil
    )
  }

  private def genWasmEssentialsImports()(implicit ctx: WasmContext): Unit = {
    def addEssentialImport(id: genFunctionID.JSHelperFunctionID,
        params: List[Type], results: List[Type]): Unit = {
      val sig = FunctionType(params, results)
      val typeID = ctx.moduleBuilder.functionTypeToTypeID(sig)
      ctx.moduleBuilder.addImport(
        Import(
          EssentialExternsModule,
          id.toString(), // import name, guaranteed by JSHelperFunctionID
          ImportDesc.Func(id, OriginalName(id.toString()), typeID)
        )
      )
    }

    addEssentialImport(genFunctionID.wasmEssentials.print, List(RefType(genTypeID.i16Array)), Nil)
    addEssentialImport(genFunctionID.wasmEssentials.nanoTime, Nil, List(Float64))
    addEssentialImport(genFunctionID.wasmEssentials.currentTimeMillis, Nil, List(Float64))
    addEssentialImport(genFunctionID.wasmEssentials.random, Nil, List(Float64))

    addEssentialImport(genFunctionID.wasmEssentials.scalajsCom.send, List(RefType(genTypeID.i16Array)), Nil)
    addEssentialImport(genFunctionID.wasmEssentials.scalajsCom.init, List(RefType.func), Nil)
  }

  // --- Global definitions ---


  private def genPrintMemory()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.dumpMemory)
    val offset = fb.addParam("offset", Int32)
    val byteLength = fb.addParam("byte_length", Int32)

    val iLocal = fb.addLocal("i", Int32)
    val end = fb.addLocal("end", Int32)

    fb += LocalGet(offset)
    fb += LocalGet(byteLength)
    fb += I32Add
    fb += LocalSet(end)

    fb += LocalGet(offset)
    fb += LocalSet(iLocal)

    fb.block() { exit =>
      fb.loop() { loop =>
        fb += LocalGet(iLocal)
        fb += LocalGet(end)
        fb += I32GeU
        fb += BrIf(exit)

        fb += LocalGet(iLocal)
        fb += I32Load8U()

        // print
        fb += Call(genFunctionID.printlnInt)

        fb += LocalGet(iLocal)
        fb += I32Const(1)
        fb += I32Add
        fb += LocalSet(iLocal)

        fb += Br(loop)
      }
    }

    fb += I32Const(999999)
    fb += Call(genFunctionID.printlnInt)

    fb.buildAndAddToModule()
  }

  private def genPrintlnInt()(implicit ctx: WasmContext): Unit = {
    // (type (;1;) (func (result i32)))
    // (import "wasi:cli/stdout@0.2.2" "get-stdout" (func (;1;) (type 1)))
    ctx.moduleBuilder.addImport(
      Import(
        "wasi:cli/stdout@0.2.0",
        "get-stdout",
        ImportDesc.Func(
          genFunctionID.wasiCliGetStdout,
          OriginalName(genFunctionID.wasiCliGetStdout.toString()),
          ctx.moduleBuilder.functionTypeToTypeID(
            FunctionType(Nil, List(Int32)))
        )
      )
    )

    // (type (;0;) (func (param i32 i32 i32 i32)))
    // (import "wasi:io/streams" "[method]output-stream.blocking-write-and-flush" (func (;0;) (type 0)))
    ctx.moduleBuilder.addImport(
      Import(
        "wasi:io/streams@0.2.0",
        "[method]output-stream.blocking-write-and-flush",
        ImportDesc.Func(
          genFunctionID.blockingWriteAndFlush,
          OriginalName(genFunctionID.blockingWriteAndFlush.toString()),
          ctx.moduleBuilder.functionTypeToTypeID(
            FunctionType(List(Int32, Int32, Int32, Int32), Nil))
        )
      )
    )

    // (import "wasi:io/streams@0.2.2" "[resource-drop]output-stream" ... ))
    ctx.moduleBuilder.addImport(
      Import(
        "wasi:io/streams@0.2.0",
        "[resource-drop]output-stream",
        ImportDesc.Func(
          genFunctionID.dropOutputStream,
          OriginalName(genFunctionID.dropOutputStream.toString()),
          ctx.moduleBuilder.functionTypeToTypeID(
            FunctionType(List(Int32), Nil))
        )
      )
    )


    val fb = newFunctionBuilder(genFunctionID.printlnInt)
    val num = fb.addParam("num", Int32)

    val tmp = fb.addLocal("tmp", Int32)
    val negative = fb.addLocal("negative", Int32)
    val length = fb.addLocal("length", Int32)
    val ptr = fb.addLocal("ptr", Int32)
    val idx = fb.addLocal("idx", Int32)
    val retAddr = fb.addLocal("ret_addr", Int32)

    val sp = fb.addLocal("sp", Int32)

    fb += GlobalGet(genGlobalID.stackPointer)
    fb += LocalSet(sp)

    fb += LocalGet(num)
    fb += I32Eqz
    fb.ifThenElse() {
      fb += I32Const(2)
      fb += LocalTee(length)
      fb += Call(genFunctionID.malloc)
      fb += LocalTee(ptr)
      fb += I32Const(48) // '0'
      fb += I32Store8()

      fb += LocalGet(ptr)
      fb += I32Const(1)
      fb += I32Add
      fb += I32Const(10) // \n
      fb += I32Store8()
    } {
      fb += LocalGet(num)
      fb += I32Const(0)
      fb += I32LtS
      fb.ifThenElse(Int32) {
        fb += LocalGet(num)
        fb += I32Const(-1)
        fb += I32Mul
        fb += LocalSet(num)

        fb += I32Const(1)
        fb += LocalSet(negative)
        fb += I32Const(2) // for '-' and '\n'
      } {
        fb += I32Const(0)
        fb += LocalSet(negative)
        fb += I32Const(1) // for '\n'
      }
      fb += LocalSet(length)

      fb += LocalGet(num)
      fb += LocalSet(tmp)
      fb.loop() { loop =>
        fb += LocalGet(tmp)
        fb += I32Eqz
        fb.ifThenElse() { // break
        } {
          fb += LocalGet(tmp)
          fb += I32Const(10)
          fb += I32DivS
          fb += LocalSet(tmp)

          fb += LocalGet(length)
          fb += I32Const(1)
          fb += I32Add
          fb += LocalSet(length)
          fb += Br(loop)
        }
      }

      fb += LocalGet(length)
      fb += Call(genFunctionID.malloc)
      fb += LocalSet(ptr)

      // fill
      fb += LocalGet(num)
      fb += LocalSet(tmp)

      // append '\n'
      fb += LocalGet(ptr)
      fb += LocalGet(length)
      fb += I32Const(1)
      fb += I32Sub
      fb += I32Add
      fb += I32Const(10)
      fb += I32Store8()

      fb += LocalGet(length)
      fb += I32Const(2) // skip the last for '\n'
      fb += I32Sub
      fb += LocalSet(idx)

      fb.loop() { loop =>
        fb += LocalGet(tmp)
        fb += I32Eqz
        fb.ifThenElse() { // break
        } {
          fb += LocalGet(ptr)
          fb += LocalGet(idx)
          fb += I32Add // ptr to store

          fb += LocalGet(tmp)
          fb += I32Const(10)
          fb += I32RemS
          fb += I32Const(48) // '0'
          fb += I32Add
          fb += I32Store8()

          fb += LocalGet(idx)
          fb += I32Const(1)
          fb += I32Sub
          fb += LocalSet(idx)

          fb += LocalGet(tmp)
          fb += I32Const(10)
          fb += I32DivS
          fb += LocalSet(tmp)

          fb += Br(loop)
        }
      }

      fb += LocalGet(negative)
      fb.ifThen() {
        fb += LocalGet(ptr)
        fb += I32Const(45) // '-'
        fb += I32Store8()
      }
    }


    val out = fb.addLocal("out", Int32)
    // now, $ptr has memory offset, and $length has array length
    // get stdout
    fb += Call(genFunctionID.wasiCliGetStdout)
    fb += LocalTee(out)
    fb += LocalGet(ptr)
    fb += LocalGet(length)
    fb += I32Const(12) // result<_, stream-error>
    fb += Call(genFunctionID.malloc)
    fb += LocalTee(retAddr)

    fb += Call(genFunctionID.blockingWriteAndFlush)

    // cleanup
    fb += LocalGet(sp)
    fb += GlobalSet(genGlobalID.stackPointer)

    // drop outputstream
    fb += LocalGet(out)
    fb += Call(genFunctionID.dropOutputStream)

    fb.buildAndAddToModule()
  }

  private def genPrimitiveTypeDataGlobals()(implicit ctx: WasmContext): Unit = {
    import genFieldID.typeData._

    val typeDataTypeID = genTypeID.typeData

    // Other than `name` and `kind`, all the fields have the same value for all primitives
    val commonFieldValues = List(
      // specialInstanceTypes
      I32Const(0),
      // strictAncestors
      RefNull(HeapType.None),
      // componentType
      RefNull(HeapType.None),
      // the classOf instance - initially `null`; filled in by the `createClassOf` helper
      RefNull(HeapType.None),
      // arrayOf, the typeData of an array of this type - initially `null`; filled in by the `arrayTypeData` helper
      RefNull(HeapType.None),
      // cloneFunction
      RefNull(HeapType.NoFunc),
      // isJSClassInstance
      RefNull(HeapType.NoFunc),
      // reflectiveProxies
      ArrayNewFixed(genTypeID.reflectiveProxies, 0)
    )

    for ((primRef, kind) <- primRefsWithKinds) {
      val nameValue =
        if (targetPureWasm)
          ctx.stringPool.getConstantStringDataInstr(primRef.displayName) :+
              RefNull(HeapType(genTypeID.wasmString))
        else ctx.stringPool.getConstantStringDataInstr(primRef.displayName)

      val instrs: List[Instr] = {
        nameValue ::: I32Const(kind) :: commonFieldValues :::
          StructNew(genTypeID.typeData) :: Nil
      }

      ctx.addGlobal(
        Global(
          genGlobalID.forVTable(primRef),
          OriginalName("d." + primRef.charCode),
          isMutable = false,
          RefType(genTypeID.typeData),
          Expr(instrs)
        )
      )
    }
  }

  private def genBoxedZeroGlobals()(implicit ctx: WasmContext): Unit = {
    val primTypesWithBoxClasses: List[(GlobalID, ClassName, Instr)] = List(
      (genGlobalID.bZeroChar, SpecialNames.CharBoxClass, I32Const(0)),
      (genGlobalID.bZeroLong, SpecialNames.LongBoxClass, I64Const(0))
    ) ++ (if (targetPureWasm) List(
      (genGlobalID.bZeroInteger, SpecialNames.IntegerBoxClass, I32Const(0)),
      (genGlobalID.bZeroFloat, SpecialNames.FloatBoxClass, F32Const(0)),
      (genGlobalID.bZeroDouble, SpecialNames.DoubleBoxClass, F64Const(0))
    ) else Nil)

    for ((globalID, boxClassName, zeroValueInstr) <- primTypesWithBoxClasses) {
      val boxStruct = genTypeID.forClass(boxClassName)
      val instrs: List[Instr] = List(
        GlobalGet(genGlobalID.forVTable(boxClassName)),
      ) :::
        (if (targetPureWasm) List(I32Const(0)) else Nil) :::
        List(
          zeroValueInstr,
          StructNew(boxStruct)
        )

      ctx.addGlobal(
        Global(
          globalID,
          OriginalName(globalID.toString()),
          isMutable = false,
          RefType(boxStruct),
          Expr(instrs)
        )
      )
    }
  }

  // --- Function definitions ---

  /** Generates all the helper function definitions of the core Wasm lib. */
  private def genHelperDefinitions()(implicit ctx: WasmContext): Unit = {
    genBoxBoolean()
    genBoxInt()
    genUnboxInt()
    genUnboxByteOrShort(ByteRef)
    genUnboxByteOrShort(ShortRef)
    genTestByteOrShort(ByteRef, I32Extend8S)
    genTestByteOrShort(ShortRef, I32Extend16S)
    if (targetPureWasm) genStringLiteral()
    genTypeDataName()
    genCreateClassOf()
    genGetClassOf()
    genArrayTypeData()

    if (semantics.asInstanceOfs != CheckedBehavior.Unchecked ||
        semantics.arrayStores != CheckedBehavior.Unchecked) {
      genValueDescription()
    }

    if (semantics.asInstanceOfs != CheckedBehavior.Unchecked) {
      genClassCastException()
      genPrimitiveAsInstances()
      genArrayAsInstances()
    }

    if (semantics.arrayStores != CheckedBehavior.Unchecked)
      genThrowArrayStoreException()

    if (semantics.arrayIndexOutOfBounds != CheckedBehavior.Unchecked) {
      genThrowArrayIndexOutOfBoundsException()
      genArrayGets()
      genArraySets()
    } else if (semantics.arrayStores != CheckedBehavior.Unchecked) {
      genArraySet(ClassRef(ObjectClass))
    }

    if (semantics.negativeArraySizes != CheckedBehavior.Unchecked) {
      genThrowNegativeArraySizeException()
    }

    if (semantics.stringIndexOutOfBounds != CheckedBehavior.Unchecked) {
      genCheckedStringCharAtOrCodePointAt(
        genFunctionID.checkedStringCharAt,
        if (targetPureWasm) {
          genFunctionID.wasmString.charCodeAt
        } else {
          genFunctionID.stringBuiltins.charCodeAt
        }
      )
      if (!targetPureWasm) { // In WASI, Optimizer won't transform substring method
        genCheckedStringCharAtOrCodePointAt(
            genFunctionID.checkedStringCodePointAt, genFunctionID.stringBuiltins.codePointAt)
        genCheckedSubstringStart()
        genCheckedSubstringStartEnd()
      }
    }

    if (semantics.nullPointers != CheckedBehavior.Unchecked) {
      genThrowNullPointerException()
    }

    if (semantics.moduleInit == CheckedBehavior.Fatal) {
      genThrowModuleInitError()
    }

    genIsInstance()
    genIsAssignableFrom()
    if (semantics.asInstanceOfs != CheckedBehavior.Unchecked)
      genCast()
    genGetComponentType()
    if (globalInfo.isClassSuperClassUsed)
      genGetSuperClass()
    genNewArray()
    genAnyGetClass()
    genAnyGetClassName()
    genAnyGetTypeData()
    genIdentityHashCode()
    genSearchReflectiveProxy()
    genArrayCloneFunctions()
    genArrayCopyFunctions()

    // WASI
    if (targetPureWasm) {
      genStringConcat()
      genStringEquals()
      genGetWholeChars()
      genCollapseString()
      genCharCodeAt()
      ctx.addGlobal(
        Global(
          genGlobalID.emptyStringArray,
          NoOriginalName,
          isMutable = false,
          RefType(genTypeID.wasmString),
          Expr(List(
            ArrayNewFixed(genTypeID.i16Array, 0),
            I32Const(0),
            RefNull(HeapType(genTypeID.wasmString)),
            StructNew(genTypeID.wasmString)
          ))
        )
      )
    }
  }

 /** Memory allocation using a global stack pointer.
   *
   * Instead of using dynamic memory allocation, memory management can be performed using a global
   * stack pointer. The free memory is tracked by a global variable `stackPointer`.
   * `malloc` simply increments `stackPointer` by `nbytes` (+ 8-byte alignment).
   * When the allocated memory is no longer needed, `stackPointer` is restored to its previous value.
   *
   * This memory management model is simple and efficient.
   * Given that memory allocation mainly occurs when interacting with external components in the Component Model,
   *
   * When calling an external function, `stackPointer` is saved before the call and restored
   * afterward.
   * However, issues arise when a function exported via the Component Model is called.
   *
   * ## Problem with stack pointer based memory management
   * When the VM passes arguments to an exported function, it may require additional memory (e.g.,
   * for strings, lists, or when the argument count exceeds 16 in core Wasm).
   * In such cases, the VM uses the exported `cabi_realloc` function to allocate memory and passes the memory offset as
   * an argument. These allocations occur before the function call itself.
   * In that scenario, it's difficult to determine which address to restore the stack pointer,
   * since we cannot save the stack pointer at callee site.
   *
   * ## Possible workaround for the problem
   * Ideally, the caller should save the stack pointer, but since the caller is the VM, the language
   * cannot enforce such an operation to VM.
   *
   * An alternative approach is to save the stack pointer within `cabi_realloc`.
   * However, determining which address to restore the stack pointer is problematic because `realloc`
   * calls are not directly linked to a specific function invocation.
   * It is not sufficient to remember only the most recent value, because multiple `realloc` calls
   * may occur before the function execution.
   *
   * ## Our workaround
   * Our workaround is that, find a minimum pointer value to restore from the memory offset
   * values given as arguments of the exported function.
   * For example, the memory offset of string might be the offset to restore after function call.
   *
   * A challenge with this approach is that when storing a string, `realloc` may allocate a certain
   * memory size initially and later modify it again with another `realloc` call.
   * However, for UTF-16 system like Scala, the Canonical ABI specification states that `realloc` does not
   * increase memory size with `realloc` when storing a string (just shrink it).
   * At least of the current spec: https://github.com/WebAssembly/component-model/commit/2489e3d614a0f6f95089e8d1a71ddc0708b68851
   *
   * If our `realloc` implementation does not copy existing memory segments when shrinking,
   * then the memory offsets passed as arguments will always include the correct memory address to restore.
   *
   * By resetting the stack pointer to the minimum of these memory addresses in `post-return`,
   * it should be possible to free memory allocated by the VM when calling an externally exported
   * function.
   */
  private def genMemoryAndAllocator()(implicit ctx: WasmContext): Unit = {
    ctx.moduleBuilder.addMemory(
      Memory(genMemoryID.memory, OriginalName.NoOriginalName, Memory.Limits(1, None))
    )
    // > all modules accessing WASI APIs also export a linear memory with the name `memory`.
    // > Data pointers in WASI API calls are relative to this memory's index space.
    // https://github.com/WebAssembly/WASI/blob/main/legacy/application-abi.md
    ctx.moduleBuilder.addExport(Export("memory", ExportDesc.Memory(genMemoryID.memory)))

    ctx.addGlobal(
      Global(
        genGlobalID.stackPointer,
        OriginalName(genGlobalID.stackPointer.toString()),
        isMutable = true,
        Int32,
        Expr(List(I32Const(0)))
      )
    )

    ctx.addGlobal(
      Global(
        genGlobalID.savedStackPointer,
        OriginalName(genGlobalID.savedStackPointer.toString()),
        isMutable = true,
        Int32,
        Expr(List(I32Const(0)))
      )
    )

    locally {
      val fb = newFunctionBuilder(genFunctionID.malloc)
      val nbytes = fb.addParam("nbytes", Int32)
      fb.setResultType(Int32)

      val alignment = 8 // max alignment?

      val base = fb.addLocal("base", Int32)

      fb += GlobalGet(genGlobalID.stackPointer)

      // align
      // ((base + alignment - 1) / alignment) * alignment
      fb += I32Const(alignment)
      fb += I32Add
      fb += I32Const(1)
      fb += I32Sub
      fb += I32Const(alignment)
      fb += I32DivU
      fb += I32Const(alignment)
      fb += I32Mul
      fb += LocalTee(base)

      fb += LocalGet(nbytes) // newPtr = stackPointer + nbytes
      fb += I32Add

      fb += GlobalSet(genGlobalID.stackPointer)

      fb += LocalGet(base)

      fb.buildAndAddToModule()
    }

    genRealloc()
    ctx.moduleBuilder.addExport(
      Export(
        "cabi_realloc",
        ExportDesc.Func(genFunctionID.realloc)
      )
    )
  }

  // TODO: generate only if they are needed? maybe we don't need to care
  //   because wasm-opt should dce them.
  private def genCABIHelpers()(implicit ctx: WasmContext): Unit = {
    {
      val fb = newFunctionBuilder(genFunctionID.cabiLoadString)
      val offset = fb.addParam("offset", Int32)
      val units = fb.addParam("units", Int32)
      fb.setResultType(RefType(genTypeID.wasmString))

      val array = fb.addLocal("array", RefType(genTypeID.i16Array))
      val i = fb.addLocal("i", Int32)

      fb += LocalGet(units)
      fb += ArrayNewDefault(genTypeID.i16Array)
      fb += LocalSet(array)

      fb += I32Const(0)
      fb += LocalSet(i)

      fb.block() { exit =>
        fb.loop() { loop =>
          // if i >= units, break
          fb += LocalGet(i)
          fb += LocalGet(units)
          fb += I32GeU
          fb += BrIf(exit)

          // for array.set
          fb += LocalGet(array)
          fb += LocalGet(i)

          // load i16 value
          fb += LocalGet(offset)
          fb += LocalGet(i)
          fb += I32Const(2)
          fb += I32Mul
          fb += I32Add
          fb += I32Load16U()

          fb += ArraySet(genTypeID.i16Array)

          fb += LocalGet(i)
          fb += I32Const(1)
          fb += I32Add
          fb += LocalSet(i)

          fb += Br(loop)
        }
      }

      SWasmGen.genWasmStringFromArray(fb, array)
      fb.buildAndAddToModule()
    }

    {
      val fb = newFunctionBuilder(genFunctionID.cabiStoreString)
      val str = fb.addParam("str", RefType.nullable(genTypeID.wasmString))
      fb.setResultTypes(List(Int32, Int32)) // baseAddr, units

      val chars = fb.addLocal("chars", RefType(genTypeID.i16Array))
      val baseAddr = fb.addLocal("baseAddr", Int32)
      val iLocal = fb.addLocal("i", Int32)

      // TODO: npe if null

      // required bytes
      fb += LocalGet(str)
      fb += StructGet(genTypeID.wasmString, genFieldID.wasmString.length)
      fb += I32Const(2)
      fb += I32Mul
      fb += Call(genFunctionID.malloc)
      fb += LocalSet(baseAddr)

      fb += LocalGet(str)
      fb += RefAsNonNull
      fb += Call(genFunctionID.wasmString.getWholeChars)
      fb += LocalSet(chars)

      // i := 0
      fb += I32Const(0)
      fb += LocalSet(iLocal)

      fb.block() { exit =>
        fb.loop() { loop =>
          fb += LocalGet(iLocal)
          fb += LocalGet(str)
          fb += StructGet(genTypeID.wasmString, genFieldID.wasmString.length)
          fb += I32Eq
          fb += BrIf(exit)

          // store
          // position (baseAddr + i * 2)
          fb += LocalGet(baseAddr)
          fb += LocalGet(iLocal)
          fb += I32Const(2)
          fb += I32Mul
          fb += I32Add

          // value
          fb += LocalGet(chars)
          fb += LocalGet(iLocal)
          fb += ArrayGetU(genTypeID.i16Array) // i32 here
          fb += I32Store16() // store 2 bytes

          // i := i + 1
          fb += LocalGet(iLocal)
          fb += I32Const(1)
          fb += I32Add
          fb += LocalSet(iLocal)
          fb += Br(loop)
        }
      }
      fb += LocalGet(baseAddr) // offset
      fb += LocalGet(str)
      fb += StructGet(genTypeID.wasmString, genFieldID.wasmString.length)

      fb.buildAndAddToModule()
    }
  }

  private def newFunctionBuilder(functionID: FunctionID, originalName: OriginalName)(
      implicit ctx: WasmContext): FunctionBuilder = {
    new FunctionBuilder(ctx.moduleBuilder, functionID, originalName, noPos)
  }

  private def newFunctionBuilder(functionID: FunctionID)(
      implicit ctx: WasmContext): FunctionBuilder = {
    newFunctionBuilder(functionID, OriginalName(functionID.toString()))
  }

  private def genUnboxBoolean()(implicit ctx: WasmContext): Unit = {
    assert(true /* isWASI */) // scalastyle:ignore
    val fb = newFunctionBuilder(genFunctionID.unbox(BooleanRef))
    val xParam = fb.addParam("x", RefType.i31)
    fb.setResultType(Int32)

    fb += LocalGet(xParam)
    fb += I31GetS
    fb.buildAndAddToModule()
  }

  private def genBoxBoolean()(implicit ctx: WasmContext): Unit = {
    if (targetPureWasm) {
      genBox(genFunctionID.box(BooleanRef), BooleanType)
    } else {
      val fb = newFunctionBuilder(genFunctionID.box(BooleanRef))
      val xParam = fb.addParam("x", Int32)
      fb.setResultType(RefType.any)

      fb += GlobalGet(genGlobalID.bTrue)
      fb += GlobalGet(genGlobalID.bFalse)
      fb += LocalGet(xParam)
      fb += Select(List(RefType.any))

      fb.buildAndAddToModule()
    }
  }

  private def genBoxInt()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.box(IntRef))
    val xParam = fb.addParam("x", Int32)
    fb.setResultType(RefType.any)

    /* Test if the two most significant bits are different: (x ^ (x << 1)) & 0x80000000
     * If they are, we cannot box as i31 since sign extension on unbox will
     * duplicate the second most significant bit (and JS would see the wrong
     * number value as well).
     */
    fb += LocalGet(xParam)
    fb += LocalGet(xParam)
    fb += I32Const(1)
    fb += I32Shl
    fb += I32Xor
    fb += I32Const(0x80000000)
    fb += I32And

    // If non-zero,
    fb.ifThenElse(RefType.any) {
      // then call the fallback JS helper
      fb += LocalGet(xParam)
      fb += Call(genFunctionID.bIFallback)
    } {
      // else use ref.i31
      fb += LocalGet(xParam)
      fb += RefI31
    }

    fb.buildAndAddToModule()
  }

  private def genUnboxInt()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.unbox(IntRef))
    val xParam = fb.addParam("x", RefType.anyref)
    fb.setResultType(Int32)

    // If x is a (ref i31), extract it
    fb.block(RefType.anyref) { xIsNotI31Label =>
      fb += LocalGet(xParam)
      fb += BrOnCastFail(xIsNotI31Label, RefType.anyref, RefType.i31)
      fb += I31GetS
      fb += Return
    }

    // Otherwise, use the fallback helper
    fb += Call(genFunctionID.uIFallback)

    fb.buildAndAddToModule()
  }

  private def genTypeTest(functionID: FunctionID, targetTpe: PrimType)(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm)

    val fb = newFunctionBuilder(functionID)
    val xParam = fb.addParam("x", RefType.anyref)
    fb.setResultType(Int32)

    val boxClass = targetTpe match {
      case BooleanType => SpecialNames.BooleanBoxClass
      case _ => throw new AssertionError(s"Invalid type: $targetTpe")
    }
    fb += LocalGet(xParam)
    fb += RefTest(RefType(genTypeID.forClass(boxClass)))
    fb.buildAndAddToModule()
  }

  private def genBox(functionID: FunctionID, targetTpe: PrimType)(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm)

    val wasmType = transformPrimType(targetTpe)
    val fb = newFunctionBuilder(functionID)
    val xParam = fb.addParam("x", wasmType)
    fb.setResultType(RefType.any)

    val boxClass = targetTpe match {
      case IntType => SpecialNames.IntegerBoxClass
      case FloatType => SpecialNames.FloatBoxClass
      case DoubleType => SpecialNames.DoubleBoxClass
      case CharType => SpecialNames.CharBoxClass
      case LongType => SpecialNames.LongBoxClass
      case BooleanType => SpecialNames.BooleanBoxClass
      case _ => throw new AssertionError(s"Invalid targetTpe: $targetTpe")
    }

    fb += GlobalGet(genGlobalID.forVTable(boxClass))
    if (targetPureWasm) fb += I32Const(0)
    fb += LocalGet(xParam)
    fb += StructNew(genTypeID.forClass(boxClass))
    fb.buildAndAddToModule()
  }

  private def genUnbox(functionID: FunctionID, targetTpe: PrimType)(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm)
    val fb = newFunctionBuilder(functionID)
    val xParam = fb.addParam("x", RefType.anyref)
    val resultType = transformPrimType(targetTpe)
    fb.setResultType(resultType)

    val boxClass = targetTpe match {
      case IntType => SpecialNames.IntegerBoxClass
      case FloatType => SpecialNames.FloatBoxClass
      case DoubleType => SpecialNames.DoubleBoxClass
      case CharType => SpecialNames.CharBoxClass
      case LongType => SpecialNames.LongBoxClass
      case BooleanType => SpecialNames.BooleanBoxClass
      case _ => throw new AssertionError(s"Invalid targetTpe: $targetTpe")
    }
    val fieldName = FieldName(boxClass, SpecialNames.valueFieldSimpleName)

    fb += LocalGet(xParam)
    fb.block(FunctionType(List(RefType.anyref), List(resultType))) { doneLabel =>
      fb.block(FunctionType(List(RefType.anyref), Nil)) { isNullLabel =>
        fb += BrOnNull(isNullLabel)
        val structTypeID = genTypeID.forClass(boxClass)
        fb += RefCast(RefType(structTypeID))
        fb += StructGet(
          structTypeID,
          genFieldID.forClassInstanceField(fieldName)
        )
        fb += Br(doneLabel)
      }
      fb += genZeroOf(targetTpe)
    }

    fb.buildAndAddToModule()
  }

  private def genUnboxFloat()(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm)
    val fb = newFunctionBuilder(genFunctionID.unbox(FloatRef))
    val xParam = fb.addParam("x", RefType.anyref)
    val resultType = transformPrimType(FloatType)
    fb.setResultType(resultType)

    fb += LocalGet(xParam)
    fb.block(FunctionType(List(RefType.anyref), List(resultType))) { doneLabel =>
      fb.block(FunctionType(List(RefType.anyref), Nil)) { isNullLabel =>
        fb.block(FunctionType(List(RefType.anyref), List(RefType.i31))) { isI31Label =>
          fb.block(FunctionType(List(RefType.anyref), List(RefType(genTypeID.forClass(SpecialNames.FloatBoxClass))))) { isFloatLabel =>
            fb += BrOnNull(isNullLabel)
            fb += BrOnCast(isI31Label, RefType.anyref, RefType.i31)
            fb += BrOnCast(isFloatLabel, RefType.anyref, RefType(genTypeID.forClass(SpecialNames.FloatBoxClass)))
            fb += Unreachable
          } // FloatBoxClass
          fb += StructGet(
            genTypeID.forClass(SpecialNames.FloatBoxClass),
            genFieldID.forClassInstanceField(FieldName(SpecialNames.FloatBoxClass, SpecialNames.valueFieldSimpleName))
          )
          fb += Br(doneLabel)
        } // i31
        fb += I31GetS
        fb += F32ConvertI32S
        fb += Br(doneLabel)
      }
      fb += F32Const(0.0f)
    }
    fb.buildAndAddToModule()
  }

  private def genUnboxDouble()(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm)
    val fb = newFunctionBuilder(genFunctionID.unbox(DoubleRef))
    val xParam = fb.addParam("x", RefType.anyref)
    val resultType = transformPrimType(DoubleType)
    fb.setResultType(resultType)

    fb += LocalGet(xParam)
    fb.block(FunctionType(List(RefType.anyref), List(resultType))) { doneLabel =>
      fb.block(FunctionType(List(RefType.anyref), Nil)) { isNullLabel =>
        fb.block(FunctionType(List(RefType.anyref), List(RefType.i31))) { isI31Label =>
          fb.block(FunctionType(List(RefType.anyref), List(RefType(genTypeID.forClass(SpecialNames.IntegerBoxClass))))) { isIntLabel =>
            fb.block(FunctionType(List(RefType.anyref), List(RefType(genTypeID.forClass(SpecialNames.FloatBoxClass))))) { isFloatLabel =>
              fb.block(FunctionType(List(RefType.anyref), List(RefType(genTypeID.forClass(SpecialNames.DoubleBoxClass))))) { isDoubleLabel =>
                fb += BrOnNull(isNullLabel)
                fb += BrOnCast(isI31Label, RefType.anyref, RefType.i31)
                fb += BrOnCast(isIntLabel, RefType.anyref, RefType(genTypeID.forClass(SpecialNames.IntegerBoxClass)))
                fb += BrOnCast(isFloatLabel, RefType.anyref, RefType(genTypeID.forClass(SpecialNames.FloatBoxClass)))
                fb += BrOnCast(isDoubleLabel, RefType.anyref, RefType(genTypeID.forClass(SpecialNames.DoubleBoxClass)))
                fb += Unreachable
              } // DoubleBoxClass
              fb += StructGet(
                genTypeID.forClass(SpecialNames.DoubleBoxClass),
                genFieldID.forClassInstanceField(FieldName(SpecialNames.DoubleBoxClass, SpecialNames.valueFieldSimpleName))
              )
              fb += Br(doneLabel)
            } // FloatBoxClass
            fb += StructGet(
              genTypeID.forClass(SpecialNames.FloatBoxClass),
              genFieldID.forClassInstanceField(FieldName(SpecialNames.FloatBoxClass, SpecialNames.valueFieldSimpleName))
            )
            fb += F64PromoteF32
            fb += Br(doneLabel)
          } // IntegerBoxClass
          fb += StructGet(
            genTypeID.forClass(SpecialNames.IntegerBoxClass),
            genFieldID.forClassInstanceField(FieldName(SpecialNames.IntegerBoxClass, SpecialNames.valueFieldSimpleName))
          )
          fb += F64ConvertI32S
          fb += Br(doneLabel)
        } // i31
        fb += I31GetS
        fb += F64ConvertI32S
        fb += Br(doneLabel)
      }
      fb += F64Const(0.0)
    }
    fb.buildAndAddToModule()
  }

  private def genUnboxByteOrShort(typeRef: PrimRef)(implicit ctx: WasmContext): Unit = {
    /* The unboxing functions for Byte and Short actually do exactly the same thing.
     * We keep them separate so that the rest of the codebase is clearer.
     * Note that *checked* unboxing goes through `genFunctionID.asInstance` instead.
     */

    val fb = newFunctionBuilder(genFunctionID.unbox(typeRef))
    val xParam = fb.addParam("x", RefType.anyref)
    fb.setResultType(Int32)

    // If x is a (ref i31), extract it
    fb.block(RefType.anyref) { xIsNotI31Label =>
      fb += LocalGet(xParam)
      fb += BrOnCastFail(xIsNotI31Label, RefType.anyref, RefType.i31)
      fb += I31GetS
      fb += Return
    }

    // Otherwise, it must be null, so return 0
    // Note that all JS `number`s in the correct range are guaranteed to be i31ref's
    fb += Drop
    fb += I32Const(0)

    fb.buildAndAddToModule()
  }

  private def genTestByteOrShort(typeRef: PrimRef, signExtend: SimpleInstr)(
        implicit ctx: WasmContext): Unit = {

    val fb = newFunctionBuilder(genFunctionID.typeTest(typeRef))
    val xParam = fb.addParam("x", RefType.anyref)
    fb.setResultType(Int32)

    val intValueLocal = fb.addLocal("intValue", Int32)

    // If x is a (ref i31), extract it and test whether it sign-extends to itself
    fb.block(RefType.anyref) { xIsNotI31Label =>
      fb += LocalGet(xParam)
      fb += BrOnCastFail(xIsNotI31Label, RefType.anyref, RefType.i31)
      fb += I31GetS
      fb += LocalTee(intValueLocal)
      fb += LocalGet(intValueLocal)
      fb += signExtend
      fb += I32Eq
      fb += Return
    }

    // Otherwise, return false
    // Note that all JS `number`s in the correct range are guaranteed to be i31ref's
    fb += Drop
    fb += I32Const(0)

    fb.buildAndAddToModule()
  }

  private def genTestFloat()(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm)
    val fb = newFunctionBuilder(genFunctionID.typeTest(FloatRef))
    val xParam = fb.addParam("x", RefType.anyref)
    fb.setResultType(Int32)

    val doubleValue = fb.addLocal("doubleValue", Float64)

    fb += LocalGet(xParam)
    fb += Call(genFunctionID.typeTest(DoubleRef))
    fb.ifThenElse(Int32) {
      fb += LocalGet(xParam)
      fb += Call(genFunctionID.unbox(DoubleRef))
      fb += LocalTee(doubleValue)

      // if doubleValue.toFloat.toDouble == doubleValue
      fb += F32DemoteF64
      fb += F64PromoteF32
      fb += LocalGet(doubleValue)
      fb += F64Eq
      fb.ifThenElse(Int32) {
        fb += I32Const(1)
      } {
        // if it is NaN
        fb += LocalGet(doubleValue)
        fb += LocalGet(doubleValue)
        fb += F64Ne
      }
    } {
      fb += I32Const(0)
    }
    fb.buildAndAddToModule()
  }

  private def genTestInteger()(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm)

    val fb = newFunctionBuilder(genFunctionID.typeTest(IntRef))
    val xParam = fb.addParam("x", RefType.anyref)
    fb.setResultType(Int32)

    val doubleValue = fb.addLocal("doubleValue", Float64)
    val truncatedDoubleValue = fb.addLocal("truncDoubleValue", Float64)

    // fast path
    fb += LocalGet(xParam)
    fb += RefTest(RefType.i31)
    fb.ifThen() {
      fb += I32Const(1)
      fb += Return
    }

    fb += LocalGet(xParam)
    fb += RefTest(RefType(genTypeID.forClass(SpecialNames.IntegerBoxClass)))
    fb.ifThen() {
      fb += I32Const(1)
      fb += Return
    }

    fb += LocalGet(xParam)
    fb += Call(genFunctionID.typeTest(DoubleRef))
    fb.ifThenElse(Int32) {
      fb += LocalGet(xParam)
      fb += Call(genFunctionID.unbox(DoubleRef))
      // doubleValue.toInt.toDouble == doubleValue
      // NaN and Inifinity will be false
      // because of i32.trunc_sat_f64_s
      fb += LocalTee(doubleValue)
      fb += LocalGet(doubleValue)
      fb += I32TruncSatF64S
      fb += F64ConvertI32S
      fb += LocalTee(truncatedDoubleValue)
      fb += F64Eq

      // check same bit pattern to avoid -0.0 to be Int
      fb += LocalGet(doubleValue)
      fb += I64ReinterpretF64
      fb += LocalGet(truncatedDoubleValue)
      fb += I64ReinterpretF64
      fb += I64Eq

      fb += I32And
    } {
      fb += I32Const(0)
    }

    fb.buildAndAddToModule()
  }

  private def genTestDouble()(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm)

    val fb = newFunctionBuilder(genFunctionID.typeTest(DoubleRef))
    val xParam = fb.addParam("x", RefType.anyref)
    fb.setResultType(Int32)

    fb += LocalGet(xParam)
    fb += RefTest(RefType.i31)
    fb.ifThen() {
      fb += I32Const(1)
      fb += Return
    }
    for (t <- List(
        SpecialNames.IntegerBoxClass,
        SpecialNames.FloatBoxClass,
        SpecialNames.DoubleBoxClass
    )) {
      fb += LocalGet(xParam)
      fb += RefTest(RefType(genTypeID.forClass(t)))
      fb.ifThen() {
        fb += I32Const(1)
        fb += Return
      }
    }
    fb += I32Const(0)
    fb += Return
    fb.buildAndAddToModule()
  }

  private def genStringLiteral()(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm, "genStringLiteral should be generated only for Wasm only target")
    val fb = newFunctionBuilder(genFunctionID.stringLiteral)
    val offsetParam = fb.addParam("offset", Int32)
    val sizeParam = fb.addParam("size", Int32)
    val stringIndexParam = fb.addParam("stringIndex", Int32)

    fb.setResultType(RefType(genTypeID.wasmString))

    fb += LocalGet(offsetParam)
    fb += LocalGet(sizeParam)
    fb += ArrayNewData(genTypeID.i16Array, genDataID.string)
    fb += LocalGet(sizeParam) // length
    fb += RefNull(HeapType(genTypeID.wasmString))

    fb += StructNew(genTypeID.wasmString)
    // TODO: cache

    // fb.setResultType(RefType.extern)

    // val str = fb.addLocal("str", RefType.extern)

    // fb.block(RefType.extern) { cacheHit =>
    //   fb += GlobalGet(genGlobalID.stringLiteralCache)
    //   fb += LocalGet(stringIndexParam)
    //   fb += ArrayGet(genTypeID.externrefArray)

    //   fb += BrOnNonNull(cacheHit)

    //   // cache miss, create a new string and cache it
    //   fb += GlobalGet(genGlobalID.stringLiteralCache)
    //   fb += LocalGet(stringIndexParam)

    //   fb += LocalGet(offsetParam)
    //   fb += LocalGet(sizeParam)
    //   fb += ArrayNewData(genTypeID.i16Array, genDataID.string)
    //   fb += Call(genFunctionID.createStringFromData)
    //   fb += LocalTee(str)
    //   fb += ArraySet(genTypeID.externrefArray)

    //   fb += LocalGet(str)
    // }


    fb.buildAndAddToModule()
  }

  /** `typeDataName: (ref typeData) -> (ref extern)` (representing a `string`).
   *
   *  Initializes the `name` field of the given `typeData` if that was not done yet, and returns its
   *  value.
   *
   *  The computed value is specified by `java.lang.Class.getName()`. See also the documentation on
   *  [[Names.StructFieldIdx.typeData.name]] for details.
   *
   *  @see
   *    [[https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getName()]]
   */
  private def genTypeDataName()(implicit ctx: WasmContext): Unit = {

    val typeDataType = RefType(genTypeID.typeData)

    val fb = newFunctionBuilder(genFunctionID.typeDataName)
    val typeDataParam = fb.addParam("typeData", typeDataType)
    fb.setResultType(stringType)

    val componentTypeDataLocal = fb.addLocal("componentTypeData", typeDataType)
    val nameLocal = fb.addLocal("name", stringType)

    def genFromCharCode() =
      if (targetPureWasm) {
        SWasmGen.genWasmStringFromCharCode(fb)
      } else {
        fb += Call(genFunctionID.stringBuiltins.fromCharCode)
      }

    def genArrayTypeDataName(): Unit = {
      // <top of stack> := "[", for the CALL to stringConcat near the end
      fb += I32Const('['.toInt)
      genFromCharCode()

      // componentTypeData := ref_as_non_null(typeData.componentType)
      fb += LocalGet(typeDataParam)
      fb += StructGet(
        genTypeID.typeData,
        genFieldID.typeData.componentType
      )
      fb += RefAsNonNull
      fb += LocalSet(componentTypeDataLocal)

      // switch (componentTypeData.kind)
      // the result of this switch is the string that must come after "["
      fb.switch(stringType) { () =>
        // scrutinee
        fb += LocalGet(componentTypeDataLocal)
        fb += StructGet(genTypeID.typeData, genFieldID.typeData.kind)
      }(
        List(KindBoolean) -> { () =>
          fb += I32Const('Z'.toInt)
          genFromCharCode()
        },
        List(KindChar) -> { () =>
          fb += I32Const('C'.toInt)
          genFromCharCode()
        },
        List(KindByte) -> { () =>
          fb += I32Const('B'.toInt)
          genFromCharCode()
        },
        List(KindShort) -> { () =>
          fb += I32Const('S'.toInt)
          genFromCharCode()
        },
        List(KindInt) -> { () =>
          fb += I32Const('I'.toInt)
          genFromCharCode()
        },
        List(KindLong) -> { () =>
          fb += I32Const('J'.toInt)
          genFromCharCode()
        },
        List(KindFloat) -> { () =>
          fb += I32Const('F'.toInt)
          genFromCharCode()
        },
        List(KindDouble) -> { () =>
          fb += I32Const('D'.toInt)
          genFromCharCode()
        },
        List(KindArray) -> { () =>
          // the component type is an array; get its own name
          fb += LocalGet(componentTypeDataLocal)
          fb += Call(genFunctionID.typeDataName)
        }
      ) { () =>
        // default: the component type is neither a primitive nor an array;
        // concatenate "L" + <its own name> + ";"
        fb += I32Const('L'.toInt)
        genFromCharCode()
        fb += LocalGet(componentTypeDataLocal)
        fb += Call(genFunctionID.typeDataName)
        genStringConcat(fb)
        fb += I32Const(';'.toInt)
        genFromCharCode()
        genStringConcat(fb)
      }

      // At this point, the stack contains "[" and the string that must be concatenated with it
      genStringConcat(fb)
    }

    if (targetPureWasm) {
      fb.block(stringType) { alreadyInitializedLabel =>
        // br_on_non_null $alreadyInitialized typeData.name
        fb += LocalGet(typeDataParam)
        fb += StructGet(genTypeID.typeData, genFieldID.typeData.name)
        fb += BrOnNonNull(alreadyInitializedLabel)

        // for the STRUCT_SET typeData.name near the end
        fb += LocalGet(typeDataParam)

        // if typeData.kind == KindArray
        fb += LocalGet(typeDataParam)
        fb += StructGet(genTypeID.typeData, genFieldID.typeData.kind)
        fb += I32Const(KindArray)
        fb += I32Eq
        fb.ifThenElse(stringType) {
          // it is an array; compute its name from the component type name
          genArrayTypeDataName()
        } {
          // it is not an array; its name is stored in nameData
          for (
            idx <- List(
              genFieldID.typeData.nameOffset,
              genFieldID.typeData.nameSize,
              genFieldID.typeData.nameStringIndex
            )
          ) {
            fb += LocalGet(typeDataParam)
            fb += StructGet(genTypeID.typeData, idx)
          }
          fb += Call(genFunctionID.stringLiteral)
        }

        // typeData.name := <top of stack> ; leave it on the stack
        fb += LocalTee(nameLocal)
        fb += StructSet(genTypeID.typeData, genFieldID.typeData.name)
        fb += LocalGet(nameLocal)
      }
    } else {
      fb.block(stringType) { alreadyInitializedLabel =>
        // br_on_non_null $alreadyInitialized typeData.name
        fb += LocalGet(typeDataParam)
        fb += StructGet(genTypeID.typeData, genFieldID.typeData.name)
        fb += BrOnNonNull(alreadyInitializedLabel)

        /* if it was null, the typeData must represent an array type;
         * compute its name from the component type name
         */

        // for the STRUCT_SET typeData.name near the end
        fb += LocalGet(typeDataParam)
        genArrayTypeDataName()

        // typeData.name := <top of stack> ; leave it on the stack
        fb += LocalTee(nameLocal)
        fb += StructSet(genTypeID.typeData, genFieldID.typeData.name)
        fb += LocalGet(nameLocal)
      }
    }

    fb.buildAndAddToModule()
  }

  /** `createClassOf: (ref typeData) -> (ref jlClass)`.
   *
   *  Creates the unique `java.lang.Class` instance associated with the given `typeData`, stores it
   *  in its `classOfValue` field, and returns it.
   *
   *  Must be called only if the `classOfValue` of the typeData is null. All call sites must deal
   *  with the non-null case as a fast-path.
   */
  private def genCreateClassOf()(implicit ctx: WasmContext): Unit = {
    val typeDataType = RefType(genTypeID.typeData)

    val fb = newFunctionBuilder(genFunctionID.createClassOf)
    val typeDataParam = fb.addParam("typeData", typeDataType)
    fb.setResultType(RefType(genTypeID.ClassStruct))

    val classInstanceLocal = fb.addLocal("classInstance", RefType(genTypeID.ClassStruct))

    // classInstance := newDefault$java.lang.Class(typeData)
    // leave it on the stack for the constructor call
    fb += LocalGet(typeDataParam)
    fb += Call(genFunctionID.newDefault(ClassClass))
    fb += LocalTee(classInstanceLocal)

    // Call java.lang.Class::<init>()
    fb += Call(
      genFunctionID.forMethod(
        MemberNamespace.Constructor,
        ClassClass,
        NoArgConstructorName
      )
    )

    // typeData.classOfValue := classInstance
    fb += LocalGet(typeDataParam)
    fb += LocalGet(classInstanceLocal)
    fb += StructSet(genTypeID.typeData, genFieldID.typeData.classOfValue)

    // <top-of-stack> := classInstance for the implicit return
    fb += LocalGet(classInstanceLocal)

    fb.buildAndAddToModule()
  }

  /** `getClassOf: (ref typeData) -> (ref jlClass)`.
   *
   *  Initializes the `java.lang.Class` instance associated with the given `typeData` if not already
   *  done, and returns it.
   */
  private def genGetClassOf()(implicit ctx: WasmContext): Unit = {
    val typeDataType = RefType(genTypeID.typeData)

    val fb = newFunctionBuilder(genFunctionID.getClassOf)
    val typeDataParam = fb.addParam("typeData", typeDataType)
    fb.setResultType(RefType(genTypeID.ClassStruct))

    fb.block(RefType(genTypeID.ClassStruct)) { alreadyInitializedLabel =>
      // fast path
      fb += LocalGet(typeDataParam)
      fb += StructGet(genTypeID.typeData, genFieldID.typeData.classOfValue)
      fb += BrOnNonNull(alreadyInitializedLabel)
      // slow path
      fb += LocalGet(typeDataParam)
      fb += Call(genFunctionID.createClassOf)
    } // end bock alreadyInitializedLabel

    fb.buildAndAddToModule()
  }

  /** `valueDescription: anyref -> (ref extern)` (a string).
   *
   *  Returns a safe string description of a value. This helper is never called
   *  for `value === null`. As implemented, it would return `"object"` if it were.
   */
  private def genValueDescription()(implicit ctx: WasmContext): Unit = {
    val objectType = RefType(genTypeID.ObjectStruct)

    val fb = newFunctionBuilder(genFunctionID.valueDescription)
    val valueParam = fb.addParam("value", anyref)
    fb.setResultType(stringType)

    if (targetPureWasm) {
      fb.block(anyref) { notOurObjectLabel =>
        fb.block(objectType) { isCharLabel =>
          fb.block(objectType) { isLongLabel =>
            fb.block(objectType) { isIntegerLabel =>
              fb.block(objectType) { isFloatLabel =>
                fb.block(objectType) { isDoubleLabel =>
                  // If it not our object, jump out of notOurObject
                  fb += LocalGet(valueParam)
                  fb += BrOnCastFail(notOurObjectLabel, anyref, objectType)

                  fb += BrOnCast(isLongLabel, objectType, RefType(genTypeID.forClass(SpecialNames.LongBoxClass)))
                  fb += BrOnCast(isCharLabel, objectType, RefType(genTypeID.forClass(SpecialNames.CharBoxClass)))
                  fb += BrOnCast(isIntegerLabel, objectType, RefType(genTypeID.forClass(SpecialNames.IntegerBoxClass)))
                  fb += BrOnCast(isFloatLabel, objectType, RefType(genTypeID.forClass(SpecialNames.FloatBoxClass)))
                  fb += BrOnCast(isDoubleLabel, objectType, RefType(genTypeID.forClass(SpecialNames.DoubleBoxClass)))

                  // Get and return the class name
                  fb += StructGet(genTypeID.ObjectStruct, genFieldID.objStruct.vtable)
                  fb += ReturnCall(genFunctionID.typeDataName)
                }

                fb ++= ctx.stringPool.getConstantStringInstr("double")
                fb += Return
              }

              fb ++= ctx.stringPool.getConstantStringInstr("float")
              fb += Return
            }

            fb ++= ctx.stringPool.getConstantStringInstr("int")
            fb += Return
          }

          // Return the constant string "long"
          fb ++= ctx.stringPool.getConstantStringInstr("long")
          fb += Return
        }

        // Return the constant string "char"
        fb ++= ctx.stringPool.getConstantStringInstr("char")
        fb += Return
      }
    } else {
      fb.block(anyref) { notOurObjectLabel =>
        fb.block(objectType) { isCharLabel =>
          fb.block(objectType) { isLongLabel =>
            // If it not our object, jump out of notOurObject
            fb += LocalGet(valueParam)
            fb += BrOnCastFail(notOurObjectLabel, anyref, objectType)

            // If is a long or char box, jump out to the appropriate label
            fb += BrOnCast(isLongLabel, objectType, RefType(genTypeID.forClass(SpecialNames.LongBoxClass)))
            fb += BrOnCast(isCharLabel, objectType, RefType(genTypeID.forClass(SpecialNames.CharBoxClass)))

            // Get and return the class name
            fb += StructGet(genTypeID.ObjectStruct, genFieldID.objStruct.vtable)
            fb += ReturnCall(genFunctionID.typeDataName)
          }

          // Return the constant string "long"
          fb ++= ctx.stringPool.getConstantStringInstr("long")
          fb += Return
        }
        // Return the constant string "char"
        fb ++= ctx.stringPool.getConstantStringInstr("char")
        fb += Return
      }
    }

    if (targetPureWasm) {
      // shouldn't reach here
      fb += Drop
      fb ++= ctx.stringPool.getConstantStringInstr("TODO")
    } else {
      // When it is not one of our objects, use the JS helper
      fb += Call(genFunctionID.jsValueDescription)
    }

    fb.buildAndAddToModule()
  }

  /** `classCastException: [anyref, (ref typeData)] -> void`.
   *
   *  This function always throws. It should be followed by an `unreachable`
   *  statement.
   */
  private def genClassCastException()(implicit ctx: WasmContext): Unit = {
    val typeDataType = RefType(genTypeID.typeData)

    val fb = newFunctionBuilder(genFunctionID.classCastException)
    val objParam = fb.addParam("obj", anyref)
    val typeDataParam = fb.addParam("typeData", typeDataType)

    maybeWrapInUBE(fb, semantics.asInstanceOfs) {
      genNewScalaClass(fb, ClassCastExceptionClass, SpecialNames.StringArgConstructorName) {
        fb += LocalGet(objParam)
        fb += Call(genFunctionID.valueDescription)

        fb ++= ctx.stringPool.getConstantStringInstr(" cannot be cast to ")
        genStringConcat(fb)

        fb += LocalGet(typeDataParam)
        fb += Call(genFunctionID.typeDataName)
        genStringConcat(fb)
      }
    }

    genThrow(fb, fakeResult = Nil)

    fb.buildAndAddToModule()
  }

  /** Generates the `asInstance` functions for primitive types.
   */
  private def genPrimitiveAsInstances()(implicit ctx: WasmContext): Unit = {
    val primTypesWithAsInstances: List[PrimType] = List(
      UndefType,
      BooleanType,
      CharType,
      ByteType,
      ShortType,
      IntType,
      LongType,
      FloatType,
      DoubleType,
      StringType
    )

    for (primType <- primTypesWithAsInstances) {
      // asInstanceOf[PrimType]
      genPrimitiveOrBoxedClassAsInstance(primType, targetTpe = primType, isUnbox = true)

      // asInstanceOf[BoxedClass]
      val boxedClassType = ClassType(PrimTypeToBoxedClass(primType), nullable = true)
      genPrimitiveOrBoxedClassAsInstance(primType, targetTpe = boxedClassType, isUnbox = false)
    }
  }

  /** Common logic for primitives and boxed classes in `genPrimitiveAsInstances`. */
  private def genPrimitiveOrBoxedClassAsInstance(primType: PrimType,
      targetTpe: irtpe.Type, isUnbox: Boolean)(
      implicit ctx: WasmContext): Unit = {

    val origName = OriginalName("as." + targetTpe.show())

    val resultType = TypeTransformer.transformSingleType(targetTpe)

    val fb = newFunctionBuilder(genFunctionID.asInstance(targetTpe), origName)
    val objParam = fb.addParam("obj", RefType.anyref)
    fb.setResultType(resultType)

    def genCastDerivedClass(objIsNullLabel: LabelID): Unit = {
      val boxClass = primType match {
        case CharType => SpecialNames.CharBoxClass
        case LongType => SpecialNames.LongBoxClass
        case _ => throw new AssertionError(s"Invalid primType: $primType")
      }
      val structTypeID = genTypeID.forClass(boxClass)

      fb.block(RefType.any) { castFailLabel =>
        fb += LocalGet(objParam)
        fb += BrOnNull(objIsNullLabel)
        fb += BrOnCastFail(castFailLabel, RefType.any, RefType(structTypeID))

        // Extract the `value` field if unboxing
        if (isUnbox) {
          val fieldName = FieldName(boxClass, SpecialNames.valueFieldSimpleName)
          fb += StructGet(structTypeID, genFieldID.forClassInstanceField(fieldName))
        }

        fb += Return
      }
    }

    fb.block() { objIsNullLabel =>
      primType match {
        // For byte and short, use br_on_cast_fail with i31 then check the value
        case ByteType | ShortType =>
          val intValueLocal = fb.addLocal("intValue", Int32)

          fb.block(RefType.anyref) { castFailLabel =>
            fb += LocalGet(objParam)
            fb += BrOnNull(objIsNullLabel)
            fb += BrOnCastFail(castFailLabel, RefType.any, RefType.i31)

            // Extract the i31 value
            fb += I31GetS
            fb += LocalTee(intValueLocal)

            // if it sign-extends to itself
            fb += LocalGet(intValueLocal)
            if (primType == ByteType)
              fb += I32Extend8S
            else
              fb += I32Extend16S
            fb += I32Eq
            fb.ifThen() {
              // then success
              if (isUnbox)
                fb += LocalGet(intValueLocal)
              else
                fb += LocalGet(objParam)
              fb += Return
            }

            // Fall through for CCE
            // Note that all JS `number`s in the correct range are guaranteed to be i31ref's
            fb += LocalGet(objParam)
          }

        // For char and long, use br_on_cast_fail to test+cast to the box class
        case CharType | LongType =>
          genCastDerivedClass(objIsNullLabel)

        // For all other types, use type test, and separately unbox if required
        case _ =>
          // For Int, include a fast path for values that fit in i31
          if (primType == IntType) {
            fb.block(RefType.any) { notI31Label =>
              fb += LocalGet(objParam)
              fb += BrOnNull(objIsNullLabel)
              fb += BrOnCastFail(notI31Label, RefType.any, RefType.i31)
              if (isUnbox)
                fb += I31GetS
              fb += Return
            }
          } else {
            fb += LocalGet(objParam)
            fb += BrOnNull(objIsNullLabel)
          }

          // if obj.isInstanceOf[primType]
          primType match {
            case UndefType =>
              fb += Call(genFunctionID.isUndef)
            case StringType =>
              if (targetPureWasm) {
                fb += RefTest(RefType(genTypeID.wasmString))
              } else {
                fb += ExternConvertAny
                fb += Call(genFunctionID.stringBuiltins.test)
              }
            case ByteType | ShortType | IntType | FloatType | DoubleType if targetPureWasm =>
              fb += Call(genFunctionID.typeTest(DoubleRef))
            case primType: PrimTypeWithRef =>
              fb += Call(genFunctionID.typeTest(primType.primRef))
          }
          fb.ifThen() {
            // then, unbox if required then return
            if (isUnbox) {
              primType match {
                case UndefType =>
                  fb += GlobalGet(genGlobalID.undef)
                case StringType =>
                  fb += LocalGet(objParam)
                  if (targetPureWasm) {
                    fb += RefCast(RefType(genTypeID.wasmString))
                  } else {
                    fb += ExternConvertAny
                    fb += RefAsNonNull
                  }
                case ByteType | ShortType | IntType if targetPureWasm =>
                  fb += LocalGet(objParam)
                  fb += Call(genFunctionID.unbox(DoubleRef))
                  fb += I32TruncF64S

                case FloatType if targetPureWasm =>
                  fb += LocalGet(objParam)
                  fb += Call(genFunctionID.unbox(DoubleRef))
                  fb += F32DemoteF64

                case primType: PrimTypeWithRef =>
                  fb += LocalGet(objParam)
                  fb += Call(genFunctionID.unbox(primType.primRef))
              }
            } else {
              fb += LocalGet(objParam)
              primType match {
                case StringType =>
                  if (targetPureWasm) {
                    fb += RefCast(RefType.nullable(genTypeID.wasmString))
                  } else {
                    fb += ExternConvertAny
                    fb += RefAsNonNull
                  }

                // subtype of DoubleType (Byte, Short, Int, Float, and Double)
                case ByteType | ShortType | IntType if targetPureWasm =>
                  fb += Call(genFunctionID.unbox(DoubleRef))
                  fb += I32TruncF64S
                  fb += Call(genFunctionID.box(primType.asInstanceOf[PrimTypeWithRef].primRef))

                case FloatType if targetPureWasm =>
                  fb += Call(genFunctionID.unbox(DoubleRef))
                  fb += F32DemoteF64
                  fb += Call(genFunctionID.box(FloatRef))

                case p: PrimTypeWithRef if targetPureWasm =>
                  fb += Call(genFunctionID.unbox(p.primRef))
                  fb += Call(genFunctionID.box(p.primRef))
                case _ =>
              }

            }
            fb += Return
          }

          // Fall through for CCE
          fb += LocalGet(objParam)
      }

      // If we get here, it is a CCE
      fb += GlobalGet(genGlobalID.forVTable(PrimTypeToBoxedClass(primType)))
      fb += Call(genFunctionID.classCastException)
      genForwardThrowAlways(fb, fakeResult = List(SWasmGen.genZeroOf(targetTpe)))
    }

    // obj is null -- load the zero of the target type (which is `null` for boxed classes)
    fb += SWasmGen.genZeroOf(targetTpe)

    fb.buildAndAddToModule()
  }

  private def genArrayAsInstances()(implicit ctx: WasmContext): Unit = {
    for (baseRef <- arrayBaseRefs)
      genBaseArrayAsInstance(baseRef)

    genAsSpecificRefArray()
  }

  private def genBaseArrayAsInstance(baseRef: NonArrayTypeRef)(implicit ctx: WasmContext): Unit = {
    val arrayTypeRef = ArrayTypeRef(baseRef, 1)

    val wasmTypeID = genTypeID.forArrayClass(arrayTypeRef)
    val resultType = RefType.nullable(wasmTypeID)

    val fb = newFunctionBuilder(
      genFunctionID.asInstance(irtpe.ArrayType(arrayTypeRef, nullable = true)),
      OriginalName("asArray." + baseRef.displayName)
    )
    val objParam = fb.addParam("obj", anyref)
    fb.setResultType(resultType)

    fb.block(resultType) { successLabel =>
      fb += LocalGet(objParam)
      fb += BrOnCast(successLabel, anyref, resultType)

      // If we get here, it's a CCE -- `obj` is still on the stack
      fb += GlobalGet(genGlobalID.forVTable(baseRef))
      fb += I32Const(1)
      fb += Call(genFunctionID.arrayTypeData)
      fb += Call(genFunctionID.classCastException)
      genForwardThrowAlways(fb, fakeResult = List(RefNull(HeapType.None)))
    }

    fb.buildAndAddToModule()
  }

  private def genAsSpecificRefArray()(implicit ctx: WasmContext): Unit = {
    val refArrayStructTypeID = genTypeID.forArrayClass(ArrayTypeRef(ClassRef(ObjectClass), 1))
    val resultType = RefType.nullable(refArrayStructTypeID)

    val fb = newFunctionBuilder(genFunctionID.asSpecificRefArray)
    val objParam = fb.addParam("obj", anyref)
    val arrayTypeDataParam = fb.addParam("arrayTypeData", RefType(genTypeID.typeData))
    fb.setResultType(resultType)

    val refArrayLocal = fb.addLocal("refArray", RefType(refArrayStructTypeID))

    fb.block(resultType) { successLabel =>
      fb.block() { isNullLabel =>
        fb.block(anyref) { failureLabel =>
          // If obj is null, return null
          fb += LocalGet(objParam)
          fb += BrOnNull(isNullLabel)

          // Otherwise, if we cannot cast to ObjectArray, fail
          fb += BrOnCastFail(failureLabel, RefType.any, RefType(refArrayStructTypeID))
          fb += LocalTee(refArrayLocal) // leave it on the stack for BrIf or for fall through to CCE

          // Otherwise, test assignability of the array type
          fb += LocalGet(arrayTypeDataParam)
          fb += LocalGet(refArrayLocal)
          fb += StructGet(genTypeID.ObjectStruct, genFieldID.objStruct.vtable)
          fb += Call(genFunctionID.isAssignableFrom)

          // If true, jump to success
          fb += BrIf(successLabel)
        }

        // If we get here, it's a CCE -- `obj` is still on the stack
        fb += LocalGet(arrayTypeDataParam)
        fb += Call(genFunctionID.classCastException)
        genForwardThrowAlways(fb, fakeResult = List(RefNull(HeapType.None)))
      }

      fb += RefNull(HeapType.None)
    }

    fb.buildAndAddToModule()
  }

  /** `throwArrayStoreException: anyref -> void`.
   *
   *  This function always throws. It should be followed by an `unreachable`
   *  statement.
   */
  private def genThrowArrayStoreException()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.throwArrayStoreException)
    val valueParam = fb.addParam("value", anyref)

    maybeWrapInUBE(fb, semantics.arrayStores) {
      genNewScalaClass(fb, ArrayStoreExceptionClass,
          SpecialNames.StringArgConstructorName) {
        fb += LocalGet(valueParam)
        fb += Call(genFunctionID.valueDescription)
      }
    }
    genThrow(fb, fakeResult = Nil)

    fb.buildAndAddToModule()
  }

  /** `throwArrayIndexOutOfBoundsException: i32 -> void`.
   *
   *  This function always throws. It should be followed by an `unreachable`
   *  statement.
   */
  private def genThrowArrayIndexOutOfBoundsException()(implicit ctx: WasmContext): Unit = {
    val typeDataType = RefType(genTypeID.typeData)

    val fb = newFunctionBuilder(genFunctionID.throwArrayIndexOutOfBoundsException)
    val indexParam = fb.addParam("index", Int32)

    maybeWrapInUBE(fb, semantics.arrayIndexOutOfBounds) {
      genNewScalaClass(fb, ArrayIndexOutOfBoundsExceptionClass,
          SpecialNames.StringArgConstructorName) {
        if (targetPureWasm) {
          fb += LocalGet(indexParam)
          fb += Call(genFunctionID.itoa)
        } else {
          fb += LocalGet(indexParam)
          fb += Call(genFunctionID.intToString)
        }
      }
    }
    genThrow(fb, fakeResult = Nil)

    fb.buildAndAddToModule()
  }

  /** `throwNegativeArraySizeException: i32 -> void`.
   *
   *  This function always throws. It should be followed by an `unreachable`
   *  statement.
   */
  private def genThrowNegativeArraySizeException()(implicit ctx: WasmContext): Unit = {
    val typeDataType = RefType(genTypeID.typeData)

    val fb = newFunctionBuilder(genFunctionID.throwNegativeArraySizeException)
    val sizeParam = fb.addParam("size", Int32)

    maybeWrapInUBE(fb, semantics.negativeArraySizes) {
      genNewScalaClass(fb, NegativeArraySizeExceptionClass,
          SpecialNames.StringArgConstructorName) {
        if (targetPureWasm) {
          fb += LocalGet(sizeParam)
          fb += Call(genFunctionID.itoa)
        } else {
          fb += LocalGet(sizeParam)
          fb += Call(genFunctionID.intToString)
        }
      }
    }
    genThrow(fb, fakeResult = Nil)

    fb.buildAndAddToModule()
  }

  /** `throwNullPointerException: void -> void`.
   *
   *  This function always throws. It should be followed by an `unreachable`
   *  statement.
   */
  private def genThrowNullPointerException()(implicit ctx: WasmContext): Unit = {
    val typeDataType = RefType(genTypeID.typeData)

    val fb = newFunctionBuilder(genFunctionID.throwNullPointerException)

    maybeWrapInUBE(fb, semantics.nullPointers) {
      genNewScalaClass(fb, NullPointerExceptionClass, NoArgConstructorName) {
      }
    }
    genThrow(fb, fakeResult = Nil)

    fb.buildAndAddToModule()
  }

  /** Generates the `arrayGet.x` functions. */
  private def genArrayGets()(implicit ctx: WasmContext): Unit = {
    for (baseRef <- arrayBaseRefs)
      genArrayGet(baseRef)
  }

  /** `arrayGet.x: (ref null xArray), i32 -> x`. */
  private def genArrayGet(baseRef: NonArrayTypeRef)(implicit ctx: WasmContext): Unit = {
    val origName = OriginalName("arrayGet." + charCodeForOriginalName(baseRef))

    val arrayTypeRef = ArrayTypeRef(baseRef, 1)
    val arrayStructTypeID = genTypeID.forArrayClass(arrayTypeRef)
    val underlyingTypeID = genTypeID.underlyingOf(arrayTypeRef)

    val elemWasmType = baseRef match {
      case PrimRef(tpe) => transformSingleType(tpe)
      case ClassRef(_)  => anyref
    }

    val fb = newFunctionBuilder(genFunctionID.arrayGet(baseRef), origName)
    val arrayParam = fb.addParam("array", RefType.nullable(arrayStructTypeID))
    val indexParam = fb.addParam("index", Int32)
    fb.setResultType(elemWasmType)

    val underlyingLocal = fb.addLocal("underlying", RefType(underlyingTypeID))

    // Get the underlying array
    fb += LocalGet(arrayParam)
    fb += StructGet(arrayStructTypeID, genFieldID.objStruct.arrayUnderlying)
    fb += LocalTee(underlyingLocal)

    // if underlying.length unsigned_<= index
    fb += ArrayLen
    fb += LocalGet(indexParam)
    fb += I32LeU
    fb.ifThen() {
      // then throw ArrayIndexOutOfBoundsException
      fb += LocalGet(indexParam)
      fb += Call(genFunctionID.throwArrayIndexOutOfBoundsException)
      genForwardThrowAlways(fb, fakeResult = List(SWasmGen.genZeroOf(elemWasmType)))
    }

    // Load the underlying and index
    fb += LocalGet(underlyingLocal)
    fb += LocalGet(indexParam)

    // Use the appropriate variant of array.get for sign extension
    baseRef match {
      case BooleanRef | CharRef =>
        fb += ArrayGetU(underlyingTypeID)
      case ByteRef | ShortRef =>
        fb += ArrayGetS(underlyingTypeID)
      case _ =>
        fb += ArrayGet(underlyingTypeID)
    }

    fb.buildAndAddToModule()
  }

  /** Generates the `arraySet.x` functions. */
  private def genArraySets()(implicit ctx: WasmContext): Unit = {
    for (baseRef <- arrayBaseRefs)
      genArraySet(baseRef)
  }

  /** `arraySet.x: (ref null xArray), i32, x -> []`. */
  private def genArraySet(baseRef: NonArrayTypeRef)(implicit ctx: WasmContext): Unit = {
    val origName = OriginalName("arraySet." + charCodeForOriginalName(baseRef))

    val arrayTypeRef = ArrayTypeRef(baseRef, 1)
    val arrayStructTypeID = genTypeID.forArrayClass(arrayTypeRef)
    val underlyingTypeID = genTypeID.underlyingOf(arrayTypeRef)

    val elemWasmType = baseRef match {
      case PrimRef(tpe) => transformSingleType(tpe)
      case ClassRef(_)  => anyref
    }

    val fb = newFunctionBuilder(genFunctionID.arraySet(baseRef), origName)
    val arrayParam = fb.addParam("array", RefType.nullable(arrayStructTypeID))
    val indexParam = fb.addParam("index", Int32)
    val valueParam = fb.addParam("value", elemWasmType)

    val underlyingLocal = fb.addLocal("underlying", RefType(underlyingTypeID))

    // Get the underlying array
    fb += LocalGet(arrayParam)
    fb += StructGet(arrayStructTypeID, genFieldID.objStruct.arrayUnderlying)

    // Bounds check
    if (semantics.arrayIndexOutOfBounds != CheckedBehavior.Unchecked) {
      fb += LocalTee(underlyingLocal)

      // if underlying.length unsigned_<= index
      fb += ArrayLen
      fb += LocalGet(indexParam)
      fb += I32LeU
      fb.ifThen() {
        // then throw ArrayIndexOutOfBoundsException
        fb += LocalGet(indexParam)
        fb += Call(genFunctionID.throwArrayIndexOutOfBoundsException)
        genForwardThrowAlways(fb, fakeResult = Nil)
      }
    } else {
      fb += LocalSet(underlyingLocal)
    }

    // Store check
    if (semantics.arrayStores != CheckedBehavior.Unchecked &&
        baseRef.isInstanceOf[ClassRef]) {
      val componentTypeDataLocal = fb.addLocal("componentTypeData", RefType(genTypeID.typeData))

      fb.block() { successLabel =>
        // Get the component type data
        fb += LocalGet(arrayParam)
        fb += StructGet(arrayStructTypeID, genFieldID.objStruct.vtable)
        fb += StructGet(genTypeID.ObjectVTable, genFieldID.typeData.componentType)
        fb += RefAsNonNull
        fb += LocalTee(componentTypeDataLocal)

        // Fast path: if componentTypeData eq typeDataOf[jl.Object], succeed
        fb += GlobalGet(genGlobalID.forVTable(ClassRef(ObjectClass)))
        fb += RefEq
        fb += BrIf(successLabel)

        // If componentTypeData.kind >= KindJSType, succeed
        fb += LocalGet(componentTypeDataLocal)
        fb += StructGet(genTypeID.typeData, genFieldID.typeData.kind)
        fb += I32Const(KindJSType)
        fb += I32GeU
        fb += BrIf(successLabel)

        // If value is null, succeed
        fb += LocalGet(valueParam)
        fb += RefIsNull
        fb += BrIf(successLabel)

        // If isInstance(componentTypeData, value), succeed
        fb += LocalGet(componentTypeDataLocal)
        fb += LocalGet(valueParam)
        fb += Call(genFunctionID.isInstance)
        fb += BrIf(successLabel)

        // Otherwise, it is a store exception
        fb += LocalGet(valueParam)
        fb += Call(genFunctionID.throwArrayStoreException)
        genForwardThrowAlways(fb, fakeResult = Nil)
      }
    }

    // Store the value
    fb += LocalGet(underlyingLocal)
    fb += LocalGet(indexParam)
    fb += LocalGet(valueParam)
    fb += ArraySet(underlyingTypeID)

    fb.buildAndAddToModule()
  }

  /** `arrayTypeData: (ref typeData), i32 -> (ref vtable.java.lang.Object)`.
   *
   *  Returns the typeData/vtable of an array with `dims` dimensions over the given typeData. `dims`
   *  must be be strictly positive.
   */
  private def genArrayTypeData()(implicit ctx: WasmContext): Unit = {
    val typeDataType = RefType(genTypeID.typeData)
    val objectVTableType = RefType(genTypeID.ObjectVTable)

    /* Array classes extend Cloneable, Serializable and Object.
     * Filter out the ones that do not have run-time type info at all, as
     * we do for other classes.
     */
    val strictAncestors =
      List(ObjectClass, CloneableClass, SerializableClass)
        .filter(name => ctx.getClassInfoOption(name).exists(_.hasRuntimeTypeInfo))

    val fb = newFunctionBuilder(genFunctionID.arrayTypeData)
    val typeDataParam = fb.addParam("typeData", typeDataType)
    val dimsParam = fb.addParam("dims", Int32)
    fb.setResultType(objectVTableType)

    val arrayTypeDataLocal = fb.addLocal("arrayTypeData", objectVTableType)

    fb.loop() { loopLabel =>
      fb.block(objectVTableType) { arrayOfIsNonNullLabel =>
        // br_on_non_null $arrayOfIsNonNull typeData.arrayOf
        fb += LocalGet(typeDataParam)
        fb += StructGet(
          genTypeID.typeData,
          genFieldID.typeData.arrayOf
        )
        fb += BrOnNonNull(arrayOfIsNonNullLabel)

        // <top-of-stack> := typeData ; for the <old typeData>.arrayOf := ... later on
        fb += LocalGet(typeDataParam)

        // typeData := new typeData(...)
        if (targetPureWasm) {
          fb += I32Const(0) // nameOffset
          fb += I32Const(0) // nameSize
          fb += I32Const(0) // nameStringIndex
          fb += RefNull(HeapType(genTypeID.wasmString)) // name (initialized lazily by typeDataName)
        } else {
          fb += RefNull(HeapType.NoExtern) // name (initialized lazily by typeDataName)
        }

        fb += I32Const(KindArray) // kind = KindArray
        fb += I32Const(0) // specialInstanceTypes = 0

        // strictAncestors
        for (strictAncestor <- strictAncestors)
          fb += GlobalGet(genGlobalID.forVTable(strictAncestor))
        fb += ArrayNewFixed(
          genTypeID.typeDataArray,
          strictAncestors.size
        )

        fb += LocalGet(typeDataParam) // componentType
        fb += RefNull(HeapType.None) // classOf
        fb += RefNull(HeapType.None) // arrayOf

        // clone
        fb.switch(RefType(genTypeID.cloneFunctionType)) { () =>
          fb += LocalGet(typeDataParam)
          fb += StructGet(genTypeID.typeData, genFieldID.typeData.kind)
        }(
          List(KindBoolean) -> { () =>
            fb += ctx.refFuncWithDeclaration(genFunctionID.cloneArray(BooleanRef))
          },
          List(KindChar) -> { () =>
            fb += ctx.refFuncWithDeclaration(genFunctionID.cloneArray(CharRef))
          },
          List(KindByte) -> { () =>
            fb += ctx.refFuncWithDeclaration(genFunctionID.cloneArray(ByteRef))
          },
          List(KindShort) -> { () =>
            fb += ctx.refFuncWithDeclaration(genFunctionID.cloneArray(ShortRef))
          },
          List(KindInt) -> { () =>
            fb += ctx.refFuncWithDeclaration(genFunctionID.cloneArray(IntRef))
          },
          List(KindLong) -> { () =>
            fb += ctx.refFuncWithDeclaration(genFunctionID.cloneArray(LongRef))
          },
          List(KindFloat) -> { () =>
            fb += ctx.refFuncWithDeclaration(genFunctionID.cloneArray(FloatRef))
          },
          List(KindDouble) -> { () =>
            fb += ctx.refFuncWithDeclaration(genFunctionID.cloneArray(DoubleRef))
          }
        ) { () =>
          fb += ctx.refFuncWithDeclaration(
            genFunctionID.cloneArray(ClassRef(ObjectClass))
          )
        }

        // isJSClassInstance
        fb += RefNull(HeapType.NoFunc)

        // reflectiveProxies, empty since all methods of array classes exist in jl.Object
        fb += ArrayNewFixed(genTypeID.reflectiveProxies, 0)

        // itable slots
        val objectClassInfo = ctx.getClassInfo(ObjectClass)
        fb ++= ClassEmitter.genItableSlots(objectClassInfo, List(SerializableClass, CloneableClass))

        // vtable items
        fb ++= objectClassInfo.tableEntries.map { methodName =>
          ctx.refFuncWithDeclaration(objectClassInfo.resolvedMethodInfos(methodName).tableEntryID)
        }
        fb += StructNew(genTypeID.ObjectVTable)
        fb += LocalTee(arrayTypeDataLocal)

        // <old typeData>.arrayOf := typeData
        fb += StructSet(genTypeID.typeData, genFieldID.typeData.arrayOf)

        // put arrayTypeData back on the stack
        fb += LocalGet(arrayTypeDataLocal)
      } // end block $arrayOfIsNonNullLabel

      // dims := dims - 1 -- leave dims on the stack
      fb += LocalGet(dimsParam)
      fb += I32Const(1)
      fb += I32Sub
      fb += LocalTee(dimsParam)

      // if dims == 0 then
      //   return typeData.arrayOf (which is on the stack)
      fb += I32Eqz
      fb.ifThen(FunctionType(List(objectVTableType), List(objectVTableType))) {
        fb += Return
      }

      // typeData := typeData.arrayOf (which is on the stack), then loop back to the beginning
      fb += LocalSet(typeDataParam)
      fb += Br(loopLabel)
    } // end loop $loop
    fb += Unreachable

    fb.buildAndAddToModule()
  }

  /** `checkedString{CharAt,CodePointAt}`: (ref extern), i32 -> i32`.
   *
   *  Accesses a char/code point of a string by index. Used when
   *  stringIndexOutOfBounds are checked.
   */
  private def genCheckedStringCharAtOrCodePointAt(
      checkedHelperID: FunctionID, builtinID: FunctionID)(
      implicit ctx: WasmContext): Unit = {

    val fb = newFunctionBuilder(checkedHelperID)
    val strParam = fb.addParam("str", stringType)
    val indexParam = fb.addParam("index", Int32)
    fb.setResultType(Int32)

    // if index unsigned_>= str.length
    fb += LocalGet(indexParam)
    fb += LocalGet(strParam)
    if (targetPureWasm) {
      fb += StructGet(genTypeID.wasmString, genFieldID.wasmString.length)
    } else {
      fb += Call(genFunctionID.stringBuiltins.length)
    }
    fb += I32GeU // unsigned comparison makes negative values of index larger than the length
    fb.ifThen() {
      // then, throw a StringIndexOutOfBoundsException
      maybeWrapInUBE(fb, semantics.stringIndexOutOfBounds) {
        genNewScalaClass(fb, StringIndexOutOfBoundsExceptionClass,
            SpecialNames.IntArgConstructorName) {
          fb += LocalGet(indexParam)
        }
      }
      genThrow(fb, fakeResult = List(I32Const(0)))
    }

    // otherwise, read the char
    fb += LocalGet(strParam)
    fb += LocalGet(indexParam)
    fb += Call(builtinID)

    fb.buildAndAddToModule()
  }

  /** `checkedSubstringStart: (ref extern), i32 -> (ref extern)`.
   *
   *  Implementation of jl.String.substring(start). Used when
   *  stringIndexOutOfBounds are checked.
   */
  private def genCheckedSubstringStart()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.checkedSubstringStart)
    val strParam = fb.addParam("str", RefType.extern)
    val startParam = fb.addParam("start", Int32)
    fb.setResultType(RefType.extern)

    /* if start unsigned_> str.length
     * The unsigned comparison makes negative values larger than the length.
     */
    fb += LocalGet(startParam)
    fb += LocalGet(strParam)
    fb += Call(genFunctionID.stringBuiltins.length)
    fb += I32GtU
    fb.ifThen() {
      // then, throw a StringIndexOutOfBoundsException
      maybeWrapInUBE(fb, semantics.stringIndexOutOfBounds) {
        genNewScalaClass(fb, StringIndexOutOfBoundsExceptionClass,
            SpecialNames.IntArgConstructorName) {
          fb += LocalGet(startParam)
        }
      }
      genThrow(fb, fakeResult = List(LocalGet(strParam)))
    }

    // otherwise, call the substring builtin
    fb += LocalGet(strParam)
    fb += LocalGet(startParam)
    fb += I32Const(-1) // unsigned max value
    fb += Call(genFunctionID.stringBuiltins.substring)

    fb.buildAndAddToModule()
  }

  /** `checkedSubstringStartEnd: (ref extern), i32, i32 -> (ref extern)`.
   *
   *  Implementation of jl.String.substring(start, end). Used when
   *  stringIndexOutOfBounds are checked.
   */
  private def genCheckedSubstringStartEnd()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.checkedSubstringStartEnd)
    val strParam = fb.addParam("str", RefType.extern)
    val startParam = fb.addParam("start", Int32)
    val endParam = fb.addParam("end", Int32)
    fb.setResultType(RefType.extern)

    /* if (start unsigned_> end) | (end unsigned_> str.length)
     * The unsigned comparisons make negative values larger than the length
     * since the happy path evaluates both conditions anyway, we don't bother
     * with a short-circuiting || and implement an | instead.
     */
    fb += LocalGet(startParam)
    fb += LocalGet(endParam)
    fb += I32GtU
    fb += LocalGet(endParam)
    fb += LocalGet(strParam)
    fb += Call(genFunctionID.stringBuiltins.length)
    fb += I32GtU
    fb += I32Or
    fb.ifThen() {
      // then, throw a StringIndexOutOfBoundsException
      maybeWrapInUBE(fb, semantics.stringIndexOutOfBounds) {
        genNewScalaClass(fb, StringIndexOutOfBoundsExceptionClass,
            SpecialNames.IntArgConstructorName) {
          // Redo part of the test to determine the argument
          fb += LocalGet(startParam) // value if true for Select
          fb += LocalGet(endParam) // value if false for Select

          // start unsigned_> string.length
          fb += LocalGet(startParam)
          fb += LocalGet(strParam)
          fb += Call(genFunctionID.stringBuiltins.length)
          fb += I32GtU

          fb += Select(Nil) // infer i32
        }
      }
      genThrow(fb, fakeResult = List(LocalGet(strParam)))
    }

    // otherwise, call the substring builtin
    fb += LocalGet(strParam)
    fb += LocalGet(startParam)
    fb += LocalGet(endParam)
    fb += Call(genFunctionID.stringBuiltins.substring)

    fb.buildAndAddToModule()
  }

  /** `throwModuleInitError: [] -> []` (always throws).
   *
   *  Throws an `UndefinedBehaviorError` for a module initialization error.
   */
  private def genThrowModuleInitError()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.throwModuleInitError)
    val typeDataParam = fb.addParam("typeData", RefType(genTypeID.typeData))

    genNewScalaClass(fb, SpecialNames.UndefinedBehaviorErrorClass,
        SpecialNames.StringArgConstructorName) {
      fb ++= ctx.stringPool.getConstantStringInstr("Initializer of ")
      fb += LocalGet(typeDataParam)
      fb += Call(genFunctionID.typeDataName)
      genStringConcat(fb)
      fb ++= ctx.stringPool.getConstantStringInstr(
          " called before completion of its super constructor")
      genStringConcat(fb)
    }
    genThrow(fb, fakeResult = Nil)

    fb.buildAndAddToModule()
  }

  /** `isInstance: (ref typeData), anyref -> i32` (a boolean).
   *
   *  Tests whether the given value is a non-null instance of the given type.
   */
  private def genIsInstance()(implicit ctx: WasmContext): Unit = {
    import genFieldID.typeData._

    val typeDataType = RefType(genTypeID.typeData)
    val objectRefType = RefType(genTypeID.ObjectStruct)

    val fb = newFunctionBuilder(genFunctionID.isInstance)
    val typeDataParam = fb.addParam("typeData", typeDataType)
    val valueParam = fb.addParam("value", RefType.anyref)
    fb.setResultType(Int32)

    val valueNonNullLocal = fb.addLocal("valueNonNull", RefType.any)
    val specialInstanceTypesLocal = fb.addLocal("specialInstanceTypes", Int32)

    // switch (typeData.kind)
    fb.switch(Int32) { () =>
      fb += LocalGet(typeDataParam)
      fb += StructGet(genTypeID.typeData, kind)
    }(
      // case anyPrimitiveKind => false
      (KindVoid to KindLastPrimitive).toList -> { () =>
        fb += I32Const(0)
      },
      // case KindObject => value ne null
      List(KindObject) -> { () =>
        fb += LocalGet(valueParam)
        fb += RefIsNull
        fb += I32Eqz
      },
      // for each boxed class, the corresponding primitive type test
      List(KindBoxedUnit) -> { () =>
        fb += LocalGet(valueParam)
        fb += Call(genFunctionID.isUndef)
      },
      List(KindBoxedBoolean) -> { () =>
        fb += LocalGet(valueParam)
        fb += Call(genFunctionID.typeTest(BooleanRef))
      },
      List(KindBoxedCharacter) -> { () =>
        fb += LocalGet(valueParam)
        val structTypeID = genTypeID.forClass(SpecialNames.CharBoxClass)
        fb += RefTest(RefType(structTypeID))
      },
      List(KindBoxedByte) -> { () =>
        fb += LocalGet(valueParam)
        fb += Call(genFunctionID.typeTest(ByteRef))
      },
      List(KindBoxedShort) -> { () =>
        fb += LocalGet(valueParam)
        fb += Call(genFunctionID.typeTest(ShortRef))
      },
      List(KindBoxedInteger) -> { () =>
        fb += LocalGet(valueParam)
        if (targetPureWasm) {
          val structTypeID = genTypeID.forClass(SpecialNames.IntegerBoxClass)
          fb += RefTest(RefType(structTypeID))
        } else {
          fb += Call(genFunctionID.typeTest(IntRef))
        }
      },
      List(KindBoxedLong) -> { () =>
        fb += LocalGet(valueParam)
        val structTypeID = genTypeID.forClass(SpecialNames.LongBoxClass)
        fb += RefTest(RefType(structTypeID))
      },
      List(KindBoxedFloat) -> { () =>
        fb += LocalGet(valueParam)
        if (targetPureWasm) {
          val structTypeID = genTypeID.forClass(SpecialNames.FloatBoxClass)
          fb += RefTest(RefType(structTypeID))
        } else {
          fb += Call(genFunctionID.typeTest(FloatRef))
        }
      },
      List(KindBoxedDouble) -> { () =>
        fb += LocalGet(valueParam)
        if (targetPureWasm) {
          val structTypeID = genTypeID.forClass(SpecialNames.DoubleBoxClass)
          fb += RefTest(RefType(structTypeID))
        } else {
          fb += Call(genFunctionID.typeTest(DoubleRef))
        }
      },
      List(KindBoxedString) -> { () =>
        fb += LocalGet(valueParam)
        if (targetPureWasm) {
          fb += RefTest(RefType(genTypeID.wasmString))
        } else {
          fb += ExternConvertAny
          fb += Call(genFunctionID.stringBuiltins.test)
        }
      },
      // case KindJSType | KindJSTypeWithSuperClass => call typeData.isJSClassInstance(value) or throw if it is null
      List(KindJSType, KindJSTypeWithSuperClass) -> { () =>
        if (targetPureWasm) {
          fb += Unreachable // shouldn't reach here
        } else {
          fb.block(RefType.anyref) { isJSClassInstanceIsNull =>
            // Load value as the argument to the function
            fb += LocalGet(valueParam)

            // Load the function reference; break if null
            fb += LocalGet(typeDataParam)
            fb += StructGet(genTypeID.typeData, isJSClassInstance)
            fb += BrOnNull(isJSClassInstanceIsNull)

            // Call the function
            fb += CallRef(genTypeID.isJSClassInstanceFuncType)
            fb += Return
          }
          fb += Drop // drop `value` which was left on the stack

          // throw new TypeError("...")
          fb ++= ctx.stringPool.getConstantStringInstr(
            "Cannot call isInstance() on a Class representing a JS trait/object"
          )
          fb += Call(genFunctionID.makeTypeError)
          fb += Throw(genTagID.exception)
        }
      }
    ) { () =>
      // case _ =>

      // valueNonNull := as_non_null value; return false if null
      fb.block(RefType.any) { nonNullLabel =>
        fb += LocalGet(valueParam)
        fb += BrOnNonNull(nonNullLabel)
        fb += I32Const(0)
        fb += Return
      }
      fb += LocalSet(valueNonNullLocal)

      /* If `typeData` represents an ancestor of a hijacked classes, we have to
       * answer `true` if `valueNonNull` is a primitive instance of any of the
       * hijacked classes that ancestor class/interface. For example, for
       * `Comparable`, we have to answer `true` if `valueNonNull` is a primitive
       * boolean, number or string.
       *
       * To do that, we use `jsValueType` and `typeData.specialInstanceTypes`.
       *
       * We test whether `jsValueType(valueNonNull)` is in the set represented by
       * `specialInstanceTypes`. Since the latter is a bitset where the bit
       * indices correspond to the values returned by `jsValueType`, we have to
       * test whether
       *
       * ((1 << jsValueType(valueNonNull)) & specialInstanceTypes) != 0
       *
       * Since computing `jsValueType` is somewhat expensive, we first test
       * whether `specialInstanceTypes != 0` before calling `jsValueType`.
       *
       * There is a more elaborated concrete example of this algorithm in
       * `genInstanceTest`.
       */
      fb += LocalGet(typeDataParam)
      fb += StructGet(genTypeID.typeData, specialInstanceTypes)
      fb += LocalTee(specialInstanceTypesLocal)
      fb += I32Const(0)
      fb += I32Ne
      fb.ifThen() {
        // Load (1 << jsValueType(valueNonNull))
        fb += I32Const(1)
        fb += LocalGet(valueNonNullLocal)
        if (targetPureWasm) fb += Call(genFunctionID.scalaValueType)
        else fb += Call(genFunctionID.jsValueType)
        fb += I32Shl

        // if ((... & specialInstanceTypes) != 0)
        fb += LocalGet(specialInstanceTypesLocal)
        fb += I32And
        fb += I32Const(0)
        fb += I32Ne
        fb.ifThen() {
          // then return true
          fb += I32Const(1)
          fb += Return
        }
      }

      // Get the vtable and delegate to isAssignableFrom

      // Load typeData
      fb += LocalGet(typeDataParam)

      // Load the vtable; return false if it is not one of our object
      fb.block(objectRefType) { ourObjectLabel =>
        // Try cast to jl.Object
        fb += LocalGet(valueNonNullLocal)
        fb += BrOnCast(ourObjectLabel, RefType.any, objectRefType)

        // on cast fail, return false
        fb += I32Const(0)
        fb += Return
      }
      fb += StructGet(genTypeID.ObjectStruct, genFieldID.objStruct.vtable)

      // Call isAssignableFrom
      fb += Call(genFunctionID.isAssignableFrom)
    }

    fb.buildAndAddToModule()
  }

  /** `isAssignableFrom: (ref typeData), (ref typeData) -> i32` (a boolean).
   *
   *  Specified by `java.lang.Class.isAssignableFrom(Class)`.
   */
  private def genIsAssignableFrom()(implicit ctx: WasmContext): Unit = {
    import genFieldID.typeData._

    val typeDataType = RefType(genTypeID.typeData)

    val fb = newFunctionBuilder(genFunctionID.isAssignableFrom)
    val typeDataParam = fb.addParam("typeData", typeDataType)
    val fromTypeDataParam = fb.addParam("fromTypeData", typeDataType)
    fb.setResultType(Int32)

    val fromAncestorsLocal = fb.addLocal("fromAncestors", RefType(genTypeID.typeDataArray))
    val lenLocal = fb.addLocal("len", Int32)
    val iLocal = fb.addLocal("i", Int32)

    // if (fromTypeData eq typeData)
    fb += LocalGet(fromTypeDataParam)
    fb += LocalGet(typeDataParam)
    fb += RefEq
    fb.ifThen() {
      // then return true
      fb += I32Const(1)
      fb += Return
    }

    // "Tail call" loop for diving into array component types
    fb.loop(Int32) { loopForArrayLabel =>
      // switch (typeData.kind)
      fb.switch(Int32) { () =>
        // typeData.kind
        fb += LocalGet(typeDataParam)
        fb += StructGet(genTypeID.typeData, kind)
      }(
        // case anyPrimitiveKind => return false
        (KindVoid to KindLastPrimitive).toList -> { () =>
          fb += I32Const(0)
        },
        // case KindArray => check that from is an array, recurse into component types
        List(KindArray) -> { () =>
          fb.block() { fromComponentTypeIsNullLabel =>
            // fromTypeData := fromTypeData.componentType; jump out if null
            fb += LocalGet(fromTypeDataParam)
            fb += StructGet(genTypeID.typeData, componentType)
            fb += BrOnNull(fromComponentTypeIsNullLabel)
            fb += LocalSet(fromTypeDataParam)

            // typeData := ref.as_non_null typeData.componentType (OK because KindArray)
            fb += LocalGet(typeDataParam)
            fb += StructGet(genTypeID.typeData, componentType)
            fb += RefAsNonNull
            fb += LocalSet(typeDataParam)

            // loop back ("tail call")
            fb += Br(loopForArrayLabel)
          }

          // return false
          fb += I32Const(0)
        },
        // case KindObject => return (fromTypeData.kind > KindLastPrimitive)
        List(KindObject) -> { () =>
          fb += LocalGet(fromTypeDataParam)
          fb += StructGet(genTypeID.typeData, kind)
          fb += I32Const(KindLastPrimitive)
          fb += I32GtU
        }
      ) { () =>
        // All other cases: test whether `fromTypeData.strictAncestors` contains `typeData`

        fb.block() { fromAncestorsIsNullLabel =>
          // fromAncestors := fromTypeData.strictAncestors; go to fromAncestorsIsNull if null
          fb += LocalGet(fromTypeDataParam)
          fb += StructGet(genTypeID.typeData, strictAncestors)
          fb += BrOnNull(fromAncestorsIsNullLabel)
          fb += LocalTee(fromAncestorsLocal)

          // if fromAncestors contains typeData, return true

          // len := fromAncestors.length
          fb += ArrayLen
          fb += LocalSet(lenLocal)

          // i := 0
          fb += I32Const(0)
          fb += LocalSet(iLocal)

          // while (i != len)
          fb.whileLoop() {
            fb += LocalGet(iLocal)
            fb += LocalGet(lenLocal)
            fb += I32Ne
          } {
            // if (fromAncestors[i] eq typeData)
            fb += LocalGet(fromAncestorsLocal)
            fb += LocalGet(iLocal)
            fb += ArrayGet(genTypeID.typeDataArray)
            fb += LocalGet(typeDataParam)
            fb += RefEq
            fb.ifThen() {
              // then return true
              fb += I32Const(1)
              fb += Return
            }

            // i := i + 1
            fb += LocalGet(iLocal)
            fb += I32Const(1)
            fb += I32Add
            fb += LocalSet(iLocal)
          }
        }

        // from.strictAncestors is null or does not contain typeData
        // return false
        fb += I32Const(0)
      }
    }

    fb.buildAndAddToModule()
  }

  /** `cast: (ref jlClass), anyref -> anyref`.
   *
   *  Casts the given value to the given type; subject to undefined behaviors.
   *
   *  This is the underlying func for the `Class_cast` operation.
   */
  private def genCast()(implicit ctx: WasmContext): Unit = {
    assert(semantics.asInstanceOfs != CheckedBehavior.Unchecked)

    val fb = newFunctionBuilder(genFunctionID.cast)
    val jlClassParam = fb.addParam("jlClass", RefType(genTypeID.ClassStruct))
    val valueParam = fb.addParam("value", RefType.anyref)
    fb.setResultType(RefType.anyref)

    val typeDataLocal = fb.addLocal("typeData", RefType(genTypeID.typeData))

    fb.block() { successLabel =>
      // typeData := jlClass.data, leave it on the stack for the kind test
      fb += LocalGet(jlClassParam)
      fb += StructGet(genTypeID.ClassStruct, genFieldID.classData)
      fb += LocalTee(typeDataLocal)

      // If typeData.kind >= KindJSType, succeed
      fb += StructGet(genTypeID.typeData, genFieldID.typeData.kind)
      fb += I32Const(KindJSType)
      fb += I32GeU
      fb += BrIf(successLabel)

      // If value is null, succeed
      fb += LocalGet(valueParam)
      fb += RefIsNull // consumes `value`, unlike `BrOnNull` which would leave it on the stack
      fb += BrIf(successLabel)

      // If isInstance(typeData, value), succeed
      fb += LocalGet(typeDataLocal)
      fb += LocalGet(valueParam)
      fb += Call(genFunctionID.isInstance)
      fb += BrIf(successLabel)

      // Otherwise, it is a CCE
      fb += LocalGet(valueParam)
      fb += LocalGet(typeDataLocal)
      fb += Call(genFunctionID.classCastException)
      genForwardThrowAlways(fb, fakeResult = List(RefNull(HeapType.None)))
    }

    fb += LocalGet(valueParam)

    fb.buildAndAddToModule()
  }

  /** `getComponentType: (ref jlClass) -> (ref null jlClass)`.
   *
   *  This is the underlying func for the `Class_componentType` operation.
   */
  private def genGetComponentType()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.getComponentType)
    val jlClassParam = fb.addParam("jlClass", RefType(genTypeID.ClassStruct))
    fb.setResultType(RefType.nullable(genTypeID.ClassStruct))

    fb.block() { nullResultLabel =>
      // Try and extract non-null component type data
      fb += LocalGet(jlClassParam)
      fb += StructGet(genTypeID.ClassStruct, genFieldID.classData)
      fb += StructGet(genTypeID.typeData, genFieldID.typeData.componentType)
      fb += BrOnNull(nullResultLabel)
      // Get the corresponding classOf
      fb += Call(genFunctionID.getClassOf)
      fb += Return
    } // end block nullResultLabel
    fb += RefNull(HeapType(genTypeID.ClassStruct))

    fb.buildAndAddToModule()
  }

  /** `getSuperClass: (ref jlClass) -> (ref null jlClass)`.
   *
   *  This is the underlying func for the `Class_superClass` operation.
   */
  private def genGetSuperClass()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.getSuperClass)
    val jlClassParam = fb.addParam("jlClass", RefType(genTypeID.ClassStruct))
    fb.setResultType(RefType.nullable(genTypeID.ClassStruct))

    val typeDataLocal = fb.addLocal("typeData", RefType(genTypeID.typeData))
    val kindLocal = fb.addLocal("kind", Int32)

    // typeData := jlClass.data
    fb += LocalGet(jlClassParam)
    fb += StructGet(genTypeID.ClassStruct, genFieldID.classData)
    fb += LocalTee(typeDataLocal)

    // kind := typeData.kind
    fb += StructGet(genTypeID.typeData, genFieldID.typeData.kind)
    fb += LocalTee(kindLocal)

    /* There are 3 cases that yield non-null results:
     *
     * - Scala classes that are not jl.Object (KindObject < kind <= KindClass)
     * - JS types with a superClass (kind == KindJSTypeWithSuperClass)
     * - Array classes (kind == KindArray)
     *
     * Note that KindArray < KindObject, and KindJSTypeWithSuperClass > KindObject,
     * so we dispatch these two kinds in the two branches of an
     * `if kind > KindObject` first.
     */

    // if kind > KindObject
    fb += I32Const(KindObject)
    fb += I32GtU
    fb.ifThenElse(RefType(genTypeID.typeData)) {
      // then, we may have to load the superClass from the strictAncestors array
      fb.block() { loadSuperClassFromStrictAncestorsLabel =>
        // if kind <= KindClass, then yes
        fb += LocalGet(kindLocal)
        fb += I32Const(KindClass)
        fb += I32LeU
        fb += BrIf(loadSuperClassFromStrictAncestorsLabel)

        // if kind == KindJSTypeWithSuperClass, then yes
        fb += LocalGet(kindLocal)
        fb += I32Const(KindJSTypeWithSuperClass)
        fb += I32Eq
        fb += BrIf(loadSuperClassFromStrictAncestorsLabel)

        // otherwise, there is no superClass
        fb += RefNull(HeapType(genTypeID.ClassStruct))
        fb += Return
      }

      // load the superClass from the strictAncestors array
      fb += LocalGet(typeDataLocal)
      fb += StructGet(genTypeID.typeData, genFieldID.typeData.strictAncestors)
      fb += I32Const(0)
      fb += ArrayGet(genTypeID.typeDataArray)
    } {
      // else, it might be an Array class

      // if kind != KindArray
      fb += LocalGet(kindLocal)
      fb += I32Const(KindArray)
      fb += I32Ne
      fb.ifThen() {
        // then return null
        fb += RefNull(HeapType(genTypeID.ClassStruct))
        fb += Return
      }

      // otherwise, load the typeData of jl.Object
      fb += GlobalGet(genGlobalID.forVTable(ClassRef(ObjectClass)))
    }

    // Load the jl.Class from the typeData
    fb += Call(genFunctionID.getClassOf)

    fb.buildAndAddToModule()
  }

  /** `newArray: (ref jlClass), i32 -> (ref jlObject)`.
   *
   *  This is the underlying func for the `Class_newArray` operation.
   */
  private def genNewArray()(implicit ctx: WasmContext): Unit = {
    val typeDataType = RefType(genTypeID.typeData)
    val arrayTypeDataType = RefType(genTypeID.ObjectVTable)

    val fb = newFunctionBuilder(genFunctionID.newArray)
    val jlClassParam = fb.addParam("jlClass", RefType(genTypeID.ClassStruct))
    val lengthParam = fb.addParam("length", Int32)
    fb.setResultType(RefType(genTypeID.ObjectStruct))

    val componentTypeDataLocal = fb.addLocal("componentTypeData", RefType(genTypeID.typeData))

    // Check negative array size
    if (semantics.negativeArraySizes != CheckedBehavior.Unchecked) {
      fb += LocalGet(lengthParam)
      fb += I32Const(0)
      fb += I32LtS
      fb.ifThen() {
        fb += LocalGet(lengthParam)
        fb += Call(genFunctionID.throwNegativeArraySizeException)
        genForwardThrowAlways(fb, fakeResult = List(LocalGet(jlClassParam)))
      }
    }

    // componentTypeData := jlClass.data
    fb += LocalGet(jlClassParam)
    fb += StructGet(genTypeID.ClassStruct, genFieldID.classData)
    fb += LocalTee(componentTypeDataLocal)

    // Load the vtable of the ArrayClass instance we will create
    fb += I32Const(1)
    fb += Call(genFunctionID.arrayTypeData)
    if (targetPureWasm) fb += I32Const(0) // idHashCode

    // Load the length
    fb += LocalGet(lengthParam)

    val switchParams: List[Type] =
      if (targetPureWasm) List(arrayTypeDataType, Int32, Int32)
      else List(arrayTypeDataType, Int32)
    // switch (componentTypeData.kind)
    val switchClauseSig = FunctionType(
      switchParams,
      List(RefType(genTypeID.ObjectStruct))
    )
    fb.switch(switchClauseSig) { () =>
      // scrutinee
      fb += LocalGet(componentTypeDataLocal)
      fb += StructGet(genTypeID.typeData, genFieldID.typeData.kind)
    }(
      // case KindPrim => array.new_default underlyingPrimArray; struct.new PrimArray
      primRefsWithKinds.map { case (primRef, kind) =>
        List(kind) -> { () =>
          if (primRef == VoidRef) {
            // throw IllegalArgumentException for VoidRef
            genNewScalaClass(fb, IllegalArgumentExceptionClass, NoArgConstructorName) {
              // no argument
            }
            genThrow(fb, fakeResult = List(LocalGet(jlClassParam)))
          } else {
            val arrayTypeRef = ArrayTypeRef(primRef, 1)
            fb += ArrayNewDefault(genTypeID.underlyingOf(arrayTypeRef))
            fb += StructNew(genTypeID.forArrayClass(arrayTypeRef))
          }
          () // required for correct type inference
        }
      }: _*
    ) { () =>
      // case _ => array.new_default anyrefArray; struct.new ObjectArray
      val arrayTypeRef = ArrayTypeRef(ClassRef(ObjectClass), 1)
      fb += ArrayNewDefault(genTypeID.underlyingOf(arrayTypeRef))
      fb += StructNew(genTypeID.forArrayClass(arrayTypeRef))
    }

    fb.buildAndAddToModule()
  }

  /** `anyGetClass: (ref any) -> (ref null jlClass)`.
   *
   *  This is the implementation of `value.getClass()` when `value` can be an instance of a hijacked
   *  class, i.e., a primitive.
   *
   *  For `number`s, the result is based on the actual value, as specified by
   *  [[https://www.scala-js.org/doc/semantics.html#getclass]].
   */
  private def genAnyGetClass()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.anyGetClass)
    val valueParam = fb.addParam("value", RefType.any)
    fb.setResultType(RefType.nullable(genTypeID.ClassStruct))

    fb.block() { typeDataIsNullLabel =>
      fb += LocalGet(valueParam)
      fb += Call(genFunctionID.anyGetTypeData)
      fb += BrOnNull(typeDataIsNullLabel)
      fb += ReturnCall(genFunctionID.getClassOf)
    }
    fb += RefNull(HeapType.None)

    fb.buildAndAddToModule()
  }

  /** `anyGetClassName: (ref any) -> (ref extern)` (a string).
   *
   *  This is the implementation of `value.getClass().getName()`, which comes
   *  to the backend as the `ObjectClassName` intrinsic.
   */
  private def genAnyGetClassName()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.anyGetClassName)
    val valueParam = fb.addParam("value", RefType.any)
    fb.setResultType(stringType)

    if (semantics.nullPointers == CheckedBehavior.Unchecked) {
      fb += LocalGet(valueParam)
      fb += Call(genFunctionID.anyGetTypeData)
      fb += RefAsNonNull // NPE for null.getName()
      fb += ReturnCall(genFunctionID.typeDataName)
    } else {
      fb.block() { npeLabel =>
        fb += LocalGet(valueParam)
        fb += Call(genFunctionID.anyGetTypeData)
        fb += BrOnNull(npeLabel) // NPE for null.getName()
        fb += ReturnCall(genFunctionID.typeDataName)
      }
      fb += Call(genFunctionID.throwNullPointerException)
      genForwardThrowAlways(fb, fakeResult = ctx.stringPool.getConstantStringInstr(""))
    }

    fb.buildAndAddToModule()
  }

  /** `anyGetTypeData: (ref any) -> (ref null typeData)`.
   *
   *  Common code between `anyGetClass` and `anyGetClassName`.
   */
  private def genAnyGetTypeData()(implicit ctx: WasmContext): Unit = {
    val typeDataType = RefType(genTypeID.typeData)

    val fb = newFunctionBuilder(genFunctionID.anyGetTypeData)
    val valueParam = fb.addParam("value", RefType.any)
    fb.setResultType(RefType.nullable(genTypeID.typeData))

    val doubleValueLocal = fb.addLocal("doubleValue", Float64)
    val intValueLocal = fb.addLocal("intValue", Int32)
    val ourObjectLocal = fb.addLocal("ourObject", RefType(genTypeID.ObjectStruct))

    def getHijackedClassTypeDataInstr(className: ClassName): Instr =
      GlobalGet(genGlobalID.forVTable(className))

    fb.block(RefType(genTypeID.ObjectStruct)) { ourObjectLabel =>
      // if value is our object, jump to $ourObject
      fb += LocalGet(valueParam)
      fb += BrOnCast(
        ourObjectLabel,
        RefType.any,
        RefType(genTypeID.ObjectStruct)
      )

      // switch(jsValueType(value)) { ... }
      fb.switch() { () =>
        // scrutinee
        fb += LocalGet(valueParam)
        if (targetPureWasm) fb += Call(genFunctionID.scalaValueType)
        else fb += Call(genFunctionID.jsValueType)
      }(
        // case JSValueTypeFalse, JSValueTypeTrue => typeDataOf[jl.Boolean]
        List(JSValueTypeFalse, JSValueTypeTrue) -> { () =>
          fb += getHijackedClassTypeDataInstr(BoxedBooleanClass)
          fb += Return
        },
        // case JSValueTypeString => typeDataOf[jl.String]
        List(JSValueTypeString) -> { () =>
          fb += getHijackedClassTypeDataInstr(BoxedStringClass)
          fb += Return
        },
        // case JSValueTypeNumber => ...
        List(JSValueTypeNumber) -> { () =>
          /* For `number`s, the result is based on the actual value, as specified by
           * [[https://www.scala-js.org/doc/semantics.html#getclass]].
           */

          // doubleValue := unboxDouble(value)
          fb += LocalGet(valueParam)
          fb += Call(genFunctionID.unbox(DoubleRef))
          fb += LocalTee(doubleValueLocal)

          // intValue := doubleValue.toInt
          fb += I32TruncSatF64S
          fb += LocalTee(intValueLocal)

          // if same(intValue.toDouble, doubleValue) -- same bit pattern to avoid +0.0 == -0.0
          fb += F64ConvertI32S
          fb += I64ReinterpretF64
          fb += LocalGet(doubleValueLocal)
          fb += I64ReinterpretF64
          fb += I64Eq
          fb.ifThenElse(typeDataType) {
            // then it is a Byte, a Short, or an Integer

            // if intValue.toByte.toInt == intValue
            fb += LocalGet(intValueLocal)
            fb += I32Extend8S
            fb += LocalGet(intValueLocal)
            fb += I32Eq
            fb.ifThenElse(typeDataType) {
              // then it is a Byte
              fb += getHijackedClassTypeDataInstr(BoxedByteClass)
            } {
              // else, if intValue.toShort.toInt == intValue
              fb += LocalGet(intValueLocal)
              fb += I32Extend16S
              fb += LocalGet(intValueLocal)
              fb += I32Eq
              fb.ifThenElse(typeDataType) {
                // then it is a Short
                fb += getHijackedClassTypeDataInstr(BoxedShortClass)
              } {
                // else, it is an Integer
                fb += getHijackedClassTypeDataInstr(BoxedIntegerClass)
              }
            }
          } {
            // else, it is a Float or a Double

            // if doubleValue.toFloat.toDouble == doubleValue
            fb += LocalGet(doubleValueLocal)
            fb += F32DemoteF64
            fb += F64PromoteF32
            fb += LocalGet(doubleValueLocal)
            fb += F64Eq
            fb.ifThenElse(typeDataType) {
              // then it is a Float
              fb += getHijackedClassTypeDataInstr(BoxedFloatClass)
            } {
              // else, if it is NaN
              fb += LocalGet(doubleValueLocal)
              fb += LocalGet(doubleValueLocal)
              fb += F64Ne
              fb.ifThenElse(typeDataType) {
                // then it is a Float
                fb += getHijackedClassTypeDataInstr(BoxedFloatClass)
              } {
                // else, it is a Double
                fb += getHijackedClassTypeDataInstr(BoxedDoubleClass)
              }
            }
          }
          fb += Return
        },
        // case JSValueTypeUndefined => typeDataOf[jl.Void]
        List(JSValueTypeUndefined) -> { () =>
          fb += getHijackedClassTypeDataInstr(BoxedUnitClass)
          fb += Return
        }
      ) { () =>
        // case _ (JSValueTypeOther) => return null
        fb += RefNull(HeapType.None)
        fb += Return
      }

      fb += Unreachable
    }

    /* Now we have one of our objects. Normally we only have to get the
     * vtable, but there are two exceptions. If the value is an instance of
     * `jl.CharacterBox` or `jl.LongBox`, we must use the typeData of
     * `jl.Character` or `jl.Long`, respectively.
     */
    fb += LocalTee(ourObjectLocal)
    fb += RefTest(RefType(genTypeID.forClass(SpecialNames.CharBoxClass)))
    fb.ifThenElse(typeDataType) {
      fb += getHijackedClassTypeDataInstr(BoxedCharacterClass)
    } {
      fb += LocalGet(ourObjectLocal)
      fb += RefTest(RefType(genTypeID.forClass(SpecialNames.LongBoxClass)))
      fb.ifThenElse(typeDataType) {
        fb += getHijackedClassTypeDataInstr(BoxedLongClass)
      } {
        if (targetPureWasm) {
          fb += LocalGet(ourObjectLocal)
          fb += RefTest(RefType(genTypeID.forClass(SpecialNames.IntegerBoxClass)))
          fb.ifThenElse(typeDataType) {
            fb += getHijackedClassTypeDataInstr(BoxedIntegerClass)
          } {
            fb += LocalGet(ourObjectLocal)
            fb += RefTest(RefType(genTypeID.forClass(SpecialNames.FloatBoxClass)))
            fb.ifThenElse(typeDataType) {
              fb += getHijackedClassTypeDataInstr(BoxedFloatClass)
            } {
              fb += LocalGet(ourObjectLocal)
              fb += RefTest(RefType(genTypeID.forClass(SpecialNames.DoubleBoxClass)))
              fb.ifThenElse(typeDataType) {
                fb += getHijackedClassTypeDataInstr(BoxedDoubleClass)
              } {
                fb += LocalGet(ourObjectLocal)
                fb += StructGet(genTypeID.ObjectStruct, genFieldID.objStruct.vtable)
              }
            }
          }
        } else {
          fb += LocalGet(ourObjectLocal)
          fb += StructGet(genTypeID.ObjectStruct, genFieldID.objStruct.vtable)
        }
      }
    }

    fb.buildAndAddToModule()
  }

  /** `identityHashCode`: `anyref -> i32`.
   *
   *  This is the implementation of `IdentityHashCode`. It is also used to compute the `hashCode()`
   *  of primitive values when dispatch is required (i.e., when the receiver type is not known to be
   *  a specific primitive or hijacked class), so it must be consistent with the implementations of
   *  `hashCode()` in hijacked classes.
   *
   *  For `String` and `Double`, we actually call the hijacked class methods, as they are a bit
   *  involved. For `Boolean` and `Void`, we hard-code a copy here.
  */
  private def genIdentityHashCode()(implicit ctx: WasmContext): Unit = {
    import MemberNamespace.Public
    import SpecialNames.hashCodeMethodName
    import genFieldID.typeData._

    // A global exclusively used by this function
    ctx.addGlobal(
      Global(
        genGlobalID.lastIDHashCode,
        OriginalName(genGlobalID.lastIDHashCode.toString()),
        isMutable = true,
        Int32,
        Expr(List(I32Const(0)))
      )
    )

    val fb = newFunctionBuilder(genFunctionID.identityHashCode)
    val objParam = fb.addParam("obj", RefType.anyref)
    fb.setResultType(Int32)

    val objNonNullLocal = fb.addLocal("objNonNull", RefType.any)
    val resultLocal = fb.addLocal("result", Int32)

    // If `obj` is `null`, return 0 (by spec)
    fb.block(RefType.any) { nonNullLabel =>
      fb += LocalGet(objParam)
      fb += BrOnNonNull(nonNullLabel)
      fb += I32Const(0)
      fb += Return
    }

    if (targetPureWasm) {
      // In pure Wasm, the boxed primitives are our objects, and
      // the scalaValueType tests are needed.
      fb += LocalSet(objNonNullLocal)
      fb += I32Const(1)
    } else {
      // If `obj` is one of our objects, skip all the jsValueType tests
      fb += LocalTee(objNonNullLocal)
      fb += RefTest(RefType(genTypeID.ObjectStruct))
      fb += I32Eqz
    }
    fb.ifThen() {
      fb.switch() { () =>
        fb += LocalGet(objNonNullLocal)
        if (targetPureWasm) fb += Call(genFunctionID.scalaValueType)
        else fb += Call(genFunctionID.jsValueType)
      }(
        Seq(
          List(JSValueTypeFalse) -> { () =>
            fb += I32Const(1237) // specified by jl.Boolean.hashCode()
            fb += Return
          },
          List(JSValueTypeTrue) -> { () =>
            fb += I32Const(1231) // specified by jl.Boolean.hashCode()
            fb += Return
          },
          List(JSValueTypeString) -> { () =>
            fb += LocalGet(objNonNullLocal)
            if (!targetPureWasm) fb += ExternConvertAny
            else fb += RefCast(RefType(genTypeID.wasmString))
            fb += Call(
              genFunctionID.forMethod(Public, BoxedStringClass, hashCodeMethodName)
            )
            fb += Return
          },
          List(JSValueTypeNumber) -> { () =>
            fb += LocalGet(objNonNullLocal)
            fb += Call(genFunctionID.unbox(DoubleRef))
            fb += Call(
              genFunctionID.forMethod(Public, BoxedDoubleClass, hashCodeMethodName)
            )
            fb += Return
          },
          List(JSValueTypeUndefined) -> { () =>
            fb += I32Const(0) // specified by jl.Void.hashCode(), Scala.js only
            fb += Return
          }
        ) ++ (if (!targetPureWasm) {
          List(
            List(JSValueTypeBigInt) -> { () =>
              fb += LocalGet(objNonNullLocal)
              fb += Call(genFunctionID.bigintHashCode)
              fb += Return
            },
            List(JSValueTypeSymbol) -> { () =>
              fb.block() { descriptionIsNullLabel =>
                fb += LocalGet(objNonNullLocal)
                fb += Call(genFunctionID.symbolDescription)
                fb += BrOnNull(descriptionIsNullLabel)
                fb += Call(
                  genFunctionID.forMethod(Public, BoxedStringClass, hashCodeMethodName)
                )
                fb += Return
              }
              fb += I32Const(0)
              fb += Return
            }
          )
        } else Nil): _*
      ) { () =>
        // JSValueTypeOther -- fall through to using idHashCodeMap
        ()
      }
    }

    // If we get here, use the idHashCodeMap

    if (!targetPureWasm) {
      // Read the existing idHashCode, if one exists
      fb += GlobalGet(genGlobalID.idHashCodeMap)
      fb += LocalGet(objNonNullLocal)
      fb += Call(genFunctionID.idHashCodeGet)
      fb += LocalTee(resultLocal)

      // If it is 0, there was no recorded idHashCode yet; allocate a new one
      fb += I32Eqz
      fb.ifThen() {
        // Allocate a new idHashCode
        fb += GlobalGet(genGlobalID.lastIDHashCode)
        fb += I32Const(1)
        fb += I32Add
        fb += LocalTee(resultLocal)
        fb += GlobalSet(genGlobalID.lastIDHashCode)

        // Store it for next time
        fb += GlobalGet(genGlobalID.idHashCodeMap)
        fb += LocalGet(objNonNullLocal)
        fb += LocalGet(resultLocal)
        fb += Call(genFunctionID.idHashCodeSet)
      }

      fb += LocalGet(resultLocal)
    } else {
      val jlObjectLocal = fb.addLocal("jlObj", RefType(genTypeID.ObjectStruct))
      // It should be j.l.Object here in pure wasm setting?
      fb += LocalGet(objNonNullLocal)
      fb += RefCast(RefType(genTypeID.ObjectStruct))
      fb += LocalTee(jlObjectLocal)
      fb += StructGet(genTypeID.ObjectStruct, genFieldID.objStruct.idHashCode)
      fb += LocalTee(resultLocal)

      fb += I32Eqz
      fb.ifThen() {
        // Allocate a new idHashCode
        fb += GlobalGet(genGlobalID.lastIDHashCode)
        fb += I32Const(1)
        fb += I32Add
        fb += LocalTee(resultLocal)
        fb += GlobalSet(genGlobalID.lastIDHashCode)

        fb += LocalGet(jlObjectLocal)
        fb += GlobalGet(genGlobalID.lastIDHashCode)
        fb += LocalTee(resultLocal)
        fb += StructSet(genTypeID.ObjectStruct, genFieldID.objStruct.idHashCode)
      }
      fb += LocalGet(resultLocal)
    }

    fb.buildAndAddToModule()
  }

  /** Search for a reflective proxy function with the given `methodId` in the `reflectiveProxies`
   *  field in `typeData` and returns the corresponding function reference.
   *
   *  `searchReflectiveProxy`: [typeData, i32] -> [(ref func)]
   */
  private def genSearchReflectiveProxy()(implicit ctx: WasmContext): Unit = {
    import genFieldID.typeData._

    val typeDataType = RefType(genTypeID.typeData)

    val fb = newFunctionBuilder(genFunctionID.searchReflectiveProxy)
    val typeDataParam = fb.addParam("typeData", typeDataType)
    val methodIDParam = fb.addParam("methodID", Int32)
    fb.setResultType(RefType(HeapType.Func))

    val reflectiveProxies =
      fb.addLocal("reflectiveProxies", Types.RefType(genTypeID.reflectiveProxies))
    val startLocal = fb.addLocal("start", Types.Int32)
    val endLocal = fb.addLocal("end", Types.Int32)
    val midLocal = fb.addLocal("mid", Types.Int32)
    val entryLocal = fb.addLocal("entry", Types.RefType(genTypeID.reflectiveProxy))

    /* This function implements a binary search. Unlike the typical binary search,
     * it does not stop early if it happens to exactly hit the target ID.
     * Instead, it systematically reduces the search range until it contains at
     * most one element. At that point, it checks whether it is the ID we are
     * looking for.
     *
     * We do this in the name of predictability, in order to avoid performance
     * cliffs. It avoids the scenario where a codebase happens to be fast
     * because a particular reflective call resolves in Θ(1), but where adding
     * or removing something completely unrelated somewhere else in the
     * codebase pushes it to a different slot where it resolves in Θ(log n).
     *
     * This function is therefore intentionally Θ(log n), not merely O(log n).
     */

    fb += LocalGet(typeDataParam)
    fb += StructGet(genTypeID.typeData, genFieldID.typeData.reflectiveProxies)
    fb += LocalTee(reflectiveProxies)

    // end := reflectiveProxies.length
    fb += ArrayLen
    fb += LocalSet(endLocal)

    // start := 0
    fb += I32Const(0)
    fb += LocalSet(startLocal)

    // while (start + 1 < end)
    fb.whileLoop() {
      fb += LocalGet(startLocal)
      fb += I32Const(1)
      fb += I32Add
      fb += LocalGet(endLocal)
      fb += I32LtU
    } {
      // mid := (start + end) >>> 1
      fb += LocalGet(startLocal)
      fb += LocalGet(endLocal)
      fb += I32Add
      fb += I32Const(1)
      fb += I32ShrU
      fb += LocalSet(midLocal)

      // if (methodID < reflectiveProxies[mid].methodID)
      fb += LocalGet(methodIDParam)
      fb += LocalGet(reflectiveProxies)
      fb += LocalGet(midLocal)
      fb += ArrayGet(genTypeID.reflectiveProxies)
      fb += StructGet(genTypeID.reflectiveProxy, genFieldID.reflectiveProxy.methodID)
      fb += I32LtU
      fb.ifThenElse() {
        // then end := mid
        fb += LocalGet(midLocal)
        fb += LocalSet(endLocal)
      } {
        // else start := mid
        fb += LocalGet(midLocal)
        fb += LocalSet(startLocal)
      }
    }

    // if (start < end)
    fb += LocalGet(startLocal)
    fb += LocalGet(endLocal)
    fb += I32LtU
    fb.ifThen() {
      // entry := reflectiveProxies[start]
      fb += LocalGet(reflectiveProxies)
      fb += LocalGet(startLocal)
      fb += ArrayGet(genTypeID.reflectiveProxies)
      fb += LocalTee(entryLocal)

      // if (entry.methodID == methodID)
      fb += StructGet(genTypeID.reflectiveProxy, genFieldID.reflectiveProxy.methodID)
      fb += LocalGet(methodIDParam)
      fb += I32Eq
      fb.ifThen() {
        // return entry.funcRef
        fb += LocalGet(entryLocal)
        fb += StructGet(genTypeID.reflectiveProxy, genFieldID.reflectiveProxy.funcRef)
        fb += Return
      }
    }

    // If we get here, we did not find the method; throw.

    if (targetPureWasm) {
      genNewScalaClass(fb, ArrayIndexOutOfBoundsExceptionClass,
          SpecialNames.StringArgConstructorName) {
        fb ++= ctx.stringPool.getConstantStringInstr("Method not found")
      }
      // We need a (ref func) as fake result; any function will do, really.
      genThrow(fb, fakeResult = List(ctx.refFuncWithDeclaration(genFunctionID.searchReflectiveProxy)))
    } else {
      // throw new TypeError("...")
      // Originally, exception is thrown from JS saying e.g. "obj2.z1__ is not a function"
      // TODO Improve the error message to include some information about the missing method
      fb ++= ctx.stringPool.getConstantStringInstr("Method not found")
      fb += Call(genFunctionID.makeTypeError)
      fb += Throw(genTagID.exception)
    }

    fb.buildAndAddToModule()
  }

  private def genArrayCloneFunctions()(implicit ctx: WasmContext): Unit = {
    for (baseRef <- arrayBaseRefs)
      genArrayCloneFunction(baseRef)
  }

  /** Generates the clone function for the array class with the given base. */
  private def genArrayCloneFunction(baseRef: NonArrayTypeRef)(implicit ctx: WasmContext): Unit = {
    val originalName = OriginalName("cloneArray." + charCodeForOriginalName(baseRef))

    val fb = newFunctionBuilder(genFunctionID.cloneArray(baseRef), originalName)
    val fromParam = fb.addParam("from", RefType(genTypeID.ObjectStruct))
    fb.setResultType(RefType(genTypeID.ObjectStruct))
    fb.setFunctionType(genTypeID.cloneFunctionType)

    val arrayTypeRef = ArrayTypeRef(baseRef, 1)

    val arrayStructTypeID = genTypeID.forArrayClass(arrayTypeRef)
    val arrayClassType = RefType(arrayStructTypeID)

    val underlyingArrayTypeID = genTypeID.underlyingOf(arrayTypeRef)
    val underlyingArrayType = RefType(underlyingArrayTypeID)

    val fromLocal = fb.addLocal("fromTyped", arrayClassType)
    val fromUnderlyingLocal = fb.addLocal("fromUnderlying", underlyingArrayType)
    val lengthLocal = fb.addLocal("length", Int32)
    val resultUnderlyingLocal = fb.addLocal("resultUnderlying", underlyingArrayType)

    // Cast down the from argument
    fb += LocalGet(fromParam)
    fb += RefCast(arrayClassType)
    fb += LocalTee(fromLocal)

    // Load the underlying array
    fb += StructGet(arrayStructTypeID, genFieldID.objStruct.arrayUnderlying)
    fb += LocalTee(fromUnderlyingLocal)

    // Make a copy of the underlying array
    fb += ArrayLen
    fb += LocalTee(lengthLocal)
    fb += ArrayNewDefault(underlyingArrayTypeID)
    fb += LocalTee(resultUnderlyingLocal) // also dest for array.copy
    fb += I32Const(0) // destOffset
    fb += LocalGet(fromUnderlyingLocal) // src
    fb += I32Const(0) // srcOffset
    fb += LocalGet(lengthLocal) // length
    fb += ArrayCopy(underlyingArrayTypeID, underlyingArrayTypeID)

    // Build the result arrayStruct
    fb += LocalGet(fromLocal)
    fb += StructGet(arrayStructTypeID, genFieldID.objStruct.vtable) // vtable
    if (targetPureWasm) fb += I32Const(0) // idHashCode
    fb += LocalGet(resultUnderlyingLocal)
    fb += StructNew(arrayStructTypeID)

    fb.buildAndAddToModule()
  }

  private def genArrayCopyFunctions()(implicit ctx: WasmContext): Unit = {
    if (semantics.arrayIndexOutOfBounds != CheckedBehavior.Unchecked)
      genArrayCopyCheckBounds()

    if (semantics.arrayStores != CheckedBehavior.Unchecked)
      genSlowRefArrayCopy()

    for (baseRef <- arrayBaseRefs)
      genSpecializedArrayCopy(baseRef)

    genGenericArrayCopy()
  }

  /** `arrayCopyCheckBounds: [i32, i32, i32, i32, i32] -> []`.
   *
   *  Checks all the bounds for an `arrayCopy`. Arguments correspond to the
   *  arguments of the `arrayCopy`, where arrays are replaced by their lengths.
   */
  private def genArrayCopyCheckBounds()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.arrayCopyCheckBounds)
    val srcLengthParam = fb.addParam("srcLength", Int32)
    val srcPosParam = fb.addParam("srcPos", Int32)
    val destLengthParam = fb.addParam("destLength", Int32)
    val destPosParam = fb.addParam("destPos", Int32)
    val lengthParam = fb.addParam("length", Int32)

    fb.block() { failureLabel =>
      /* if (srcPos < 0) || (destPos < 0) || (length < 0), fail
       * we test all of those with a single branch as follows:
       * ((srcPos | destPos | length) & 0x80000000) != 0
       */
      fb += LocalGet(srcPosParam)
      fb += LocalGet(destPosParam)
      fb += I32Or
      fb += LocalGet(lengthParam)
      fb += I32Or
      fb += I32Const(0x80000000)
      fb += I32And
      fb += BrIf(failureLabel)

      // if srcPos > (srcLength - length), fail
      fb += LocalGet(srcPosParam)
      fb += LocalGet(srcLengthParam)
      fb += LocalGet(lengthParam)
      fb += I32Sub
      fb += I32GtS
      fb += BrIf(failureLabel)

      // if destPos > (destLength - length), fail
      fb += LocalGet(destPosParam)
      fb += LocalGet(destLengthParam)
      fb += LocalGet(lengthParam)
      fb += I32Sub
      fb += I32GtS
      fb += BrIf(failureLabel)

      // otherwise, succeed
      fb += Return
    }

    maybeWrapInUBE(fb, semantics.arrayIndexOutOfBounds) {
      genNewScalaClass(fb, ArrayIndexOutOfBoundsExceptionClass,
          SpecialNames.StringArgConstructorName) {
        if (targetPureWasm)
          fb += RefNull(HeapType(genTypeID.wasmString))
        else
          fb += RefNull(HeapType.NoExtern)
      }
    }
    genThrow(fb, fakeResult = Nil)

    fb.buildAndAddToModule()
  }

  /** `slowRefArrayCopy: [ArrayObject, i32, ArrayObject, i32, i32] -> []`
   *
   *  Used when the type of the dest is not assignable from the type of the source.
   *  Performs an `arraySet` call for every element in order to detect
   *  `ArrayStoreException`s.
   *
   *  Bounds are already known to be valid.
   */
  private def genSlowRefArrayCopy()(implicit ctx: WasmContext): Unit = {
    val baseRef = ClassRef(ObjectClass)
    val arrayTypeRef = ArrayTypeRef(baseRef, 1)
    val arrayStructTypeID = genTypeID.forArrayClass(arrayTypeRef)
    val arrayClassType = RefType.nullable(arrayStructTypeID)
    val underlyingArrayTypeID = genTypeID.underlyingOf(arrayTypeRef)

    val fb = newFunctionBuilder(genFunctionID.slowRefArrayCopy)
    val srcParam = fb.addParam("src", arrayClassType)
    val srcPosParam = fb.addParam("srcPos", Int32)
    val destParam = fb.addParam("dest", arrayClassType)
    val destPosParam = fb.addParam("destPos", Int32)
    val lengthParam = fb.addParam("length", Int32)

    val srcUnderlyingLocal = fb.addLocal("srcUnderlying", RefType(underlyingArrayTypeID))
    val iLocal = fb.addLocal("i", Int32)

    // srcUnderlying := src.underlying
    fb += LocalGet(srcParam)
    fb += StructGet(arrayStructTypeID, genFieldID.objStruct.arrayUnderlying)
    fb += LocalSet(srcUnderlyingLocal)

    // i := 0
    fb += I32Const(0)
    fb += LocalSet(iLocal)

    // while i != length
    fb.whileLoop() {
      fb += LocalGet(iLocal)
      fb += LocalGet(lengthParam)
      fb += I32Ne
    } {
      // arraySet.O(dest, destPos + i, srcUnderlying(srcPos + i))

      fb += LocalGet(destParam)

      fb += LocalGet(destPosParam)
      fb += LocalGet(iLocal)
      fb += I32Add

      fb += LocalGet(srcUnderlyingLocal)
      fb += LocalGet(srcPosParam)
      fb += LocalGet(iLocal)
      fb += I32Add
      fb += ArrayGet(underlyingArrayTypeID)

      fb += Call(genFunctionID.arraySet(baseRef))
      genForwardThrow(fb, fakeResult = Nil)

      // i := i + 1
      fb += LocalGet(iLocal)
      fb += I32Const(1)
      fb += I32Add
      fb += LocalSet(iLocal)
    }

    fb.buildAndAddToModule()
  }

  /** Generates a specialized arrayCopy for the array class with the given base. */
  private def genSpecializedArrayCopy(baseRef: NonArrayTypeRef)(implicit ctx: WasmContext): Unit = {
    val originalName = OriginalName("arrayCopy." + charCodeForOriginalName(baseRef))

    val arrayTypeRef = ArrayTypeRef(baseRef, 1)
    val arrayStructTypeID = genTypeID.forArrayClass(arrayTypeRef)
    val arrayClassType = RefType.nullable(arrayStructTypeID)
    val underlyingArrayTypeID = genTypeID.underlyingOf(arrayTypeRef)

    val fb = newFunctionBuilder(genFunctionID.specializedArrayCopy(arrayTypeRef), originalName)
    val srcParam = fb.addParam("src", arrayClassType)
    val srcPosParam = fb.addParam("srcPos", Int32)
    val destParam = fb.addParam("dest", arrayClassType)
    val destPosParam = fb.addParam("destPos", Int32)
    val lengthParam = fb.addParam("length", Int32)

    if (semantics.arrayIndexOutOfBounds != CheckedBehavior.Unchecked) {
      fb += LocalGet(srcParam)
      fb += StructGet(arrayStructTypeID, genFieldID.objStruct.arrayUnderlying)
      fb += ArrayLen
      fb += LocalGet(srcPosParam)
      fb += LocalGet(destParam)
      fb += StructGet(arrayStructTypeID, genFieldID.objStruct.arrayUnderlying)
      fb += ArrayLen
      fb += LocalGet(destPosParam)
      fb += LocalGet(lengthParam)
      fb += Call(genFunctionID.arrayCopyCheckBounds)
      genForwardThrow(fb, fakeResult = Nil)
    }

    if (baseRef.isInstanceOf[ClassRef] && semantics.arrayStores != CheckedBehavior.Unchecked) {
      // if !isAssignableFrom(dest.vtable, src.vtable)
      fb += LocalGet(destParam)
      fb += StructGet(arrayStructTypeID, genFieldID.objStruct.vtable)
      fb += LocalGet(srcParam)
      fb += StructGet(arrayStructTypeID, genFieldID.objStruct.vtable)
      fb += Call(genFunctionID.isAssignableFrom) // contains a fast-path for `eq` vtables
      fb += I32Eqz
      fb.ifThen() {
        // then, delegate to the slow copy method
        fb += LocalGet(srcParam)
        fb += LocalGet(srcPosParam)
        fb += LocalGet(destParam)
        fb += LocalGet(destPosParam)
        fb += LocalGet(lengthParam)
        fb += ReturnCall(genFunctionID.slowRefArrayCopy)
      }
    }

    fb += LocalGet(destParam)
    fb += StructGet(arrayStructTypeID, genFieldID.objStruct.arrayUnderlying)
    fb += LocalGet(destPosParam)
    fb += LocalGet(srcParam)
    fb += StructGet(arrayStructTypeID, genFieldID.objStruct.arrayUnderlying)
    fb += LocalGet(srcPosParam)
    fb += LocalGet(lengthParam)
    fb += ArrayCopy(underlyingArrayTypeID, underlyingArrayTypeID)

    fb.buildAndAddToModule()
  }

  /** Generates the generic arrayCopy for an unknown array class. */
  private def genGenericArrayCopy()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.genericArrayCopy)
    val srcParam = fb.addParam("src", RefType.anyref)
    val srcPosParam = fb.addParam("srcPos", Int32)
    val destParam = fb.addParam("dest", RefType.anyref)
    val destPosParam = fb.addParam("destPos", Int32)
    val lengthParam = fb.addParam("length", Int32)

    val anyrefToAnyrefBlockType =
      fb.sigToBlockType(FunctionType(List(RefType.anyref), List(RefType.anyref)))

    // note: this block is never used for Unchecked arrayStores, but it does not hurt much
    fb.block(anyref) { mismatchLabel =>
      // Dispatch done based on the type of src
      fb += LocalGet(srcParam)

      for (baseRef <- arrayBaseRefs) {
        val arrayTypeRef = ArrayTypeRef(baseRef, 1)
        val arrayStructTypeID = genTypeID.forArrayClass(arrayTypeRef)
        val nonNullArrayClassType = RefType(arrayStructTypeID)

        fb.block(anyrefToAnyrefBlockType) { notThisArrayTypeLabel =>
          fb += BrOnCastFail(notThisArrayTypeLabel, RefType.anyref, nonNullArrayClassType)

          fb += LocalGet(srcPosParam)
          fb += LocalGet(destParam)
          if (semantics.arrayStores == CheckedBehavior.Unchecked)
            fb += RefCast(nonNullArrayClassType)
          else
            fb += BrOnCastFail(mismatchLabel, anyref, nonNullArrayClassType)
          fb += LocalGet(destPosParam)
          fb += LocalGet(lengthParam)

          fb += ReturnCall(genFunctionID.specializedArrayCopy(arrayTypeRef))
        }
      }
    }

    // Mismatch of array types, or either array was not an array

    if (semantics.arrayStores == CheckedBehavior.Unchecked) {
      fb += Unreachable // trap
    } else {
      maybeWrapInUBE(fb, semantics.arrayStores) {
        genNewScalaClass(fb, ArrayStoreExceptionClass,
            SpecialNames.StringArgConstructorName) {
          if (targetPureWasm)
            fb += RefNull(HeapType(genTypeID.wasmString))
          else
            fb += RefNull(HeapType.NoExtern)
        }
      }
      genThrow(fb, fakeResult = Nil)
    }

    fb.buildAndAddToModule()
  }

  private def genStringConcat()(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm, "stringConcat should be generated only for Wasm only target.")
    val fb = newFunctionBuilder(genFunctionID.wasmString.stringConcat)

    val str1 = fb.addParam("str1", stringType)
    val str2 = fb.addParam("str2", stringType)
    fb.setResultType(stringType)

    // chars
    fb += LocalGet(str2)
    fb += Call(genFunctionID.wasmString.getWholeChars)
    // length
    fb += LocalGet(str1)
    fb += StructGet(genTypeID.wasmString, genFieldID.wasmString.length)
    fb += LocalGet(str2)
    fb += StructGet(genTypeID.wasmString, genFieldID.wasmString.length)
    fb += I32Add
    // left
    fb += LocalGet(str1)
    fb += StructNew(genTypeID.wasmString)

    fb.buildAndAddToModule()
  }

  private def genStringEquals()(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm, "stringEquals should be generated only for Wasm only target.")
    val fb = newFunctionBuilder(genFunctionID.wasmString.stringEquals)
    val str1 = fb.addParam("str1", nullableStringType)
    val str2 = fb.addParam("str2", nullableStringType)
    fb.setResultType(Int32)

    val len1 = fb.addLocal("len1", Int32)
    val len2 = fb.addLocal("len2", Int32)
    val iLocal = fb.addLocal("i", Int32)
    val chars1 = fb.addLocal("chars1", RefType(genTypeID.i16Array))
    val chars2 = fb.addLocal("chars2", RefType(genTypeID.i16Array))

    // Check if both arrays are null
    fb += LocalGet(str1)
    fb += RefIsNull
    fb += LocalGet(str2)
    fb += RefIsNull
    fb += I32And
    fb.ifThen() {
      fb += I32Const(1)
      fb += Return
    }

    // Check if one of the arrays is null
    fb += LocalGet(str1)
    fb += RefIsNull
    fb += LocalGet(str2)
    fb += RefIsNull
    fb += I32Or
    fb.ifThen() {
      fb += I32Const(0)
      fb += Return
    }

    // length
    fb += LocalGet(str1)
    fb += StructGet(genTypeID.wasmString, genFieldID.wasmString.length)
    fb += LocalTee(len1)
    fb += LocalGet(str2)
    fb += StructGet(genTypeID.wasmString, genFieldID.wasmString.length)
    fb += LocalTee(len2)

    // compare length
    fb += I32Ne
    fb.ifThen() {
      fb += I32Const(0)
      fb += Return
    }

    // compare elements
    fb += LocalGet(str1)
    fb += RefAsNonNull
    fb += Call(genFunctionID.wasmString.getWholeChars)
    fb += LocalSet(chars1)

    fb += LocalGet(str2)
    fb += RefAsNonNull
    fb += Call(genFunctionID.wasmString.getWholeChars)
    fb += LocalSet(chars2)

    fb += I32Const(0)
    fb += LocalSet(iLocal)
    fb.whileLoop() {
      fb += LocalGet(iLocal)
      fb += LocalGet(len1)
      fb += I32Ne
    } {
      fb += LocalGet(chars1)
      fb += LocalGet(iLocal)
      fb += ArrayGetU(genTypeID.i16Array)

      fb += LocalGet(chars2)
      fb += LocalGet(iLocal)
      fb += ArrayGetU(genTypeID.i16Array)

      fb += I32Ne
      fb.ifThen() {
        fb += I32Const(0)
        fb += Return
      }

      // i := i + 1
      fb += LocalGet(iLocal)
      fb += I32Const(1)
      fb += I32Add
      fb += LocalSet(iLocal)
    }
    fb += I32Const(1)

    fb.buildAndAddToModule()
  }

  private def genCharCodeAt()(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm, "charCodeAt should be generated only for Wasm only target.")
    val fb = newFunctionBuilder(genFunctionID.wasmString.charCodeAt)

    val strParam = fb.addParam("str", RefType(genTypeID.wasmString))
    val idxParam = fb.addParam("idx", Int32)

    fb.setResultType(Int32)

    fb += LocalGet(strParam)
    fb += Call(genFunctionID.wasmString.getWholeChars)
    fb += LocalGet(idxParam)
    fb += ArrayGetU(genTypeID.i16Array)

    fb.buildAndAddToModule()
  }

  private def genGetWholeChars()(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm, "getWholeChars should be generated only for Wasm only target.")
    val fb = newFunctionBuilder(genFunctionID.wasmString.getWholeChars)
    val strParam = fb.addParam("str", RefType(genTypeID.wasmString))

    fb.setResultType(RefType(genTypeID.i16Array))

    fb += LocalGet(strParam)
    fb += StructGet(genTypeID.wasmString, genFieldID.wasmString.left)
    fb += RefIsNull
    fb += I32Eqz
    fb.ifThen() {
      fb += LocalGet(strParam)
      fb += Call(genFunctionID.wasmString.collapseString)
    }

    fb += LocalGet(strParam)
    fb += StructGet(genTypeID.wasmString, genFieldID.wasmString.chars)

    fb.buildAndAddToModule()
  }

  private def genCollapseString()(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm, "collapseString should be generated only for Wasm only target.")
    val fb = newFunctionBuilder(genFunctionID.wasmString.collapseString)
    val strParam = fb.addParam("str", RefType(genTypeID.wasmString))

    // Copy destination array
    val newArray = fb.addLocal("newArray", RefType(genTypeID.i16Array))
    // Length of whole string
    val stringLen = fb.addLocal("stringLength", Int32)

    val currentString = fb.addLocal("currentString", RefType.nullable(genTypeID.wasmString))
    val currentChars = fb.addLocal("currentChars", RefType(genTypeID.i16Array))
    val currentCharsLen = fb.addLocal("currentCharsLen", Int32)
    val currentIdx = fb.addLocal("currentIdx", Int32)

    // currentString := strParam
    // stringLen := strParam.length
    // currentIdx := stringLen
    // newArray := array.new_default(stringLen)
    fb += LocalGet(strParam)
    fb += LocalTee(currentString)
    fb += StructGet(genTypeID.wasmString, genFieldID.wasmString.length)
    fb += LocalTee(stringLen)
    fb += LocalTee(currentIdx)
    fb += ArrayNewDefault(genTypeID.i16Array)
    fb += LocalSet(newArray)

    fb.loop() { loopLabel =>
      // currentIdx = currentIdx - currentString.chars.length
      fb += LocalGet(currentIdx)
      fb += LocalGet(currentString)
      fb += StructGet(genTypeID.wasmString, genFieldID.wasmString.chars)
      fb += LocalTee(currentChars)
      fb += ArrayLen
      fb += LocalTee(currentCharsLen)
      fb += I32Sub
      fb += LocalSet(currentIdx)

      // copy chars
      fb += LocalGet(newArray) // dest
      fb += LocalGet(currentIdx) // dest_offset
      fb += LocalGet(currentChars) // src
      fb += I32Const(0) // src_offset
      fb += LocalGet(currentCharsLen) // size
      fb += ArrayCopy(genTypeID.i16Array, genTypeID.i16Array)

      // currentString := currentString.left
      // currentString is not null -> loop
      fb += LocalGet(currentString)
      fb += StructGet(genTypeID.wasmString, genFieldID.wasmString.left)
      fb += LocalTee(currentString)
      fb += RefIsNull
      fb += I32Eqz
      fb += BrIf(loopLabel)
    }

    fb += LocalGet(strParam)
    fb += LocalGet(newArray)
    fb += StructSet(genTypeID.wasmString, genFieldID.wasmString.chars)

    fb += LocalGet(strParam)
    fb += RefNull(HeapType(genTypeID.wasmString))
    fb += StructSet(genTypeID.wasmString, genFieldID.wasmString.left)

    fb.buildAndAddToModule()
  }

  private def genUndefinedAndIsUndef()(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm)

    ctx.mainRecType.addSubType(
      genTypeID.undefined,
      OriginalName(genTypeID.undefined.toString()),
      StructType(Nil)
    )
    ctx.addGlobal(
      Global(
        genGlobalID.undef,
        OriginalName(genGlobalID.undef.toString()),
        isMutable = false,
        RefType(genTypeID.undefined),
        Expr(List(StructNew(genTypeID.undefined)))
      )
    )

    val fb = newFunctionBuilder(genFunctionID.isUndef)
    val xParam = fb.addParam("x", RefType.anyref)
    fb.setResultType(Int32)
    fb += LocalGet(xParam)
    fb += RefTest(RefType(genTypeID.undefined))
    fb.buildAndAddToModule()
  }

  // TODO: https://262.ecma-international.org/#sec-numeric-types-number-remainder
  private def genNaiveFmod()(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm)
    // f32.fmod
    locally {
      val fb = newFunctionBuilder(genFunctionID.f32Fmod)
      val x = fb.addParam("x", Float32)
      val y = fb.addParam("y", Float32)
      fb.setResultType(Float32)

      fb += LocalGet(x)
      fb += LocalGet(y)
      fb += F32Div
      fb += F32Floor
      fb += LocalGet(y)
      fb += F32Mul
      fb += LocalGet(x)
      fb += F32Sub

      fb.buildAndAddToModule()
    }

    // f64.fmod
    locally {
      val fb = newFunctionBuilder(genFunctionID.f64Fmod)
      val x = fb.addParam("x", Float64)
      val y = fb.addParam("y", Float64)
      fb.setResultType(Float64)

      fb += LocalGet(x)
      fb += LocalGet(y)
      fb += F64Div
      fb += F64Floor
      fb += LocalGet(y)
      fb += F64Mul
      fb += LocalGet(x)
      fb += F64Sub

      fb.buildAndAddToModule()
    }
  }

  private def genLtoa()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.ltoa)
    val value = fb.addParam("value", Int64)
    fb.setResultType(stringType)

    val isNegative = fb.addLocal("isNegative", Int32)
    val arrayLen = fb.addLocal("arrayLen", Int32)
    val tmp = fb.addLocal("tmp", Int64)
    val iLocal = fb.addLocal("i", Int32)
    val result = fb.addLocal("result", RefType(genTypeID.i16Array))

    fb += LocalGet(value)
    fb += I64Eqz
    fb.ifThen() {
      fb += I32Const('0'.toInt)
      if (targetPureWasm) {
        SWasmGen.genWasmStringFromCharCode(fb)
      } else {
        fb += Call(genFunctionID.stringBuiltins.fromCharCode)
      }
      fb += Return
    }

    fb += LocalGet(value)
    fb += I64Const(0L)
    fb += I64LtS
    fb.ifThenElse(Int32) { // length of the sign prefix (0 or 1)
      fb += I64Const(0L)
      fb += LocalGet(value)
      fb += I64Sub
      fb += LocalSet(value)

      fb += I32Const(1)
      fb += LocalSet(isNegative)

      fb += I32Const(1) // for '-'
    } {
      fb += I32Const(0)
      fb += LocalSet(isNegative)

      fb += I32Const(0)
    }
    fb += LocalSet(arrayLen)

    // from now on, interpret value and tmp as *unsigned* 64-bit integers (to handle MinValue)

    // calculate the resulting array length
    fb += LocalGet(value)
    fb += LocalSet(tmp)
    fb.loop() { loop =>
      fb += LocalGet(tmp)
      fb += I64Eqz
      fb.ifThenElse() {
        // break
      } {
        // tmp = tmp / 10L
        fb += LocalGet(tmp)
        fb += I64Const(10L)
        fb += I64DivU
        fb += LocalSet(tmp)

        fb += LocalGet(arrayLen)
        fb += I32Const(1)
        fb += I32Add
        fb += LocalSet(arrayLen)
        fb += Br(loop)
      }
    }

    fb += LocalGet(arrayLen)
    fb += ArrayNewDefault(genTypeID.i16Array)
    fb += LocalSet(result)

    // now, fill the array from the last index
    fb += LocalGet(value)
    fb += LocalSet(tmp)

    fb += LocalGet(arrayLen)
    fb += I32Const(1)
    fb += I32Sub
    fb += LocalSet(iLocal)

    fb.loop() { loop =>
      fb += LocalGet(tmp)
      fb += I64Eqz
      fb.ifThenElse() {
      } {
        // array store: result[i] = (tmp % 10L).toInt + '0'.toInt
        fb += LocalGet(result)
        fb += LocalGet(iLocal)

        fb += LocalGet(tmp)
        fb += I64Const(10)
        fb += I64RemU
        fb += I32WrapI64
        fb += I32Const('0'.toInt)
        fb += I32Add
        fb += ArraySet(genTypeID.i16Array)

        // iLocal = iLocal - 1
        fb += LocalGet(iLocal)
        fb += I32Const(1)
        fb += I32Sub
        fb += LocalSet(iLocal)
        // tmp = tmp / 10L
        fb += LocalGet(tmp)
        fb += I64Const(10L)
        fb += I64DivU
        fb += LocalSet(tmp)

        fb += Br(loop)
      }
    }

    // If the number was negative, add the '-' sign
    fb += LocalGet(isNegative)
    fb.ifThen() {
      // Store '-' at the start of the array
      fb += LocalGet(result)
      fb += I32Const(0)
      fb += I32Const('-'.toInt)
      fb += ArraySet(genTypeID.i16Array)
    }
    SWasmGen.genWasmStringFromArray(fb, result)

    fb.buildAndAddToModule()
  }

  private def genItoa()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.itoa)
    val value = fb.addParam("value", Int32)
    fb.setResultType(stringType)

    fb += LocalGet(value)
    fb += I64ExtendI32S
    fb += ReturnCall(genFunctionID.ltoa)

    fb.buildAndAddToModule()
  }

  private def genHijackedValueToString()(implicit ctx: WasmContext): Unit = {
    val fb = newFunctionBuilder(genFunctionID.hijackedValueToString)
    val value = fb.addParam("value", anyref)
    fb.setResultType(stringType)

    fb.block(RefType(genTypeID.wasmString)) { labelString =>
      fb.block(RefType.i31) { labelI31 =>
        fb += LocalGet(value)
        fb += BrOnCast(labelI31, anyref, RefType.i31)
        fb += BrOnCast(labelString, anyref, RefType(genTypeID.wasmString))

        // if none of the above, it must be null
        fb ++= ctx.stringPool.getConstantStringInstr("null")
        fb += Return
      } // end block of labelI31

      fb += I31GetS
      fb += Call(genFunctionID.itoa)
    }

    fb.buildAndAddToModule
  }

  /** Get the type of the given Wasm type of `x` without JS,
    * the return value should be compatible with jsValueType.
    */
  private def genScalaValueType()(implicit ctx: WasmContext): Unit = {
    assert(targetPureWasm)
    val fb = newFunctionBuilder(genFunctionID.scalaValueType)
    val xParam = fb.addParam("x", RefType.any)
    fb.setResultType(Int32)

    fb += LocalGet(xParam)
    fb += Call(genFunctionID.typeTest(DoubleRef))
    fb.ifThenElse(Int32) {
      fb += I32Const(JSValueTypeNumber)
    } {
      fb += LocalGet(xParam)
      fb += RefTest(RefType(genTypeID.wasmString))
      fb.ifThenElse(Int32) {
        fb += I32Const(JSValueTypeString)
      } {
        fb += LocalGet(xParam)
        fb += Call(genFunctionID.typeTest(BooleanRef))
        fb.ifThenElse(Int32) {
          fb += LocalGet(xParam)
          fb += Call(genFunctionID.unbox(BooleanRef))
        } {
          fb += LocalGet(xParam)
          fb += Call(genFunctionID.isUndef)
          fb.ifThenElse(Int32) {
            fb += I32Const(JSValueTypeUndefined)
          } {
            fb += I32Const(JSValueTypeOther)
          }
          // bigint and symbol?
        }
      }
    }
    fb.buildAndAddToModule()
  }

  // (func (param $originalPtr i32)
  //       (param $originalSize i32)
  //       (param $alignment i32)
  //       (param $newSize i32)
  //       (result i32))
  private def genRealloc()(implicit ctx: WasmContext): Unit = {
    assert(true /*isWASI*/) // scalastyle:ignore
    val fb = newFunctionBuilder(genFunctionID.realloc)
    val originalPtr = fb.addParam("originalPtr", Int32)
    val originalSize = fb.addParam("originalSize", Int32)
    val _alignment = fb.addParam("alignment", Int32)
    val newSize = fb.addParam("newSize", Int32)
    fb.setResultType(Int32)

    val newPtr = fb.addLocal("newPtr", Int32)

    // $originalPtr == 0 && originalSzie == 0
    fb += LocalGet(originalPtr)
    fb += I32Const(0)
    fb += I32Eq
    fb += LocalGet(originalSize)
    fb += I32Const(0)
    fb += I32Eq
    fb += I32And
    fb.ifThen() {
      fb += LocalGet(newSize)
      fb += Call(genFunctionID.malloc)
      fb += Return
    }

    fb += LocalGet(newSize)
    fb += LocalGet(originalSize)
    fb += I32LeU
    fb.ifThen() { // newSize <= originalSize
      // TODO?
      // For a typical `realloc`, when `newSize` is smaller than `originalSize`,
      // it is desirable to shrink the allocated memory segment and free the excess memory.
      //
      // However, since we use memory only in the context of the component model
      // and it has a short-lived nature where memory is freed once the function call ends,
      // the benefits of shrinking may be minimal.
      //
      // Additionally, in our implementation,
      // instead of allocating a new memory space when shrinking,
      // we need to reuse the existing memory segment. For details,
      // see `genFunctionID.malloc`.
      fb += LocalGet(originalPtr)
      fb += Return
    }



    fb += LocalGet(newSize)
    fb += Call(genFunctionID.malloc)
    fb += LocalTee(newPtr)
    fb += LocalGet(originalPtr)

    // copy size
    // if originalSize < newSize { originalSize } else { newSize }
    fb += LocalGet(originalSize)
    fb += LocalGet(newSize)
    fb += I32LeS
    fb.ifThenElse(Int32) {
      fb += LocalGet(originalSize)
    } {
      fb += LocalGet(newSize)
    }

    // destination address
    // source address
    // copy size
    fb += MemoryCopy(genMemoryID.memory, genMemoryID.memory)

    // no need to free original pointer, that should be freed after function return

    fb += LocalGet(newPtr)

    fb.buildAndAddToModule()
  }

  private def maybeWrapInUBE(fb: FunctionBuilder, behavior: CheckedBehavior)(
      genExceptionInstance: => Unit): Unit = {
    if (behavior == CheckedBehavior.Fatal) {
      genNewScalaClass(fb, SpecialNames.UndefinedBehaviorErrorClass,
          SpecialNames.ThrowableArgConsructorName) {
        genExceptionInstance
      }
    } else {
      genExceptionInstance
    }
  }

  private def genNewScalaClass(fb: FunctionBuilder, cls: ClassName, ctor: MethodName)(
      genCtorArgs: => Unit): Unit = {
    val instanceLocal = fb.addLocal(NoOriginalName, RefType(genTypeID.forClass(cls)))

    fb += Call(genFunctionID.newDefault(cls))
    fb += LocalTee(instanceLocal)
    genCtorArgs
    fb += Call(genFunctionID.forMethod(MemberNamespace.Constructor, cls, ctor))

    /* In theory the constructor can throw; but the ones we call really shouldn't.
     * That's fortunate, because we don't have access to the enclosing function's
     * result type for the fakeResult, in this context.
     */

    fb += LocalGet(instanceLocal)
  }

  private def genStringConcat(fb: FunctionBuilder): Unit = {
    if (targetPureWasm) fb += Call(genFunctionID.wasmString.stringConcat)
    else fb += Call(genFunctionID.stringBuiltins.concat)
  }

}
