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

package java.util

import java.lang.{reflect => jlr}

import scala.scalajs.js

import scala.annotation.tailrec

import ScalaOps._

object Arrays {

  /** Safe upcasting of an array, following run-time covariance. */
  @inline private def arrayUpcast[A](a: Array[_ <: A]): Array[A] =
    a.asInstanceOf[Array[A]]

  @inline private def componentTypeOf[A](clazz: Class[_ <: Array[A]]): Class[_ <: A] =
    clazz.getComponentType().asInstanceOf[Class[_ <: A]]

  @inline private def componentTypeOf[A](a: Array[A]): Class[_ <: A] =
    componentTypeOf(a.getClass())

  /** A custom typeclass for the operations we need in `Arrays` to implement
   *  the algorithms generically.
   */
  private sealed abstract class ArrayOps[A] {
    def length(a: Array[A]): Int
    def get(a: Array[A], i: Int): A
    def set(a: Array[A], i: Int, v: A): Unit
    def create(length: Int): Array[A]
    def compare(x: A, y: A): Int

    @inline def createFrom(template: Array[A], length: Int): Array[A] =
      create(length) // most subclasses have a correct `create(length)`

    @inline def lt(x: A, y: A): Boolean = compare(x, y) < 0
    @inline def lteq(x: A, y: A): Boolean = compare(x, y) <= 0
    @inline def gt(x: A, y: A): Boolean = compare(x, y) > 0
    @inline def gteq(x: A, y: A): Boolean = compare(x, y) >= 0
  }

  @inline
  private final class SpecificAnyRefArrayOps[A <: AnyRef] extends ArrayOps[A] {
    @inline def length(a: Array[A]): Int = a.length
    @inline def get(a: Array[A], i: Int): A = a(i)
    @inline def set(a: Array[A], i: Int, v: A): Unit = a(i) = v
    @inline def create(length: Int): Array[A] =
      throw new UnsupportedOperationException("SpecificAnyRefArrayOps.create()")
    @inline def compare(x: A, y: A): Int =
      throw new UnsupportedOperationException("SpecificAnyRefArrayOps.compare()")

    @inline
    override def createFrom(template: Array[A], length: Int): Array[A] =
      jlr.Array.newInstance(componentTypeOf(template), length).asInstanceOf[Array[A]]
  }

  @inline
  private final class ComparatorArrayOps[A <: AnyRef](
      comparator: Comparator[_ >: A])
      extends ArrayOps[A] {
    @inline def length(a: Array[A]): Int = a.length
    @inline def get(a: Array[A], i: Int): A = a(i)
    @inline def set(a: Array[A], i: Int, v: A): Unit = a(i) = v
    @inline def create(length: Int): Array[A] =
      throw new UnsupportedOperationException("ComparatorArrayOps.create()")
    @inline def compare(x: A, y: A): Int = comparator.compare(x, y)

    @inline
    override def createFrom(template: Array[A], length: Int): Array[A] =
      jlr.Array.newInstance(componentTypeOf(template), length).asInstanceOf[Array[A]]
  }

  @inline
  private final class ClassArrayOps[A <: AnyRef](clazz: Class[A]) extends ArrayOps[A] {
    @inline def length(a: Array[A]): Int = a.length
    @inline def get(a: Array[A], i: Int): A = a(i)
    @inline def set(a: Array[A], i: Int, v: A): Unit = a(i) = v
    @inline def create(length: Int): Array[A] =
      jlr.Array.newInstance(clazz, length).asInstanceOf[Array[A]]
    @inline def compare(x: A, y: A): Int =
      throw new UnsupportedOperationException("ClassArrayOps.compare()")
  }

  private implicit object AnyRefArrayOps extends ArrayOps[AnyRef] {
    @inline def length(a: Array[AnyRef]): Int = a.length
    @inline def get(a: Array[AnyRef], i: Int): AnyRef = a(i)
    @inline def set(a: Array[AnyRef], i: Int, v: AnyRef): Unit = a(i) = v
    @inline def create(length: Int): Array[AnyRef] = new Array[AnyRef](length)
    @inline def compare(x: AnyRef, y: AnyRef): Int = x.asInstanceOf[Comparable[AnyRef]].compareTo(y)
  }

