package componentmodel

import scala.scalajs.js
import scala.scalajs.{component => cm}
import scala.scalajs.component._
import scala.scalajs.component.annotation._
import scala.scalajs.component.unsigned._

import java.util.Optional

object Tests {

  @ComponentFlags
  final case class F1(value: Int) extends AnyVal {
    def |(other: F1): F1 = F1(value | other.value)
    def &(other: F1): F1 = F1(value & other.value)
    def ^(other: F1): F1 = F1(value ^ other.value)
    def unary_~ : F1 = F1(~value)
    def contains(other: F1): Boolean = (value & other.value) == other.value
  }
  object F1 {
    val b0 = F1(1 << 0)
    val b1 = F1(1 << 1)
    val b2 = F1(1 << 2)
    val b3 = F1(1 << 3)
    val b4 = F1(1 << 4)
    val b5 = F1(1 << 5)
    val b6 = F1(1 << 6)
    val b7 = F1(1 << 7)
  }

  @ComponentFlags
  final case class F2(value: Int) extends AnyVal {
    def |(other: F2): F2 = F2(value | other.value)
    def &(other: F2): F2 = F2(value & other.value)
    def ^(other: F2): F2 = F2(value ^ other.value)
    def unary_~ : F2 = F2(~value)
    def contains(other: F2): Boolean = (value & other.value) == other.value
  }
  object F2 {
    val b0  = F2(1 << 0)
    val b1  = F2(1 << 1)
    val b2  = F2(1 << 2)
    val b3  = F2(1 << 3)
    val b4  = F2(1 << 4)
    val b5  = F2(1 << 5)
    val b6  = F2(1 << 6)
    val b7  = F2(1 << 7)
    val b8  = F2(1 << 8)
    val b9  = F2(1 << 9)
    val b10 = F2(1 << 10)
    val b11 = F2(1 << 11)
    val b12 = F2(1 << 12)
    val b13 = F2(1 << 13)
    val b14 = F2(1 << 14)
    val b15 = F2(1 << 15)
  }

  @ComponentFlags
  final case class F3(value: Int) extends AnyVal {
    def |(other: F3): F3 = F3(value | other.value)
    def &(other: F3): F3 = F3(value & other.value)
    def ^(other: F3): F3 = F3(value ^ other.value)
    def unary_~ : F3 = F3(~value)
    def contains(other: F3): Boolean = (value & other.value) == other.value
  }
  object F3 {
    val b0  = F3(1 << 0)
    val b1  = F3(1 << 1)
    val b2  = F3(1 << 2)
    val b3  = F3(1 << 3)
    val b4  = F3(1 << 4)
    val b5  = F3(1 << 5)
    val b6  = F3(1 << 6)
    val b7  = F3(1 << 7)
    val b8  = F3(1 << 8)
    val b9  = F3(1 << 9)
    val b10 = F3(1 << 10)
    val b11 = F3(1 << 11)
    val b12 = F3(1 << 12)
    val b13 = F3(1 << 13)
    val b14 = F3(1 << 14)
    val b15 = F3(1 << 15)
    val b16 = F3(1 << 16)
    val b17 = F3(1 << 17)
    val b18 = F3(1 << 18)
    val b19 = F3(1 << 19)
    val b20 = F3(1 << 20)
    val b21 = F3(1 << 21)
    val b22 = F3(1 << 22)
    val b23 = F3(1 << 23)
    val b24 = F3(1 << 24)
    val b25 = F3(1 << 25)
    val b26 = F3(1 << 26)
    val b27 = F3(1 << 27)
    val b28 = F3(1 << 28)
    val b29 = F3(1 << 29)
    val b30 = F3(1 << 30)
    val b31 = F3(1 << 31)
  }

  @ComponentRecord
  final case class Point(x: Int, y: Int)

  @ComponentVariant
  sealed trait C1
  object C1 {
    final case class A(value: Int) extends C1
    final case class B(value: Float) extends C1
  }

  @ComponentVariant
  sealed trait Z1
  object Z1 {
    final case class A(value: Int) extends Z1
    final case object B extends Z1
  }

  @ComponentVariant
  sealed trait E1
  object E1 {
    case object A extends E1
    case object B extends E1
    case object C extends E1
  }

}