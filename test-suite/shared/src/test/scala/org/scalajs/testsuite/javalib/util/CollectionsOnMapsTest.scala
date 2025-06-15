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

package org.scalajs.testsuite.javalib.util

import java.{lang => jl, util => ju}

import org.junit.Test
import org.junit.Assume._
import org.scalajs.testsuite.utils.CollectionsTestBase

import org.scalajs.testsuite.utils.Platform._

import scala.reflect.ClassTag

trait CollectionsOnMapsTest extends CollectionsTestBase {

  def factory: MapFactory

  @Test def unmodifiableMap(): Unit = {
    assumeFalse("TODO: ClassCastException: xxx cannot be cast to java.lang.Integer",
        executingInPureWebAssembly)
    def test[K: ClassTag, V: ClassTag](toKey: Int => K, toValue: Int => V): Unit = {
      val map = factory.empty[K, V]
      testMapUnmodifiability(ju.Collections.unmodifiableMap[K, V](map), toKey(0),
          toValue(0))
      for (i <- range)
        map.put(toKey(i), toValue(i))
      testMapUnmodifiability(ju.Collections.unmodifiableMap[K, V](map), toKey(0),
          toValue(0))
    }

    test[jl.Integer, jl.Integer](_.toInt, _.toInt)
    test[jl.Long, String](_.toLong, _.toString)
    test[String, String](_.toString, _.toString)
    test[jl.Double, jl.Double](_.toDouble, _.toDouble)
  }
}

trait CollectionsOnSortedMapsTest extends CollectionsOnMapsTest {
  def factory: SortedMapFactory

  @Test def unmodifiableSortedMap(): Unit = {
    def test[K: ClassTag, V: ClassTag](toKey: Int => K, toValue: Int => V): Unit = {
      val sortedMap = factory.empty[K, V]
      testMapUnmodifiability(ju.Collections.unmodifiableSortedMap[K, V](sortedMap),
        toKey(0), toValue(0))
      for (i <- range)
        sortedMap.put(toKey(i), toValue(i))
      testMapUnmodifiability(ju.Collections.unmodifiableSortedMap[K, V](sortedMap),
        toKey(0), toValue(0))
    }

    test[jl.Integer, jl.Integer](_.toInt, _.toInt)
    test[jl.Long, String](_.toLong, _.toString)
    test[String, String](_.toString, _.toString)
    test[jl.Double, jl.Double](_.toDouble, _.toDouble)
  }
}

class CollectionsOnHashMapTest extends CollectionsOnMapsTest {
  def factory: MapFactory = new HashMapFactory
}

class CollectionsOnLinkedHashMapInsertionOrderTest
    extends CollectionsOnMapsTest {
  def factory: MapFactory = new LinkedHashMapFactory(false, None)
}

class CollectionsOnLinkedHashMapInsertionOrderWithLimitTest
    extends CollectionsOnMapsTest {
  def factory: MapFactory = new LinkedHashMapFactory(false, Some(50))
}

class CollectionsOnLinkedHashMapAccessOrderTest extends CollectionsOnMapsTest {
  def factory: MapFactory = new LinkedHashMapFactory(true, None)
}

class CollectionsOnLinkedHashMapAccessOrderWithLimitTest
    extends CollectionsOnMapsTest {
  def factory: MapFactory = new LinkedHashMapFactory(true, Some(50))
}