  private implicit object BooleanArrayOps extends ArrayOps[Boolean] {
    @inline def length(a: Array[Boolean]): Int = a.length
    @inline def get(a: Array[Boolean], i: Int): Boolean = a(i)
    @inline def set(a: Array[Boolean], i: Int, v: Boolean): Unit = a(i) = v
    @inline def create(length: Int): Array[Boolean] = new Array[Boolean](length)
    @inline def compare(x: Boolean, y: Boolean): Int = java.lang.Boolean.compare(x, y)
  }

  private implicit object CharArrayOps extends ArrayOps[Char] {
    @inline def length(a: Array[Char]): Int = a.length
    @inline def get(a: Array[Char], i: Int): Char = a(i)
    @inline def set(a: Array[Char], i: Int, v: Char): Unit = a(i) = v
    @inline def create(length: Int): Array[Char] = new Array[Char](length)
    @inline def compare(x: Char, y: Char): Int = java.lang.Character.compare(x, y)
  }

  private implicit object ByteArrayOps extends ArrayOps[Byte] {
    @inline def length(a: Array[Byte]): Int = a.length
    @inline def get(a: Array[Byte], i: Int): Byte = a(i)
    @inline def set(a: Array[Byte], i: Int, v: Byte): Unit = a(i) = v
    @inline def create(length: Int): Array[Byte] = new Array[Byte](length)
    @inline def compare(x: Byte, y: Byte): Int = java.lang.Byte.compare(x, y)
  }

  private implicit object ShortArrayOps extends ArrayOps[Short] {
    @inline def length(a: Array[Short]): Int = a.length
    @inline def get(a: Array[Short], i: Int): Short = a(i)
    @inline def set(a: Array[Short], i: Int, v: Short): Unit = a(i) = v
    @inline def create(length: Int): Array[Short] = new Array[Short](length)
    @inline def compare(x: Short, y: Short): Int = java.lang.Short.compare(x, y)
  }

  private implicit object IntArrayOps extends ArrayOps[Int] {
    @inline def length(a: Array[Int]): Int = a.length
    @inline def get(a: Array[Int], i: Int): Int = a(i)
    @inline def set(a: Array[Int], i: Int, v: Int): Unit = a(i) = v
    @inline def create(length: Int): Array[Int] = new Array[Int](length)
    @inline def compare(x: Int, y: Int): Int = java.lang.Integer.compare(x, y)
  }

  private implicit object LongArrayOps extends ArrayOps[Long] {
    @inline def length(a: Array[Long]): Int = a.length
    @inline def get(a: Array[Long], i: Int): Long = a(i)
    @inline def set(a: Array[Long], i: Int, v: Long): Unit = a(i) = v
    @inline def create(length: Int): Array[Long] = new Array[Long](length)
    @inline def compare(x: Long, y: Long): Int = java.lang.Long.compare(x, y)
  }

  private implicit object FloatArrayOps extends ArrayOps[Float] {
    @inline def length(a: Array[Float]): Int = a.length
    @inline def get(a: Array[Float], i: Int): Float = a(i)
    @inline def set(a: Array[Float], i: Int, v: Float): Unit = a(i) = v
    @inline def create(length: Int): Array[Float] = new Array[Float](length)
    @inline def compare(x: Float, y: Float): Int = java.lang.Float.compare(x, y)
  }

  private implicit object DoubleArrayOps extends ArrayOps[Double] {
    @inline def length(a: Array[Double]): Int = a.length
    @inline def get(a: Array[Double], i: Int): Double = a(i)
    @inline def set(a: Array[Double], i: Int, v: Double): Unit = a(i) = v
    @inline def create(length: Int): Array[Double] = new Array[Double](length)
    @inline def compare(x: Double, y: Double): Int = java.lang.Double.compare(x, y)
  }

  @noinline def sort(a: Array[Int]): Unit =
    sortImpl(a)

  @noinline def sort(a: Array[Int], fromIndex: Int, toIndex: Int): Unit =
    sortRangeImpl[Int](a, fromIndex, toIndex)

  @noinline def sort(a: Array[Long]): Unit =
    sortImpl(a)

  @noinline def sort(a: Array[Long], fromIndex: Int, toIndex: Int): Unit =
    sortRangeImpl[Long](a, fromIndex, toIndex)

  @noinline def sort(a: Array[Short]): Unit =
    sortImpl(a)

