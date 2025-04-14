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

package org.scalajs.testsuite.jsinterop

import scala.scalajs.js
import scala.scalajs.js.annotation._

import org.junit.Assert._
import org.junit.Test

import org.scalajs.testsuite.utils.AssertThrows.assertThrows

class GeneratorTest {
  import GeneratorTest._

  @noinline
  private def assertNotDone(expected: Any, entry: js.Generator.Entry[Any, Any]): Unit = {
    assertFalse(entry.done)
    assertEquals(expected, entry.value)
  }

  @noinline
  private def assertDone(expected: Any, entry: js.Generator.Entry[Any, Any]): Unit = {
    assertTrue(entry.done)
    assertEquals(expected, entry.value)
  }

  @noinline
  private def assertDone(entry: js.Generator.Entry[Any, Any]): Unit =
    assertDone((), entry)

  @noinline
  private def assertPostDone(generator: js.Generator[Any, Any, Nothing]): Unit = {
    // once done, we can send anything to next()
    val gen = generator.asInstanceOf[js.Generator[Any, Any, Any]]

    for (_ <- 0 until 2) {
      assertDone(gen.next())
      assertDone(gen.next(42))
      assertDone("explicit result post done", gen.`return`("explicit result post done"))
      assertThrows(classOf[TestException], gen.`throw`(newTestException()))
    }
  }

  @noinline
  private def withExpectedSideEffects[A, B](expected: A*)(body: (A => Unit) => B): B = {
    val tracker = List.newBuilder[A]
    val result = body(effect => tracker += effect)
    val actual = tracker.result()

    // We don't use assertArrayEquals because its output is not helpful enough in this context
    if (!actual.sameElements(expected)) {
      fail(
          "Side effects mismatch.\nExpected:\n" +
          expected.map("* " + _).mkString("\n") +
          "\nActual:\n" +
          actual.map("* " + _).mkString("\n") +
          "\n")
    }

    result
  }

  @Test
  def testBasicEmpty(): Unit = {
    withExpectedSideEffects(
      "ready",
      "start"
    ) { sideEffect =>
      val gen = js.Generator[Nothing, Unit, Unit] { implicit ev =>
        sideEffect("start")
      }
      sideEffect("ready")

      assertDone(gen.next())
      assertPostDone(gen)
    }
  }

  @Test
  def testBasicSingleton(): Unit = {
    withExpectedSideEffects(
      "ready",
      "start",
      "received 5",
      "end",
      "received done"
    ) { sideEffect =>
      val gen = js.Generator[Int, Unit, Unit] { implicit ev =>
        sideEffect("start")
        js.Generator.`yield`(5)
        sideEffect("end")
      }
      sideEffect("ready")

      assertNotDone(5, gen.next())
      sideEffect("received 5")
      assertDone(gen.next())
      sideEffect("received done")
      assertPostDone(gen)
    }
  }

  @Test
  def testBasicThree(): Unit = {
    val gen = js.Generator[Int, Unit, Unit] { implicit ev =>
      js.Generator.`yield`(5)
      js.Generator.`yield`(7)
      js.Generator.`yield`(11)
    }

    assertNotDone(5, gen.next())
    assertNotDone(7, gen.next())
    assertNotDone(11, gen.next())
    assertDone(gen.next())
    assertPostDone(gen)
  }
}

object GeneratorTest {
  private final class TestException(msg: String) extends Exception(msg)

  private def newTestException(msg: String = null): TestException =
    new TestException(msg)

  private def throwTestException(msg: String = null): Nothing =
    throw new TestException(msg)
}
