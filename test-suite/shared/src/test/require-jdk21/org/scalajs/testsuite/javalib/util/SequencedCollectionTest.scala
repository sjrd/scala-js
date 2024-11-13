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

import org.junit.Test
import org.junit.Assert._

import java.{util => ju}

class SequencedCollectionTest {

  @Test def knownSequencedCollections(): Unit = {
    def test(expected: Boolean, value: Any): Unit = {
      assertEquals("" + value, expected, value.isInstanceOf[ju.SequencedCollection[_]])
    }

    test(true, new ju.LinkedList[String]())
    test(true, new ju.ArrayList[String]())
    test(true, new ju.LinkedHashSet[String]())
    test(true, new ju.TreeSet[String]())
    test(true, new ju.ArrayDeque[String]())

    test(false, new ju.HashSet[String]())
    test(false, new ju.HashMap[String, String]())
    test(false, new ju.LinkedHashMap[String, String]())
    test(false, new ju.TreeMap[String, String]())
  }

}