  @noinline def sort(a: Array[Short], fromIndex: Int, toIndex: Int): Unit =
    sortRangeImpl[Short](a, fromIndex, toIndex)

  @noinline def sort(a: Array[Char]): Unit =
    sortImpl(a)

  @noinline def sort(a: Array[Char], fromIndex: Int, toIndex: Int): Unit =
    sortRangeImpl[Char](a, fromIndex, toIndex)

  @noinline def sort(a: Array[Byte]): Unit =
    sortImpl(a)

  @noinline def sort(a: Array[Byte], fromIndex: Int, toIndex: Int): Unit =
    sortRangeImpl[Byte](a, fromIndex, toIndex)

  @noinline def sort(a: Array[Float]): Unit =
    sortImpl(a)

  @noinline def sort(a: Array[Float], fromIndex: Int, toIndex: Int): Unit =
    sortRangeImpl[Float](a, fromIndex, toIndex)

  @noinline def sort(a: Array[Double]): Unit =
    sortImpl(a)

  @noinline def sort(a: Array[Double], fromIndex: Int, toIndex: Int): Unit =
    sortRangeImpl[Double](a, fromIndex, toIndex)

  @noinline def sort(a: Array[AnyRef]): Unit =
    sortImpl(a)

  @noinline def sort(a: Array[AnyRef], fromIndex: Int, toIndex: Int): Unit =
    sortRangeImpl(a, fromIndex, toIndex)

  @noinline def sort[T <: AnyRef](array: Array[T], comparator: Comparator[_ >: T]): Unit =
    sortImpl(array)(new ComparatorArrayOps(comparator))

  @noinline def sort[T <: AnyRef](array: Array[T], fromIndex: Int, toIndex: Int,
      comparator: Comparator[_ >: T]): Unit = {
    sortRangeImpl(array, fromIndex, toIndex)(new ComparatorArrayOps(comparator))
  }

  @inline
  private def sortRangeImpl[T](a: Array[T], fromIndex: Int, toIndex: Int)(
        implicit ops: ArrayOps[T]): Unit = {
    checkRangeIndices(a, fromIndex, toIndex)
    stableMergeSort[T](a, fromIndex, toIndex)
  }

  @inline
  private def sortImpl[T](a: Array[T])(implicit ops: ArrayOps[T]): Unit =
    stableMergeSort[T](a, 0, ops.length(a))

  private final val inPlaceSortThreshold = 16

  /** Sort array `a` with merge sort and insertion sort. */
  @inline
  private def stableMergeSort[T](a: Array[T], start: Int, end: Int)(
      implicit ops: ArrayOps[T]): Unit = {
    if (end - start > inPlaceSortThreshold)
      stableSplitMerge(a, ops.createFrom(a, ops.length(a)), start, end)
    else
      insertionSort(a, start, end)
  }

  @noinline
  private def stableSplitMerge[T](a: Array[T], temp: Array[T], start: Int,
      end: Int)(implicit ops: ArrayOps[T]): Unit = {
    val length = end - start
    if (length > inPlaceSortThreshold) {
      val middle = start + (length >> 2)
      stableSplitMerge(a, temp, start, middle)
      stableSplitMerge(a, temp, middle, end)
      stableMerge(a, temp, start, middle, end)
      System.arraycopy(temp, start, a, start, length)
    } else {
      insertionSort(a, start, end)
    }
  }

  @inline
  private def stableMerge[T](a: Array[T], temp: Array[T], start: Int,
      middle: Int, end: Int)(implicit ops: ArrayOps[T]): Unit = {
    var outIndex = start
    var leftInIndex = start
    var rightInIndex = middle
    while (outIndex < end) {
      if (leftInIndex < middle &&
          (rightInIndex >= end || ops.lteq(ops.get(a, leftInIndex), ops.get(a, rightInIndex)))) {
        ops.set(temp, outIndex, ops.get(a, leftInIndex))
        leftInIndex += 1
      } else {
        ops.set(temp, outIndex, ops.get(a, rightInIndex))
        rightInIndex += 1
      }
      outIndex += 1
    }
  }

