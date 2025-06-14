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

package org.scalajs.testsuite.javalib.util.function

import org.junit.Assert._
import org.junit.Test
import org.junit.Assume._

import java.util.function._

import org.scalajs.testsuite.utils.Platform._

class DoubleFunctionTest {
  @Test def testApply(): Unit = {
    assumeFalse("float/double to string", executingInPureWebAssembly)
    val f = new DoubleFunction[String] {
      override def apply(value: Double): String = s"${value}d"
    }
    assertEquals(f.apply(0.5), "0.5d")
    assertEquals(f.apply(3.3), "3.3d")
  }
}
