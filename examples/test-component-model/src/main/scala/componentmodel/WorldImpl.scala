package componentmodel

import scala.scalajs.{component => cm}
import scala.scalajs.component.annotation._
import scala.scalajs.component.unsigned._

import componentmodel.imports.Basics._
import componentmodel.imports.Tests._
import componentmodel.imports.Countable._
import componentmodel.Tests._

import scala.scalajs.ComponentUtils.toEither

import java.util.Optional

@ComponentImplementation
object WorldImpl extends World {

  override def roundtripBasics1(a: cm.Tuple9[UByte, Byte, UShort, Short, UInt, Int, Float, Double, Char]):
      cm.Tuple9[UByte, Byte, UShort, Short, UInt, Int, Float, Double, Char] = a

  override def roundtripListU16(a: Array[UShort]): Array[UShort] = a

  override def roundtripListPoint(a: Array[Point]): Array[Point] = a

  override def roundtripListVariant(a: Array[C1]): Array[C1] = a

  override def roundtripString(a: String): String = a

  override def roundtripPoint(a: Point): Point = a

  override def roundtripC1(a: C1): C1 = a

  override def roundtripZ1(a: Z1): Z1 = a

  override def testC1(a: C1): Unit = {}

  override def roundtripEnum(a: E1): E1 = a

  override def roundtripTuple(a: cm.Tuple2[C1, Z1]): cm.Tuple2[C1, Z1] = a

  override def roundtripOption(a: Optional[String]): Optional[String] = a

  override def roundtripDoubleOption(a: Optional[Optional[String]]): Optional[Optional[String]] = a

  override def roundtripResult(a: cm.Result[Unit, Unit]): cm.Result[Unit, Unit] = a

  override def roundtripStringError(a: cm.Result[Float, String]): cm.Result[Float, String] = a

  override def roundtripEnumError(a: cm.Result[C1, E1]): cm.Result[C1, E1] = a

  override def roundtripF1(a: F1): F1 = a

  override def roundtripF2(a: F2): F2 = a

  override def roundtripF3(a: F3): F3 = a

  override def roundtripFlags(a: cm.Tuple2[F1, F1]): cm.Tuple2[F1, F1] = a

  override def roundtripTuple2(a: cm.Tuple2[Int, String]): cm.Tuple2[Int, String] = a

  override def roundtripTuple3(a: cm.Tuple3[Int, String, Boolean]): cm.Tuple3[Int, String, Boolean] = a