  // ArrayOps[T] might be slow especially for boxed primitives, so use binary
  // search variant of insertion sort
  // Caller must pass end >= start or math will fail.  Also, start >= 0.
  @noinline
  private final def insertionSort[T](a: Array[T], start: Int, end: Int)(
      implicit ops: ArrayOps[T]): Unit = {
    val n = end - start
    if (n >= 2) {
      val aStart = ops.get(a, start)
      val aStartPlusOne = ops.get(a, start + 1)
      if (ops.gt(aStart, aStartPlusOne)) {
        ops.set(a, start, aStartPlusOne)
        ops.set(a, start + 1, aStart)
      }

      var m = 2
      while (m < n) {
        // Speed up already-sorted case by checking last element first
        val next = ops.get(a, start + m)
        if (ops.lt(next, ops.get(a, start + m - 1))) {
          var iA = start
          var iB = start + m - 1
          while (iB - iA > 1) {
            val ix = (iA + iB) >>> 1 // Use bit shift to get unsigned div by 2
            if (ops.lt(next, ops.get(a, ix)))
              iB = ix
            else
              iA = ix
          }
          val ix = iA + (if (ops.lt(next, ops.get(a, iA))) 0 else 1)
          var i = start + m
          while (i > ix) {
            ops.set(a, i, ops.get(a, i - 1))
            i -= 1
          }
          ops.set(a, ix, next)
        }
        m += 1
      }
    }
  }

  @noinline def binarySearch(a: Array[Long], key: Long): Int =
    binarySearchImpl[Long](a, 0, a.length, key)

  @noinline def binarySearch(a: Array[Long], startIndex: Int, endIndex: Int, key: Long): Int = {
    checkRangeIndices(a, startIndex, endIndex)
    binarySearchImpl[Long](a, startIndex, endIndex, key)
  }

  @noinline def binarySearch(a: Array[Int], key: Int): Int =
    binarySearchImpl[Int](a, 0, a.length, key)

  @noinline def binarySearch(a: Array[Int], startIndex: Int, endIndex: Int, key: Int): Int = {
    checkRangeIndices(a, startIndex, endIndex)
    binarySearchImpl[Int](a, startIndex, endIndex, key)
  }

  @noinline def binarySearch(a: Array[Short], key: Short): Int =
    binarySearchImpl[Short](a, 0, a.length, key)

  @noinline def binarySearch(a: Array[Short], startIndex: Int, endIndex: Int, key: Short): Int = {
    checkRangeIndices(a, startIndex, endIndex)
    binarySearchImpl[Short](a, startIndex, endIndex, key)
  }

  @noinline def binarySearch(a: Array[Char], key: Char): Int =
    binarySearchImpl[Char](a, 0, a.length, key)

  @noinline def binarySearch(a: Array[Char], startIndex: Int, endIndex: Int, key: Char): Int = {
    checkRangeIndices(a, startIndex, endIndex)
    binarySearchImpl[Char](a, startIndex, endIndex, key)
  }

  @noinline def binarySearch(a: Array[Byte], key: Byte): Int =
    binarySearchImpl[Byte](a, 0, a.length, key)

  @noinline def binarySearch(a: Array[Byte], startIndex: Int, endIndex: Int, key: Byte): Int = {
    checkRangeIndices(a, startIndex, endIndex)
    binarySearchImpl[Byte](a, startIndex, endIndex, key)
  }

  @noinline def binarySearch(a: Array[Double], key: Double): Int =
    binarySearchImpl[Double](a, 0, a.length, key)

  @noinline def binarySearch(a: Array[Double], startIndex: Int, endIndex: Int, key: Double): Int = {
    checkRangeIndices(a, startIndex, endIndex)
    binarySearchImpl[Double](a, startIndex, endIndex, key)
  }

  @noinline def binarySearch(a: Array[Float], key: Float): Int =
    binarySearchImpl[Float](a, 0, a.length, key)

  @noinline def binarySearch(a: Array[Float], startIndex: Int, endIndex: Int, key: Float): Int = {
    checkRangeIndices(a, startIndex, endIndex)
    binarySearchImpl[Float](a, startIndex, endIndex, key)
  }

  @noinline def binarySearch(a: Array[AnyRef], key: AnyRef): Int =
    binarySearchImpl(a, 0, a.length, key)

