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

import java.util.function.UnaryOperator

import ScalaOps._

trait List[E] extends SequencedCollection[E] {
  import List._

  def replaceAll(operator: UnaryOperator[E]): Unit = {
    val iter = listIterator()
    while (iter.hasNext())
      iter.set(operator.apply(iter.next()))
  }

  def sort(c: Comparator[_ >: E]): Unit = {
    val arrayBuf = toArray()
    Arrays.sort[AnyRef with E](arrayBuf.asInstanceOf[Array[AnyRef with E]], c)

    val len = arrayBuf.length

    if (this.isInstanceOf[RandomAccess]) {
      var i = 0
      while (i != len) {
        set(i, arrayBuf(i).asInstanceOf[E])
        i += 1
      }
    } else {
      var i = 0
      val iter = listIterator()
      while (i != len) {
        iter.next()
        iter.set(arrayBuf(i).asInstanceOf[E])
        i += 1
      }
    }
  }

  def get(index: Int): E
  def set(index: Int, element: E): E
  def add(index: Int, element: E): Unit
  def remove(index: Int): E
  def indexOf(o: Any): Int
  def lastIndexOf(o: Any): Int
  def listIterator(): ListIterator[E]
  def listIterator(index: Int): ListIterator[E]
  def subList(fromIndex: Int, toIndex: Int): List[E]
  def addAll(index: Int, c: Collection[_ <: E]): Boolean

  override def addFirst(e: E): Unit =
    add(0, e)

  override def addLast(e: E): Unit =
    add(e)

  override def getFirst(): E =
    if (isEmpty()) throw new NoSuchElementException()
    else get(0)

  override def getLast(): E =
    if (isEmpty()) throw new NoSuchElementException()
    else get(size() - 1)

  override def removeFirst(): E =
    if (isEmpty()) throw new NoSuchElementException()
    else remove(0)

  override def removeLast(): E =
    if (isEmpty()) throw new NoSuchElementException()
    else remove(size() - 1)

  def reversed(): List[E] = this match {
    case _: RandomAccess => new ReverseViewRandomAccess(this)
    case _               => new ReverseView(this)
  }
}

object List {
  private class ReverseView[E](underlying: List[E]) extends AbstractList[E] {
    def size(): Int =
      underlying.size()

    override def isEmpty(): scala.Boolean =
      underlying.isEmpty()

    override def contains(o: Any): scala.Boolean =
      underlying.contains(o)

    override def add(e: E): scala.Boolean = {
      underlying.addFirst(e)
      true
    }

    override def containsAll(c: Collection[_]): scala.Boolean =
      underlying.containsAll(c)

    override def removeAll(c: java.util.Collection[_]): scala.Boolean =
      underlying.removeAll(c)

    override def retainAll(c: java.util.Collection[_]): scala.Boolean =
      underlying.retainAll(c)

    override def clear(): Unit =
      underlying.clear()

    override def get(index: Int): E =
      underlying.get(size() - 1 - index)

    override def set(index: Int, element: E): E =
      underlying.set(size() - 1 - index, element)

    override def add(index: Int, element: E): Unit = {
      // no -1 here; insert before 0 in this means insert before size() in underlying
      underlying.add(size() - index, element)
    }

    override def remove(index: Int): E =
      underlying.remove(size() - 1 - index)

    override def indexOf(o: Any): Int = {
      val underlyingResult = underlying.lastIndexOf(o)
      if (underlyingResult < 0) -1
      else size() - 1 - underlyingResult
    }

    override def lastIndexOf(o: Any): Int = {
      val underlyingResult = underlying.indexOf(o)
      if (underlyingResult < 0) -1
      else size() - 1 - underlyingResult
    }

    override def listIterator(index: Int): ListIterator[E] =
      new ReverseListIterator(underlying.listIterator(size() - index), underlying)
  }

  private class ReverseViewRandomAccess[E](underlying: List[E])
      extends ReverseView[E](underlying) with RandomAccess

  private final class ReverseListIterator[E](
      underlying: ListIterator[E], underlyingList: List[E])
      extends ListIterator[E] {

    def hasNext(): Boolean = underlying.hasPrevious()
    def next(): E = underlying.previous()

    def hasPrevious(): Boolean = underlying.hasNext()
    def previous(): E = next()

    def nextIndex(): Int = underlyingList.size() - 1 - underlying.previousIndex()
    def previousIndex(): Int = underlyingList.size() - 1 - underlying.nextIndex()

    override def remove(): Unit = underlying.remove()

    def set(e: E): Unit = underlying.set(e)
    def add(e: E): Unit = underlying.add(e)
  }
}
