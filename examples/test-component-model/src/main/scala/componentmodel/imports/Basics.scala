package componentmodel.imports

import scala.scalajs.{component => cm}
import scala.scalajs.component.annotation._
import scala.scalajs.component.unsigned._

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