  @noinline def binarySearch(a: Array[AnyRef], startIndex: Int, endIndex: Int, key: AnyRef): Int = {
    checkRangeIndices(a, startIndex, endIndex)
    binarySearchImpl(a, startIndex, endIndex, key)
  }

  @noinline def binarySearch[T <: AnyRef](a: Array[T], key: T, c: Comparator[_ >: T]): Int = {
    binarySearchImpl[T](a, 0, a.length, key)(new ComparatorArrayOps[T](c))
  }

  @noinline def binarySearch[T <: AnyRef](a: Array[T], startIndex: Int, endIndex: Int, key: T,
      c: Comparator[_ >: T]): Int = {
    implicit val ops = new ComparatorArrayOps[T](c)
    checkRangeIndices(a, startIndex, endIndex)
    binarySearchImpl[T](a, startIndex, endIndex, key)
  }

  @inline
  @tailrec
  private def binarySearchImpl[T](a: Array[T], startIndex: Int, endIndex: Int,
      key: T)(implicit ops: ArrayOps[T]): Int = {
    if (startIndex == endIndex) {
      // Not found
      -startIndex - 1
    } else {
      // Indices are unsigned 31-bit integer, so this does not overflow
      val mid = (startIndex + endIndex) >>> 1
      val elem = ops.get(a, mid)
      val cmp = ops.compare(key, elem)
      if (cmp < 0) {
        binarySearchImpl(a, startIndex, mid, key)
      } else if (cmp == 0) {
        // Found
        mid
      } else {
        binarySearchImpl(a, mid + 1, endIndex, key)
      }
    }
  }

  @noinline def equals(a: Array[Long], b: Array[Long]): Boolean =
    equalsImpl(a, b)

  @noinline def equals(a: Array[Int], b: Array[Int]): Boolean =
    equalsImpl(a, b)

  @noinline def equals(a: Array[Short], b: Array[Short]): Boolean =
    equalsImpl(a, b)

  @noinline def equals(a: Array[Char], b: Array[Char]): Boolean =
    equalsImpl(a, b)

  @noinline def equals(a: Array[Byte], b: Array[Byte]): Boolean =
    equalsImpl(a, b)

  @noinline def equals(a: Array[Boolean], b: Array[Boolean]): Boolean =
    equalsImpl(a, b)

  @noinline def equals(a: Array[Double], b: Array[Double]): Boolean =
    equalsImpl(a, b)

  @noinline def equals(a: Array[Float], b: Array[Float]): Boolean =
    equalsImpl(a, b)

  @noinline def equals(a: Array[AnyRef], b: Array[AnyRef]): Boolean =
    equalsImpl(a, b)

  @inline
  private def equalsImpl[T](a: Array[T], b: Array[T])(
      implicit ops: ArrayOps[T]): Boolean = {
    // scalastyle:off return
    if (a eq b)
      return true
    if (a == null || b == null)
      return false
    val len = ops.length(a)
    if (ops.length(b) != len)
      return false
    var i = 0
    while (i != len) {
      if (!ops.get(a, i).equals(ops.get(b, i)))
        return false
      i += 1
    }
    true
    // scalastyle:on return
  }

  @noinline def fill(a: Array[Long], value: Long): Unit =
    fillImpl(a, 0, a.length, value, checkIndices = false)

  @noinline def fill(a: Array[Long], fromIndex: Int, toIndex: Int, value: Long): Unit =
    fillImpl(a, fromIndex, toIndex, value)

  @noinline def fill(a: Array[Int], value: Int): Unit =
    fillImpl(a, 0, a.length, value, checkIndices = false)

  @noinline def fill(a: Array[Int], fromIndex: Int, toIndex: Int, value: Int): Unit =
    fillImpl(a, fromIndex, toIndex, value)

  @noinline def fill(a: Array[Short], value: Short): Unit =
    fillImpl(a, 0, a.length, value, checkIndices = false)

  @noinline def fill(a: Array[Short], fromIndex: Int, toIndex: Int, value: Short): Unit =
    fillImpl(a, fromIndex, toIndex, value)

  @noinline def fill(a: Array[Char], value: Char): Unit =
    fillImpl(a, 0, a.length, value, checkIndices = false)

  @noinline def fill(a: Array[Char], fromIndex: Int, toIndex: Int, value: Char): Unit =
    fillImpl(a, fromIndex, toIndex, value)

