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

package java.lang

import scala.annotation.{switch, tailrec}

import java.lang.constant.{Constable, ConstantDesc}

import scala.scalajs.js

/* This is a hijacked class. Its instances are the representation of scala.Longs.
 * Constructors are not emitted.
 */
final class Long private ()
    extends Number with Comparable[Long] with Constable with ConstantDesc {

  def this(value: scala.Long) = this()
  def this(s: String) = this()

  @inline def longValue(): scala.Long =
    this.asInstanceOf[scala.Long]

  @inline override def byteValue(): scala.Byte = longValue().toByte
  @inline override def shortValue(): scala.Short = longValue().toShort
  @inline def intValue(): scala.Int = longValue().toInt
  @inline def floatValue(): scala.Float = longValue().toFloat
  @inline def doubleValue(): scala.Double = longValue().toDouble

  @inline override def equals(that: Any): scala.Boolean = that match {
    case that: Long => longValue() == that.longValue()
    case _          => false
  }

  @inline override def hashCode(): Int =
    Long.hashCode(longValue())

  @inline override def compareTo(that: Long): Int =
    Long.compare(longValue(), that.longValue())

  @inline override def toString(): String =
    Long.toString(longValue())

}

object Long {
  /* TYPE should be a `final val`, but that crashes the JVM back-end, so we
   * use a 'def' instead, which is binary compatible.
   */
  def TYPE: Class[_] = scala.Predef.classOf[scala.Long]

  final val MIN_VALUE = -9223372036854775808L
  final val MAX_VALUE = 9223372036854775807L
  final val SIZE = 64
  final val BYTES = 8

  private final val SignBit = scala.Long.MinValue

  private final class StringRadixInfo(val chunkLength: Int,
      val radixPowLength: scala.Long, val paddingZeros: String,
      val overflowBarrier: scala.Long)

  /** Precomputed table for toUnsignedStringInternalLarge and
   *  parseUnsignedLongInternal.
   */
  private lazy val StringRadixInfos: js.Array[StringRadixInfo] = {
    val r = new js.Array[StringRadixInfo]()
    var radix = 0

    while (radix < Character.MIN_RADIX) {
      r.push(null)
      radix += 1
    }

    while (radix <= Character.MAX_RADIX) {
      /* Find the biggest chunk size we can use.
       *
       * - radixPowLength should be the biggest signed int32 value that is an
       *   exact power of radix.
       * - chunkLength is then log_radix(radixPowLength).
       * - paddingZeros is a string with exactly chunkLength '0's.
       * - overflowBarrier is divideUnsigned(-1L, radixPowLength) so that we
       *   can test whether someValue * radixPowLength will overflow.
       */
      val barrier = Int.MaxValue / radix
      var radixPowLength = radix
      var chunkLength = 1
      var paddingZeros = "0"
      while (radixPowLength <= barrier) {
        radixPowLength *= radix
        chunkLength += 1
        paddingZeros += "0"
      }
      val radixPowLengthLong = radixPowLength.toLong
      val overflowBarrier = Long.divideUnsigned(-1L, radixPowLengthLong)
      r.push(new StringRadixInfo(chunkLength, radixPowLengthLong,
          paddingZeros, overflowBarrier))
      radix += 1
    }

    r
  }

  @inline // because radix is almost certainly constant at call site
  def toString(i: scala.Long, radix: Int): String = {
    if (radix == 10 || radix < Character.MIN_RADIX || radix > Character.MAX_RADIX)
      toString(i)
    else
      toStringImpl(i, radix)
  }

