package componentmodel

import scala.scalajs.{component => cm}
import scala.scalajs.component.annotation._
import scala.scalajs.component.unsigned._

import java.util.Optional

object Basics {
 @ComponentImport("component:testing/basics", "roundtrip-u8")
 def roundtripU8(a: UByte): UByte = cm.native

  @ComponentImport("component:testing/basics", "roundtrip-s8")
  def roundtripS8(a: Byte): Byte = cm.native

  @ComponentImport("component:testing/basics", "roundtrip-u16")
  def roundtripU16(a: UShort): UShort = cm.native

  @ComponentImport("component:testing/basics", "roundtrip-s16")
  def roundtripS16(a: Short): Short = cm.native

  @ComponentImport("component:testing/basics", "roundtrip-u32")
  def roundtripU32(a: UInt): UInt = cm.native

  @ComponentImport("component:testing/basics", "roundtrip-s32")
  def roundtripS32(a: Int): Int = cm.native

  @ComponentImport("component:testing/basics", "roundtrip-u64")
  def roundtripU64(a: ULong): ULong = cm.native

  @ComponentImport("component:testing/basics", "roundtrip-s64")
  def roundtripS64(a: Long): Long = cm.native

  @ComponentImport("component:testing/basics", "roundtrip-f32")
  def roundtripF32(a: Float): Float = cm.native

  @ComponentImport("component:testing/basics", "roundtrip-f64")
  def roundtripF64(a: Double): Double = cm.native

  @ComponentImport("component:testing/basics", "roundtrip-char")
  def roundtripChar(a: Char): Char = cm.native
}


object Countable {
  @ComponentResourceImport("component:testing/countable", "counter")
  trait Counter {
    @ComponentResourceMethod("up")
    def up(): Unit = cm.native

    @ComponentResourceMethod("down")
    def down(): Unit = cm.native

    @ComponentResourceMethod("value-of")
    def valueOf(): Int = cm.native

    @ComponentResourceDrop
    def close(): Unit = cm.native
  }
  object Counter {
    @ComponentResourceConstructor
    def apply(i: Int): Counter = cm.native

    @ComponentResourceStaticMethod("sum")
    def sum(a: Counter, b: Counter): Counter = cm.native
  }
}

import TestImportsHelper._
object Tests {
  @ComponentImport("component:testing/tests", "roundtrip-basics1")
  def roundtripBasics1(a: (UByte, Byte, UShort, Short, UInt, Int, Float, Double, Char)):
      (UByte, Byte, UShort, Short, UInt, Int, Float, Double, Char) = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-list-u16")
  def roundtripListU16(a: Array[UShort]): Array[UShort] = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-list-point")
  def roundtripListPoint(a: Array[Point]): Array[Point] = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-list-variant")
  def roundtripListVariant(a: Array[C1]): Array[C1] = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-string")
  def roundtripString(a: String): String = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-point")
  def roundtripPoint(a: Point): Point = cm.native

  @ComponentImport("component:testing/tests", "test-c1")
  def testC1(a: C1): Unit = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-c1")
  def roundtripC1(a: C1): C1 = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-z1")
  def roundtripZ1(a: Z1): Z1 = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-enum")
  def roundtripEnum(a: E1): E1 = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-tuple")
  def roundtripTuple(a: (C1, Z1)): (C1, Z1) = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-option")
  def roundtripOption(a: Optional[String]): Optional[String] = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-double-option")
  def roundtripDoubleOption(a: Optional[Optional[String]]): Optional[Optional[String]] = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-result")
  def roundtripResult(a: cm.Result[Unit, Unit]): cm.Result[Unit, Unit] = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-string-error")
  def roundtripStringError(a: cm.Result[Float, String]): cm.Result[Float, String] = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-enum-error")
  def roundtripEnumError(a: cm.Result[C1, E1]): cm.Result[C1, E1] = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-f8")
  def roundtripF8(a: F1): F1 = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-flags")
  def roundtripFlags(a: (F1, F1)): (F1, F1) = cm.native
}