  @noinline def fill(a: Array[Byte], value: Byte): Unit =
    fillImpl(a, 0, a.length, value, checkIndices = false)

  @noinline def fill(a: Array[Byte], fromIndex: Int, toIndex: Int, value: Byte): Unit =
    fillImpl(a, fromIndex, toIndex, value)

  @noinline def fill(a: Array[Boolean], value: Boolean): Unit =
    fillImpl(a, 0, a.length, value, checkIndices = false)

  @noinline def fill(a: Array[Boolean], fromIndex: Int, toIndex: Int, value: Boolean): Unit =
    fillImpl(a, fromIndex, toIndex, value)

  @noinline def fill(a: Array[Double], value: Double): Unit =
    fillImpl(a, 0, a.length, value, checkIndices = false)

  @noinline def fill(a: Array[Double], fromIndex: Int, toIndex: Int, value: Double): Unit =
    fillImpl(a, fromIndex, toIndex, value)

  @noinline def fill(a: Array[Float], value: Float): Unit =
    fillImpl(a, 0, a.length, value, checkIndices = false)

  @noinline def fill(a: Array[Float], fromIndex: Int, toIndex: Int, value: Float): Unit =
    fillImpl(a, fromIndex, toIndex, value)

  @noinline def fill(a: Array[AnyRef], value: AnyRef): Unit =
    fillImpl(a, 0, a.length, value, checkIndices = false)

  @noinline def fill(a: Array[AnyRef], fromIndex: Int, toIndex: Int, value: AnyRef): Unit =
    fillImpl(a, fromIndex, toIndex, value)

  @inline
  private def fillImpl[T](a: Array[T], fromIndex: Int, toIndex: Int,
      value: T, checkIndices: Boolean = true)(
      implicit ops: ArrayOps[T]): Unit = {
    if (checkIndices)
      checkRangeIndices(a, fromIndex, toIndex)
    var i = fromIndex
    while (i != toIndex) {
      ops.set(a, i, value)
      i += 1
    }
  }

  @noinline def copyOf[T <: AnyRef](original: Array[T], newLength: Int): Array[T] = {
    copyOfImpl(original, newLength)(new SpecificAnyRefArrayOps[T],
        new ClassArrayOps(componentTypeOf(original)))
  }

  @noinline def copyOf[T <: AnyRef, U <: AnyRef](original: Array[U], newLength: Int,
      newType: Class[_ <: Array[T]]): Array[T] = {
    copyOfImpl(original, newLength)(new SpecificAnyRefArrayOps[U],
        new ClassArrayOps(componentTypeOf(newType)))
  }

  @noinline def copyOf(original: Array[Byte], newLength: Int): Array[Byte] =
    copyOfImpl(original, newLength)

  @noinline def copyOf(original: Array[Short], newLength: Int): Array[Short] =
    copyOfImpl(original, newLength)

  @noinline def copyOf(original: Array[Int], newLength: Int): Array[Int] =
    copyOfImpl(original, newLength)

  @noinline def copyOf(original: Array[Long], newLength: Int): Array[Long] =
    copyOfImpl(original, newLength)

  @noinline def copyOf(original: Array[Char], newLength: Int): Array[Char] =
    copyOfImpl(original, newLength)

  @noinline def copyOf(original: Array[Float], newLength: Int): Array[Float] =
    copyOfImpl(original, newLength)

  @noinline def copyOf(original: Array[Double], newLength: Int): Array[Double] =
    copyOfImpl(original, newLength)

  @noinline def copyOf(original: Array[Boolean], newLength: Int): Array[Boolean] =
    copyOfImpl(original, newLength)

  @inline
  private def copyOfImpl[U, T](original: Array[U], newLength: Int)(
      implicit uops: ArrayOps[U], tops: ArrayOps[_ <: T]): Array[T] = {
    checkArrayLength(newLength)
    val copyLength = Math.min(newLength, uops.length(original))
    val ret = arrayUpcast[T](tops.create(newLength))
    System.arraycopy(original, 0, ret, 0, copyLength)
    ret
  }

  @noinline def copyOfRange[T <: AnyRef](original: Array[T], from: Int, to: Int): Array[T] = {
    copyOfRangeImpl(original, from, to)(new SpecificAnyRefArrayOps[T],
        new ClassArrayOps(componentTypeOf(original)))
  }

