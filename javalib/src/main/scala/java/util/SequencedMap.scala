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

import java.util.Map.Entry

trait SequencedMap[K, V] extends Map[K, V] {
  def reversed(): SequencedMap[K, V]

  def firstEntry(): Entry[K, V] =
    iterNextOrNull(entrySet().iterator())

  def lastEntry(): Entry[K, V] =
    iterNextOrNull(reversed().entrySet().iterator())

  @inline
  private def iterNextOrNull[A](iter: Iterator[A]): A =
    if (iter.hasNext()) iter.next()
    else null.asInstanceOf[A]

  def pollFirstEntry(): Entry[K, V] =
    pollEntryFromIterator(entrySet().iterator())

  def pollLastEntry(): Entry[K, V] =
    pollEntryFromIterator(reversed().entrySet().iterator())

  @inline
  private def pollEntryFromIterator(iter: Iterator[Entry[K, V]]): Entry[K, V] = {
    if (iter.hasNext()) {
      val entry = iter.next()
      iter.remove()
      new Map.SimpleImmutableEntry(entry)
    } else {
      null
    }
  }

  def putFirst(k: K, v: V): V =
    throw new UnsupportedOperationException()

  def putLast(k: K, v: V): V =
    throw new UnsupportedOperationException()

  def sequencedKeySet(): SequencedSet[K] =
    throw new Error("todo")

  def sequencedValues(): SequencedCollection[V] =
    throw new Error("todo")

  def sequencedEntrySet(): SequencedSet[Entry[K, V]] =
    throw new Error("todo")
}
