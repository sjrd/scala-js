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

trait SequencedCollection[E] extends Collection[E] {
  def reversed(): SequencedCollection[E]

  def addFirst(e: E): Unit =
    throw new UnsupportedOperationException()

  def addLast(e: E): Unit =
    throw new UnsupportedOperationException()

  def getFirst(): E =
    iterator().next()

  def getLast(): E =
    reversed().iterator().next()

  def removeFirst(): E = {
    val iter = iterator()
    val elem = iter.next()
    iter.remove()
    elem
  }

  def removeLast(): E = {
    val iter = reversed().iterator()
    val elem = iter.next()
    iter.remove()
    elem
  }
}
