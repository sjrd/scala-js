package componentmodel.imports

import scala.scalajs.{component => cm}
import scala.scalajs.component.annotation._
import scala.scalajs.component.unsigned._

import java.util.Optional

import componentmodel.Tests._

object Tests {
  @ComponentImport("component:testing/tests", "roundtrip-basics1")
  def roundtripBasics1(a: cm.Tuple9[UByte, Byte, UShort, Short, UInt, Int, Float, Double, Char]):
      cm.Tuple9[UByte, Byte, UShort, Short, UInt, Int, Float, Double, Char] = cm.native

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
  def roundtripTuple(a: cm.Tuple2[C1, Z1]): cm.Tuple2[C1, Z1] = cm.native

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
  def roundtripFlags(a: cm.Tuple2[F1, F1]): cm.Tuple2[F1, F1] = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-tuple2")
  def roundtripTuple2(a: cm.Tuple2[Int, String]): cm.Tuple2[Int, String] = cm.native

  @ComponentImport("component:testing/tests", "roundtrip-tuple3")
  def roundtripTuple3(a: cm.Tuple3[Int, String, Boolean]): cm.Tuple3[Int, String, Boolean] = cm.native
}