  @inline // because radix is almost certainly constant at call site
  def toUnsignedString(i: scala.Long, radix: Int): String = {
    (radix: @switch) match {
      case 2  => toBinaryString(i)
      case 8  => toOctalString(i)
      case 16 => toHexString(i)
      case _  =>
        val radix1 =
          if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) 10
          else radix
        toUnsignedStringImpl(i, radix1)
    }
  }

  // Intrinsic
  @inline def toString(i: scala.Long): String = "" + i

  @inline def toUnsignedString(i: scala.Long): String =
    toUnsignedStringImpl(i, 10)

  // Must be called only with valid radix
  private def toStringImpl(i: scala.Long, radix: Int): String = {
    val lo = i.toInt
    val hi = (i >>> 32).toInt
    if (lo >> 31 == hi) {
      // It's a signed int32
      import js.JSNumberOps.enableJSNumberOps
      lo.toString(radix)
    } else if (hi < 0) {
      "-" + toUnsignedStringInternalLarge(-i, radix)
    } else {
      toUnsignedStringInternalLarge(i, radix)
    }
  }

  // Must be called only with valid radix
  private def toUnsignedStringImpl(i: scala.Long, radix: Int): String = {
    if ((i >>> 32).toInt == 0) {
      // It's an unsigned int32
      import js.JSNumberOps.enableJSNumberOps
      Utils.toUint(i.toInt).toString(radix)
    } else {
      toUnsignedStringInternalLarge(i, radix)
    }
  }

  // Must be called only with valid radix
  private def toUnsignedStringInternalLarge(i: scala.Long, radix: Int): String = {
    import js.JSNumberOps.enableJSNumberOps
    import js.JSStringOps.enableJSStringOps

    val radixInfo = StringRadixInfos(radix)
    val divisor = radixInfo.radixPowLength
    val paddingZeros = radixInfo.paddingZeros

    val divisorXorSignBit = divisor.toLong ^ SignBit

    var res = ""
    var value = i
    while ((value ^ SignBit) >= divisorXorSignBit) { // unsigned comparison
      val div = divideUnsigned(value, divisor)
      val rem = value - div * divisor // == remainderUnsigned(value, divisor)
      val remStr = rem.toInt.toString(radix)
      res = paddingZeros.jsSubstring(remStr.length) + remStr + res
      value = div
    }

    value.toInt.toString(radix) + res
  }

  def parseLong(s: String, radix: Int): scala.Long = {
    if (s == "")
      parseLongError(s)

    var start = 0
    var neg = false

    s.charAt(0) match {
      case '+' =>
        start = 1
      case '-' =>
        start = 1
        neg = true
      case _ =>
    }

    val unsignedResult = parseUnsignedLongInternal(s, radix, start)

    if (neg) {
      val result = -unsignedResult
      if (result > 0)
        parseLongError(s)
      result
    } else {
      if (unsignedResult < 0)
        parseLongError(s)
      unsignedResult
    }
  }

  @inline def parseLong(s: String): scala.Long =
    parseLong(s, 10)

  def parseUnsignedLong(s: String, radix: Int): scala.Long = {
    if (s == "")
      parseLongError(s)

    val start =
      if (s.charAt(0) == '+') 1
      else 0

    parseUnsignedLongInternal(s, radix, start)
  }

  @inline def parseUnsignedLong(s: String): scala.Long =
    parseUnsignedLong(s, 10)

  def parseUnsignedLongInternal(s: String, radix: Int, start: Int): scala.Long = {
    import js.JSStringOps._

    val length = s.length

    if (start >= length || radix < Character.MIN_RADIX ||
        radix > Character.MAX_RADIX) {
      parseLongError(s)
    } else {
      val radixInfo = StringRadixInfos(radix)
      val chunkLen = radixInfo.chunkLength

      /* Skip leading 0's - important because we have an assumption on the
       * number of chunks that are necessary to parse any string.
       */
      var firstChunkStart = start
      while (firstChunkStart < length &&
          Character.isZeroDigit(s.charAt(firstChunkStart))) {
        firstChunkStart += 1
      }

      /* After that, if more than 3 chunks are necessary, it means the value
       * is too large, and does not fit in an unsigned Long.
       */
      if (length - firstChunkStart > 3 * chunkLen)
        parseLongError(s)

      @noinline def parseChunkAsUInt(chunkStart: Int, chunkEnd: Int): Int = {
        var result = 0 // This is an *unsigned* integer
        var i = chunkStart
        while (i != chunkEnd) {
          val digit = Character.digitWithValidRadix(s.charAt(i), radix)
          if (digit == -1)
            parseLongError(s)
          result = result * radix + digit // cannot overflow
          i += 1
        }
        result
      }

      @inline def parseChunk(chunkStart: Int, chunkEnd: Int): scala.Long =
        Integer.toUnsignedLong(parseChunkAsUInt(chunkStart, chunkEnd))

      /* The first chunk is sized so that all subsequent chunks are of size
       * chunkLen. Note also that the first chunk cannot overflow.
       * For small strings (length <= MaxLen), this first chunk is all there
       * is.
       */
      val firstChunkLength = ((length - firstChunkStart) - 1) % chunkLen + 1
      val firstChunkEnd = firstChunkStart + firstChunkLength
      val firstResult = parseChunk(firstChunkStart, firstChunkEnd)

      if (firstChunkEnd == length) {
        firstResult
      } else {
        // Second chunk. Still cannot overflow.
        val multiplier = radixInfo.radixPowLength
        val secondChunkEnd = firstChunkEnd + chunkLen
        val secondResult =
          firstResult * multiplier + parseChunk(firstChunkEnd, secondChunkEnd)

        if (secondChunkEnd == length) {
          secondResult
        } else {
          // Third and final chunk. This one can overflow
          // Assert: secondChunkEnd + chunkLen == length

          val overflowBarrier = radixInfo.overflowBarrier
          val thirdChunk = parseChunk(secondChunkEnd, length)

          if (secondResult > overflowBarrier) // both positive so signed > is OK
            parseLongError(s) // * will overflow
          val thirdResult = secondResult * multiplier + thirdChunk
          if ((thirdResult ^ SignBit) < (thirdChunk ^ SignBit))
            parseLongError(s) // + overflowed

          thirdResult
        }
      }
    }
  }

  private def parseLongError(s: String): Nothing =
    throw new NumberFormatException(s"""For input string: "$s"""")

  @inline def `new`(value: scala.Long): Long = valueOf(value)

  @inline def `new`(s: String): Long = valueOf(s)

  @inline def valueOf(l: scala.Long): Long = l.asInstanceOf[Long]

  @inline def valueOf(s: String): Long = valueOf(parseLong(s))

  @inline def valueOf(s: String, radix: Int): Long =
    valueOf(parseLong(s, radix))

  @noinline def decode(nm: String): Long =
    Integer.decodeGeneric(nm, valueOf(_, _))

  @inline def hashCode(value: scala.Long): Int =
    value.toInt ^ (value >>> 32).toInt

  // Intrinsic
  @inline def compare(x: scala.Long, y: scala.Long): scala.Int = {
    if (x == y) 0
    else if (x < y) -1
    else 1
  }

  // TODO Intrinsic?
  @inline def compareUnsigned(x: scala.Long, y: scala.Long): scala.Int =
    compare(x ^ SignBit, y ^ SignBit)

  @inline def divideUnsigned(dividend: scala.Long, divisor: scala.Long): scala.Long =
    throw new Error("stub") // body replaced by the compiler back-end

  @inline def remainderUnsigned(dividend: scala.Long, divisor: scala.Long): scala.Long =
    throw new Error("stub") // body replaced by the compiler back-end

  @inline
  def highestOneBit(i: scala.Long): scala.Long = {
    val lo = i.toInt
    val hi = (i >>> 32).toInt
    makeLongFromLoHi(
        if (hi != 0) 0 else Integer.highestOneBit(lo),
        Integer.highestOneBit(hi))
  }

  @inline
  def lowestOneBit(i: scala.Long): scala.Long = {
    val lo = i.toInt
    val hi = (i >> 32).toInt
    makeLongFromLoHi(
        Integer.lowestOneBit(lo),
        if (lo != 0) 0 else Integer.lowestOneBit(hi))
  }

  // Wasm intrinsic
  @inline
  def bitCount(i: scala.Long): scala.Int = {
    val lo = i.toInt
    val hi = (i >>> 32).toInt
    Integer.bitCount(lo) + Integer.bitCount(hi)
  }

  @inline
  def reverseBytes(i: scala.Long): scala.Long = {
    makeLongFromLoHi(
        Integer.reverseBytes((i >>> 32).toInt),
        Integer.reverseBytes(i.toInt))
  }

  @inline
  def reverse(i: scala.Long): scala.Long = {
    makeLongFromLoHi(
        Integer.reverse((i >>> 32).toInt),
        Integer.reverse(i.toInt))
  }

  /** Make a `Long` value from its lo and hi 32-bit parts.
   *  When the optimizer is enabled, this operation is free.
   */
  @inline
  private def makeLongFromLoHi(lo: Int, hi: Int): scala.Long =
    (lo.toLong & 0xffffffffL) | (hi.toLong << 32)

  // Wasm intrinsic
  @inline
  def rotateLeft(i: scala.Long, distance: scala.Int): scala.Long =
    (i << distance) | (i >>> -distance)

  // Wasm intrinsic
  @inline
  def rotateRight(i: scala.Long, distance: scala.Int): scala.Long =
    (i >>> distance) | (i << -distance)

  @inline
  def signum(i: scala.Long): Int = {
    val hi = (i >>> 32).toInt
    if (hi < 0) -1
    else if (hi == 0 && i.toInt == 0) 0
    else 1
  }

  @inline
  def numberOfLeadingZeros(l: scala.Long): Int =
    throw new Error("stub") // body replaced by the compiler back-end

  // Wasm intrinsic
  @inline
  def numberOfTrailingZeros(l: scala.Long): Int = {
    val lo = l.toInt
    if (lo != 0) Integer.numberOfTrailingZeros(lo)
    else         Integer.numberOfTrailingZeros((l >>> 32).toInt) + 32
  }

  @inline def toBinaryString(l: scala.Long): String =
    toBinaryString(l.toInt, (l >>> 32).toInt)

  private def toBinaryString(lo: Int, hi: Int): String = {
    val zeros = "00000000000000000000000000000000" // 32 zeros
    @inline def padBinary32(i: Int) = {
      val s = Integer.toBinaryString(i)
      zeros.substring(s.length) + s
    }

    if (hi != 0) Integer.toBinaryString(hi) + padBinary32(lo)
    else Integer.toBinaryString(lo)
  }

  @inline def toHexString(l: scala.Long): String =
    toHexString(l.toInt, (l >>> 32).toInt)

  private def toHexString(lo: Int, hi: Int): String = {
    val zeros = "00000000" // 8 zeros
    @inline def padBinary8(i: Int) = {
      val s = Integer.toHexString(i)
      zeros.substring(s.length) + s
    }

    if (hi != 0) Integer.toHexString(hi) + padBinary8(lo)
    else Integer.toHexString(lo)
  }

  @inline def toOctalString(l: scala.Long): String =
    toOctalString(l.toInt, (l >>> 32).toInt)

  private def toOctalString(lo: Int, hi: Int): String = {
    val zeros = "0000000000" // 10 zeros
    @inline def padOctal10(i: Int) = {
      val s = Integer.toOctalString(i)
      zeros.substring(s.length) + s
    }

    val lp = lo & 0x3fffffff
    val mp = ((lo >>> 30) + (hi << 2)) & 0x3fffffff
    val hp = hi >>> 28

    if (hp != 0) Integer.toOctalString(hp) + padOctal10(mp) + padOctal10(lp)
    else if (mp != 0) Integer.toOctalString(mp) + padOctal10(lp)
    else Integer.toOctalString(lp)
  }

  @inline def sum(a: scala.Long, b: scala.Long): scala.Long =
    a + b

  @inline def max(a: scala.Long, b: scala.Long): scala.Long =
    Math.max(a, b)

  @inline def min(a: scala.Long, b: scala.Long): scala.Long =
    Math.min(a, b)
}