  @noinline def copyOfRange[T <: AnyRef, U <: AnyRef](original: Array[U],
      from: Int, to: Int, newType: Class[_ <: Array[T]]): Array[T] = {
    copyOfRangeImpl(original, from, to)(new SpecificAnyRefArrayOps[U],
        new ClassArrayOps(componentTypeOf(newType)))
  }

  @noinline def copyOfRange(original: Array[Byte], start: Int, end: Int): Array[Byte] =
    copyOfRangeImpl(original, start, end)

  @noinline def copyOfRange(original: Array[Short], start: Int, end: Int): Array[Short] =
    copyOfRangeImpl(original, start, end)

  @noinline def copyOfRange(original: Array[Int], start: Int, end: Int): Array[Int] =
    copyOfRangeImpl(original, start, end)

  @noinline def copyOfRange(original: Array[Long], start: Int, end: Int): Array[Long] =
    copyOfRangeImpl(original, start, end)

  @noinline def copyOfRange(original: Array[Char], start: Int, end: Int): Array[Char] =
    copyOfRangeImpl(original, start, end)

  @noinline def copyOfRange(original: Array[Float], start: Int, end: Int): Array[Float] =
    copyOfRangeImpl(original, start, end)

  @noinline def copyOfRange(original: Array[Double], start: Int, end: Int): Array[Double] =
    copyOfRangeImpl(original, start, end)

  @noinline def copyOfRange(original: Array[Boolean], start: Int, end: Int): Array[Boolean] =
    copyOfRangeImpl(original, start, end)

  @inline
  private def copyOfRangeImpl[T, U](original: Array[U], start: Int, end: Int)(
      implicit uops: ArrayOps[U], tops: ArrayOps[_ <: T]): Array[T] = {
    if (start > end)
      throw new IllegalArgumentException("" + start + " > " + end)

    val len = uops.length(original)
    val retLength = end - start
    val copyLength = Math.min(retLength, len - start)
    val ret = arrayUpcast[T](tops.create(retLength))
    System.arraycopy(original, start, ret, 0, copyLength)
    ret
  }

  @inline private def checkArrayLength(len: Int): Unit = {
    if (len < 0)
      throw new NegativeArraySizeException
  }

  @noinline def asList[T <: AnyRef](a: Array[T]): List[T] = {
    new AbstractList[T] with RandomAccess {
      def size(): Int =
        a.length

      def get(index: Int): T =
        a(index)

      override def set(index: Int, element: T): T = {
        val ret = a(index)
        a(index) = element
        ret
      }
    }
  }

  @noinline def hashCode(a: Array[Long]): Int =
    hashCodeImpl(a)

  @noinline def hashCode(a: Array[Int]): Int =
    hashCodeImpl(a)

  @noinline def hashCode(a: Array[Short]): Int =
    hashCodeImpl(a)

  @noinline def hashCode(a: Array[Char]): Int =
    hashCodeImpl(a)

  @noinline def hashCode(a: Array[Byte]): Int =
    hashCodeImpl(a)

  @noinline def hashCode(a: Array[Boolean]): Int =
    hashCodeImpl(a)

  @noinline def hashCode(a: Array[Float]): Int =
    hashCodeImpl(a)

  @noinline def hashCode(a: Array[Double]): Int =
    hashCodeImpl(a)

  @noinline def hashCode(a: Array[AnyRef]): Int =
    hashCodeImpl(a)

  @inline
  private def hashCodeImpl[T](a: Array[T])(implicit ops: ArrayOps[T]): Int = {
    if (a == null) {
      0
    } else {
      var acc = 1
      val len = ops.length(a)
      var i = 0
      while (i != len) {
        acc = 31 * acc + Objects.hashCode(ops.get(a, i))
        i += 1
      }
      acc
    }
  }

  @noinline def deepHashCode(a: Array[AnyRef]): Int = {
    def rec(a: Array[AnyRef]): Int = {
      var acc = 1
      val len = a.length
      var i = 0
      while (i != len) {
        acc = 31*acc + (a(i) match {
          case elem: Array[AnyRef]  => rec(elem)
          case elem: Array[Long]    => hashCode(elem)
          case elem: Array[Int]     => hashCode(elem)
          case elem: Array[Short]   => hashCode(elem)
          case elem: Array[Char]    => hashCode(elem)
          case elem: Array[Byte]    => hashCode(elem)
          case elem: Array[Boolean] => hashCode(elem)
          case elem: Array[Float]   => hashCode(elem)
          case elem: Array[Double]  => hashCode(elem)
          case elem                 => Objects.hashCode(elem)
        })
        i += 1
      }
      acc
    }

    if (a == null) 0
    else rec(a)
  }