  override def run(): Unit = {

    val start = System.currentTimeMillis()
    assert(1 == roundtripU8(1))
    assert(0 == roundtripS8(0))
    assert(0 == roundtripU16(0))
    assert(128 == roundtripS16(128))
    assert(0 == roundtripU32(0))
    assert(30 == roundtripS32(30))
    assert(30 == roundtripU64(30))
    assert(30 == roundtripS64(30))

    assert(0.0f == roundtripF32(0.0f))
    assert(0.0 == roundtripF64(0.0))

    assert('a' == roundtripChar('a'))

    val basics1Input = (127.asInstanceOf[UByte], 127.toByte, 32767.asInstanceOf[UShort], 32767.toShort, 532423, 2147483647, 0.0f, 0.0, 'x')
    val basics1Result = roundtripBasics1(basics1Input)
    assert(basics1Result._1 == 127)
    assert(basics1Result._2 == 127)
    assert(basics1Result._3 == 32767)
    assert(basics1Result._4 == 32767)
    assert(basics1Result._5 == 532423)
    assert(basics1Result._6 == 2147483647)
    assert(basics1Result._7 == 0.0f)
    assert(basics1Result._8 == 0.0)
    assert(basics1Result._9 == 'x')

    val arr = Array[UShort](0, 1, 2)
    assert(arr.sameElements(roundtripListU16(arr)))

    val arr2 = Array[Point](Point(0, 0), Point(3, 0))
    assert(arr2.sameElements(roundtripListPoint(arr2)))

    val arr3 = Array[C1](C1.A(0), C1.B(3))
    assert(arr3.sameElements(roundtripListVariant(arr3)))

    assert("foo" == roundtripString("foo"))
    assert("" == roundtripString(""))
    assert(Point(0, 5) == roundtripPoint(Point(0, 5)))

    testC1(C1.A(5))
    assert(C1.A(4) == roundtripC1(C1.A(4)))
    assert(C1.B(0.0f) == roundtripC1(C1.B(0.0f)))
    assert(Z1.A(0) == roundtripZ1(Z1.A(0)))
    assert(Z1.A(100) == roundtripZ1(Z1.A(100)))
    assert(Z1.B == roundtripZ1(Z1.B))

    assert(E1.A == roundtripEnum(E1.A))
    assert(E1.B == roundtripEnum(E1.B))
    assert(E1.C == roundtripEnum(E1.C))

    val tupleResult1: (C1, Z1) = roundtripTuple((C1.A(5), Z1.A(500)))
    assert(tupleResult1 == (C1.A(5), Z1.A(500)))

    val tupleResult2: (C1, Z1) = roundtripTuple((C1.B(200.0f), Z1.B))
    assert(tupleResult2 == (C1.B(200.0f), Z1.B))

    val tupleResult3: (C1, Z1) = roundtripTuple((C1.A(4), Z1.B))
    assert(tupleResult3 == (C1.A(4), Z1.B))

    assert(Optional.of("ok") == roundtripOption(Optional.of("ok")))
    assert(Optional.empty == roundtripOption(Optional.empty[String]))
    assert(Optional.of(Optional.of("foo")) == roundtripDoubleOption(Optional.of(Optional.of("foo"))))
    assert(Optional.of(Optional.empty) == roundtripDoubleOption(Optional.of(Optional.empty[String])))
    assert(Optional.empty == roundtripDoubleOption(Optional.empty[Optional[String]]))
    // assert(new cm.Err("aaa") != new cm.Err("bbb"))

    assert(new cm.Ok(()) == roundtripResult(new cm.Ok(())))
    assert(new cm.Err(()) == roundtripResult(new cm.Err(())))
    assert(new cm.Ok(3.0f) == roundtripStringError(new cm.Ok(3.0f)))
    assert(new cm.Err("err") == roundtripStringError(new cm.Err("err")))
    assert(new cm.Ok(C1.A(432)) == roundtripEnumError(new cm.Ok(C1.A(432))))
    assert(new cm.Ok(C1.B(0.0f)) == roundtripEnumError(new cm.Ok(C1.B(0.0f))))
    assert(new cm.Err(E1.A) == roundtripEnumError(new cm.Err(E1.A)))
    assert(new cm.Err(E1.B) == roundtripEnumError(new cm.Err(E1.B)))
    assert(new cm.Err(E1.C) == roundtripEnumError(new cm.Err(E1.C)))

    assert((F1.b3 | F1.b6 | F1.b7) == roundtripF1(F1.b3 | F1.b6 | F1.b7))
    assert((F2.b7 | F2.b8 | F2.b15) == roundtripF2(F2.b7 | F2.b8 | F2.b15))
    assert((F3.b7 | F3.b8 | F3.b15 | F3.b31) == roundtripF3(F3.b7 | F3.b8 | F3.b15 | F3.b31))

    val flagsTuple = new cm.Tuple2(F1.b3 | F1.b6, F1.b2 | F1.b3 | F1.b7)
    val flagsResult = roundtripFlags(flagsTuple)
    assert(flagsResult._1 == (F1.b3 | F1.b6))
    assert(flagsResult._2 == (F1.b2 | F1.b3 | F1.b7))

    val result2: (Int, String) = roundtripTuple2((42, "hello"))
    assert(result2 == (123, "world"))

    val result3: (Int, String, Boolean) = roundtripTuple3((123, "world", true))
    assert(result3 == (123, "world", true))

    // Test resource wrappers
    locally {
      val successResult = toEither(tryCreateCounter(10))
      assert(successResult.isRight)
      assert(10 == successResult.right.get.valueOf())

      val errorResult = toEither(tryCreateCounter(-5))
      assert(errorResult.isLeft)

      val maybeCounter = maybeGetCounter()
      assert(maybeCounter.isPresent)
      assert(42 == maybeCounter.get().valueOf())
    }

    locally {
      val c1 = Counter(0)
      c1.up()
      assert(1 == c1.valueOf())

      val c2 = Counter(100)
      c2.down()
      assert(99 == c2.valueOf())

      val s1 = Counter.sum(c1, c2)
      val s2 = Counter.sum(c1, c2)
      assert(s1.valueOf() == s2.valueOf())
      assert(100 == s1.valueOf())

      // use c1 multiple times fails with
      // unknown handle index 1 (?)
      assert(100 == Counter.sum(c1, c2).valueOf())
    }
    val end = System.currentTimeMillis()
    println(s"elapsed: ${(end - start).toInt} ms")
  }
}