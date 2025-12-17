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

import scala.language.implicitConversions

import java.util.function._

import scala.scalajs.js
import scala.scalajs.js.annotation._

/** Utilities to perform bounds checks.
 *
 *  Some of these methods have a single call sites. They are all gathered here
 *  anyway, in order to concentrate the knowledge of how to perform the checks
 *  in the most efficient way.
 *
 *  All the tests optimize for the happy path, where all values are valid.
 */
private[java] object BoundsChecks {

  /** Checks that offset >= 0, count >= 0 and offset + count <= length.
   *
   *  Where `offset + count` is understood as the mathematical (non-wrapping)
   *  addition.
   *
   *  If the conditions do not hold, throw an `IndexOutOfBoundsException`.
   *
   *  Assumes that `length >= 0`.
   */
  @inline
  def checkOffsetCount(offset: Int, count: Int, length: Int): Unit = {
    if (isOffsetCountInvalid(offset, count, length))
      throw new IndexOutOfBoundsException()
  }

  @inline
  def isOffsetCountInvalid(offset: Int, count: Int, length: Int): scala.Boolean = {
    /* - If offset < 0 or count < 0, return true.
     * - Otherwise, if endOffset = (offset + count) overflows, return true.
     *   Equiv: endOffset < 0.
     * - Otherwise, if (length < endOffset), return true.
     *   Equiv, because length >= 0: (length - endOffset) < 0.
     *
     * Since the happy path, where we return false, needs to compute all of
     * those, we always compute them all and test the four x < 0 with a single
     * branch.
     *
     * For reference, there is a trade-off against the formula
     *   ((offset | count) < 0) || (offset > length - count)
     * Our formula uses one fewer branch at the cost of an addition.
     */
    val endOffset = offset + count
    (offset | count | endOffset | (length - endOffset)) < 0
  }

  /** Checks that `0 <= start <= length` hold.
   *
   *  If the conditions do not hold, throw an `IndexOutOfBoundsException`.
   *
   *  Assumes that `length >= 0`.
   */
  @inline
  def checkStart(start: Int, length: Int): Unit = {
    if (isStartInvalid(start, length))
      throw new IndexOutOfBoundsException()
  }

  /** Returns true if the inequalities `0 <= start <= length` do *not* hold.
   *
   *  Assumes `length >= 0`.
   */
  @inline
  def isStartInvalid(start: Int, length: Int): scala.Boolean =
    Integer.unsigned_>(start, length)

  /** Checks that `0 <= start <= end <= length` hold.
   *
   *  If the conditions do not hold, throw an `IndexOutOfBoundsException`.
   *
   *  Assumes that `length >= 0`.
   */
  @inline
  def checkStartEnd(start: Int, end: Int, length: Int): Unit = {
    if (isStartEndInvalid(start, end, length))
      throw new IndexOutOfBoundsException()
  }

  /** Returns true if the inequalities `0 <= start <= end <= length` do *not* hold.
   *
   *  Assumes `length >= 0`.
   */
  @inline
  def isStartEndInvalid(start: Int, end: Int, length: Int): scala.Boolean =
    Integer.unsigned_>(end, length) || Integer.unsigned_>(start, end)

  /** Checks that `0 <= index < limit <= length` hold.
   *
   *  If the conditions do not hold, throw an `IndexOutOfBoundsException`.
   *
   *  Assumes that `length >= 0`.
   */
  @inline
  def checkIndexLimit(index: Int, limit: Int, length: Int): Unit = {
    if (isIndexLimitInvalid(index, limit, length))
      throw new IndexOutOfBoundsException()
  }

  /** Returns true if inequalities `0 <= index < limit <= length` do *not* hold.
   *
   *  Assumes `length >= 0`.
   */
  @inline
  def isIndexLimitInvalid(index: Int, limit: Int, length: Int): scala.Boolean =
    Integer.unsigned_>(limit, length) || Integer.unsigned_>=(index, limit)

  /** Checks that `0 <= start <= index < length` hold.
   *
   *  If the conditions do not hold, throw an `IndexOutOfBoundsException`.
   *
   *  Assumes that `length >= 0`.
   */
  @inline
  def checkIndexStart(index: Int, start: Int, length: Int): Unit = {
    if (isIndexStartInvalid(index, start, length))
      throw new IndexOutOfBoundsException()
  }

  /** Returns true if inequalities `0 <= start < index <= length` do *not* hold.
   *
   *  Assumes `length >= 0`.
   */
  @inline
  def isIndexStartInvalid(index: Int, start: Int, length: Int): scala.Boolean =
    Integer.unsigned_>(index, length) || Integer.unsigned_>=(start, index)

  /** Checks that `0 <= start <= index <= limit <= length` hold.
   *
   *  If the conditions do not hold, throw an `IndexOutOfBoundsException`.
   *
   *  Assumes that `length >= 0`.
   */
  @inline
  def checkIndexStartLimit(index: Int, start: Int, limit: Int, length: Int): Unit = {
    if (isIndexStartLimitInvalid(index, start, limit, length))
      throw new IndexOutOfBoundsException()
  }

  /** Returns true if inequalities `0 <= start <= index <= limit <= length`
   *  do *not* hold.
   *
   *  Assumes `length >= 0`.
   */
  @inline
  def isIndexStartLimitInvalid(index: Int, start: Int, limit: Int, length: Int): scala.Boolean = {
    Integer.unsigned_>(limit, length) ||
    Integer.unsigned_>(index, limit) ||
    Integer.unsigned_>(start, index)
  }

  /** Returns true if the given quantities are invalid for a source to dest
   *  transfer, given starting offsets and count.
   *
   *  Assumes `srcLength >= 0` and `destLength >= 0`.
   *
   *  Effectively computes
   *  {{{
   *  srcOffset < 0 || destOffset < 0 || count < 0 ||
   *  srcOffset > srcLength - count || destOffset > destLength - count
   *  }}}
   */
  @inline
  def isSrcDestOffsetCountInvalid(srcOffset: Int, srcLength: Int,
      destOffset: Int, destLength: Int, count: Int): scala.Boolean = {
    /* Similarly to `isOffsetCountInvalid`, this formula trades 2 additions for
     * 2 branches.
     */
    isSrcDestStartEndCountInvalid(srcOffset, srcOffset + count, srcLength,
        destOffset, destOffset + count, destLength, count)
  }

  /** Returns true if the given quantities are invalid for a source to dest
   *  transfer, given start and end positions.
   *
   *  Assumes `srcLength >= 0` and `destLength >= 0`.
   *
   *  Effectively computes
   *  {{{
   *  srcStart < 0 || srcEnd < srcStart || srcEnd > srcLength ||
   *  destStart < 0 || destStart + (srcEnd - srcStart) > destLength
   *  }}}
   */
  @inline
  def isSrcDestStartEndInvalid(srcStart: Int, srcEnd: Int, srcLength: Int,
      destStart: Int, destLength: Int): scala.Boolean = {
    /* Similarly to `isOffsetCountInvalid`, this formula trades 2 additions for
     * 2 branches.
     */
    val count = srcEnd - srcStart
    isSrcDestStartEndCountInvalid(srcStart, srcEnd, srcLength,
        destStart, destStart + count, destLength, count)
  }

  @inline
  private def isSrcDestStartEndCountInvalid(srcStart: Int, srcEnd: Int,
      srcLength: Int, destStart: Int, destEnd: Int, destLength: Int,
      count: Int): scala.Boolean = {
    (srcStart | destStart | count | srcEnd | destEnd |
        (srcLength - srcEnd) | (destLength - destEnd)) < 0
  }
}