  @noinline def deepEquals(a1: Array[AnyRef], a2: Array[AnyRef]): Boolean = {
    // scalastyle:off return
    if (a1 eq a2)
      return true
    if (a1 == null || a2 == null)
      return false
    val len = a1.length
    if (a2.length != len)
      return false
    var i = 0
    while (i != len) {
      if (!Objects.deepEquals(a1(i), a2(i)))
        return false
      i += 1
    }
    true
    // scalastyle:on return
  }

  @noinline def toString(a: Array[Long]): String =
    toStringImpl[Long](a)

  @noinline def toString(a: Array[Int]): String =
    toStringImpl[Int](a)

  @noinline def toString(a: Array[Short]): String =
    toStringImpl[Short](a)

  @noinline def toString(a: Array[Char]): String =
    toStringImpl[Char](a)

  @noinline def toString(a: Array[Byte]): String =
    toStringImpl[Byte](a)

  @noinline def toString(a: Array[Boolean]): String =
    toStringImpl[Boolean](a)

  @noinline def toString(a: Array[Float]): String =
    toStringImpl[Float](a)

  @noinline def toString(a: Array[Double]): String =
    toStringImpl[Double](a)

  @noinline def toString(a: Array[AnyRef]): String =
    toStringImpl[AnyRef](a)

  @inline
  private def toStringImpl[T](a: Array[T])(implicit ops: ArrayOps[T]): String = {
    if (a == null) {
      "null"
    } else {
      var result = "["
      val len = ops.length(a)
      var i = 0
      while (i != len) {
        if (i != 0)
          result += ", "
        result += ops.get(a, i)
        i += 1
      }
      result + "]"
    }
  }

  def deepToString(a: Array[AnyRef]): String = {
    /* The following array represents a set of the `Array[AnyRef]` that have
     * already been seen in the current recursion. We use a JS array instead of
     * a full-blown `HashSet` because it will likely stay very short (its size
     * is O(h) where h is the height of the tree of non-cyclical paths starting
     * at `a`), so the cost of using `System.identityHashCode` will probably
     * outweigh the benefits of the time complexity guarantees provided by a
     * hash-set.
     */
    val seen = js.Array[Array[AnyRef]]()

    @inline def wasSeen(a: Array[AnyRef]): Boolean = {
      // JavaScript's indexOf uses `===`
      seen.asInstanceOf[js.Dynamic].indexOf(a.asInstanceOf[js.Any]).asInstanceOf[Int] >= 0
    }

    def rec(a: Array[AnyRef]): String = {
      var result = "["
      val len = a.length
      var i = 0
      while (i != len) {
        if (i != 0)
          result += ", "
        a(i) match {
          case e: Array[AnyRef]  =>
            if ((e eq a) || wasSeen(e)) {
              result += "[...]"
            } else {
              seen.push(a)
              result += rec(e)
              seen.pop()
            }

          case e: Array[Long]    => result += toString(e)
          case e: Array[Int]     => result += toString(e)
          case e: Array[Short]   => result += toString(e)
          case e: Array[Byte]    => result += toString(e)
          case e: Array[Char]    => result += toString(e)
          case e: Array[Boolean] => result += toString(e)
          case e: Array[Float]   => result += toString(e)
          case e: Array[Double]  => result += toString(e)
          case e                 => result += e // handles null
        }
        i += 1
      }
      result + "]"
    }

    if (a == null) "null"
    else rec(a)
  }

  @inline
  private def checkRangeIndices[T](a: Array[T], start: Int, end: Int)(
      implicit ops: ArrayOps[T]): Unit = {
    if (start > end)
      throw new IllegalArgumentException("fromIndex(" + start + ") > toIndex(" + end + ")")

    // bounds checks
    if (start < 0)
      ops.get(a, start)

    if (end > 0)
      ops.get(a, end - 1)
  }
}
