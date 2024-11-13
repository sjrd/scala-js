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

import java.lang.Cloneable

import ScalaOps._

class LinkedList[E]() extends AbstractSequentialList[E]
    with List[E] with Deque[E] with Cloneable with Serializable {

  def this(c: Collection[_ <: E]) = {
    this()
    addAll(c)
  }

  import LinkedList._

  private var head: Node[E] = null
  private var last: Node[E] = null

  /* Inner size is represented with a Double to satisfy Collection
   * size method requirement:
   * If this collection contains more than Integer.MAX_VALUE elements,
   * returns Integer.MAX_VALUE.
   */
  private var _size: Double = 0

  override def getFirst(): E = {
    if (isEmpty())
      throw new NoSuchElementException()
    else
      peekFirst()
  }

  override def getLast(): E = {
    if (isEmpty())
      throw new NoSuchElementException()
    else
      peekLast()
  }

  override def removeFirst(): E = {
    if (isEmpty())
      throw new NoSuchElementException()

    val oldHead = head
    head = oldHead.next

    if (head ne null)
      head.prev = null
    else
      last = null

    _size -= 1
    oldHead.value
  }

  override def removeLast(): E = {
    if (isEmpty())
      throw new NoSuchElementException()

    val oldLast = last
    last = oldLast.prev

    if (last ne null)
      last.next = null
    else
      head = null

    _size -= 1
    oldLast.value
  }

  override def addFirst(e: E): Unit = {
    val oldHead = head

    head = new Node(e, next = oldHead)

    _size += 1

    if (oldHead ne null)
      oldHead.prev = head
    else
      last = head
  }

  override def addLast(e: E): Unit = {
    val oldLast = last

    last = new Node(e, prev = oldLast)

    _size += 1

    if (oldLast ne null)
      oldLast.next = last
    else
      head = last
  }

  override def contains(o: Any): Boolean =
    this.scalaOps.exists(Objects.equals(_, o))

  override def size(): Int =
    _size.toInt

  override def add(e: E): Boolean = {
    addLast(e)
    true
  }

  override def remove(o: Any): Boolean =
    _removeOccurrence(listIterator(), o)

  override def addAll(c: Collection[_ <: E]): Boolean = {
    val iter = c.iterator()
    val changed = iter.hasNext()
    while (iter.hasNext())
      addLast(iter.next())

    changed
  }

  override def clear(): Unit = {
    head = null
    last = null
    _size = 0
  }

  private def getNodeAt(index: Int): Node[E] = {
    if (index == 0) head
    else if (index == size() - 1) last
    else {
      var current: Node[E] = null
      if (index <= size() / 2) {
        current = head
        for (_ <- 0 until index)
          current = current.next
      } else {
        current = last
        for (_ <- index until (size() - 1))
          current = current.prev
      }
      current
    }
  }

  override def get(index: Int): E = {
    checkIndexInBounds(index)
    getNodeAt(index).value
  }

  override def set(index: Int, element: E): E = {
    checkIndexInBounds(index)
    val node = getNodeAt(index)
    val oldValue = node.value
    node.value = element
    oldValue
  }

  private def addNode(nextNode: Node[E], e: E): Unit = {
    if (nextNode eq head) addFirst(e)
    else if (nextNode eq null) addLast(e)
    else {
      val node = new Node(e, prev = nextNode.prev, next = nextNode)
      nextNode.prev.next = node
      nextNode.prev = node

      _size += 1
    }
  }

  override def add(index: Int, element: E): Unit = {
    checkIndexOnBounds(index)
    addNode(getNodeAt(index), element)
  }

  private def removeNode(node: Node[E]): E = {
    if (node eq head) removeFirst()
    else if (node eq last) removeLast()
    else {
      node.prev.next = node.next
      node.next.prev = node.prev

      _size -= 1

      node.value
    }
  }

  override def remove(index: Int): E = {
    checkIndexInBounds(index)
    removeNode(getNodeAt(index))
  }

  def peek(): E =
    peekFirst()

  def element(): E =
    getFirst()

  def poll(): E =
    pollFirst()

  def remove(): E =
    removeFirst()

  def offer(e: E): Boolean =
    offerLast(e)

  def offerFirst(e: E): Boolean = {
    addFirst(e)
    true
  }

  def offerLast(e: E): Boolean = {
    addLast(e)
    true
  }

  def peekFirst(): E =
    if (head eq null) null.asInstanceOf[E]
    else head.value

  def peekLast(): E =
    if (last eq null) null.asInstanceOf[E]
    else last.value

  def pollFirst(): E =
    if (isEmpty()) null.asInstanceOf[E]
    else removeFirst()

  def pollLast(): E =
    if (isEmpty()) null.asInstanceOf[E]
    else removeLast()

  def push(e: E): Unit =
    addFirst(e)

  def pop(): E =
    removeFirst()

  private def _removeOccurrence(iter: Iterator[E], o: Any): Boolean = {
    var changed = false
    while (iter.hasNext() && !changed) {
      if (Objects.equals(iter.next(), o)) {
        iter.remove()
        changed = true
      }
    }

    changed
  }

  def removeFirstOccurrence(o: Any): Boolean =
    _removeOccurrence(iterator(), o)

  def removeLastOccurrence(o: Any): Boolean =
    _removeOccurrence(descendingIterator(), o)

  override def listIterator(index: Int): ListIterator[E] = {
    checkIndexOnBounds(index)
    new ListIterator[E] {

      private var last: Double = -1
      private var i: Double = index

      private var currentNode: Node[E] =
        if (index == size()) null else
        getNodeAt(index)

      private var lastNode: Node[E] =
        if (currentNode ne null) null else
        LinkedList.this.last

      def hasNext(): Boolean =
        i < size()

      def next(): E = {
        if (i >= size())
          throw new NoSuchElementException()

        last = i
        i += 1

        lastNode = currentNode
        currentNode = currentNode.next

        lastNode.value
      }

      def hasPrevious(): Boolean =
        i > 0

      def previous(): E = {
        if (!hasPrevious())
          throw new NoSuchElementException()

        i -= 1
        last = i

        if (currentNode eq null)
          currentNode = LinkedList.this.last
        else
          currentNode = currentNode.prev

        lastNode = currentNode

        lastNode.value
      }

      def nextIndex(): Int = i.toInt

      def previousIndex(): Int = (i - 1).toInt

      override def remove(): Unit = {
        checkThatHasLast()

        if (currentNode eq null) {
          removeLast()
          lastNode = LinkedList.this.last
        } else {
          removeNode(lastNode)
        }

        if (last < i) {
          i -= 1
        }

        last = -1
      }

      def set(e: E): Unit = {
        checkThatHasLast()
        lastNode.value = e
      }

      def add(e: E): Unit = {
        if (currentNode eq null) {
          addLast(e)
          lastNode = LinkedList.this.last
        } else {
          addNode(currentNode, e)
        }

        i += 1
        last = -1
      }

      private def checkThatHasLast(): Unit = {
        if (last == -1)
          throw new IllegalStateException()
      }
    }
  }

  def descendingIterator(): Iterator[E] =
    reversed().iterator()

  override def clone(): AnyRef =
    new LinkedList[E](this)

  override def reversed(): LinkedList[E] =
    new ReverseView(this)
}

object LinkedList {

  protected[LinkedList] final class Node[T](
      var value: T,
      var prev: Node[T] = null,
      var next: Node[T] = null)

  // This is horrible; the JavaDoc specification made us do it
  private class ReverseView[E](underlying: LinkedList[E]) extends LinkedList[E] {
    override def size(): Int =
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

    override def peekFirst(): E =
      underlying.peekLast()

    override def peekLast(): E =
      underlying.peekFirst()

    override def listIterator(index: Int): ListIterator[E] =
      new List.ReverseListIterator(underlying.listIterator(size() - index), underlying)

    override def addFirst(e: E): Unit =
      underlying.addLast(e)

    override def addLast(e: E): Unit =
      underlying.addFirst(e)

    override def getFirst(): E =
      underlying.getLast()

    override def getLast(): E =
      underlying.getFirst()

    override def removeFirst(): E =
      underlying.removeLast()

    override def removeLast(): E =
      underlying.removeFirst()

    override def reversed(): LinkedList[E] =
      underlying
  }

}
