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
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.|

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.mutable.ArrayBuffer

import org.junit.Assert._
import org.junit.Assume._
import org.junit.Test

import org.scalajs.junit.async._

class JSAsyncAwaitTest {
  @Test
  def basic(): AsyncResult = await {
    val buf = new ArrayBuffer[String]()

    val px = js.Promise.resolve[Int](5)

    buf += "before"
    val p = js.async {
      buf += "start async"
      val x = js.await(px)
      buf += s"got x: $x"
      x + 13
    }
    buf += "after"

    assertArrayEquals(
        Array[AnyRef]("before", "start async", "after"),
        buf.toArray[AnyRef])

    p.toFuture.map { (result: Int) =>
      assertEquals(18, result)

      assertArrayEquals(
          Array[AnyRef]("before", "start async", "after", "got x: 5"),
          buf.toArray[AnyRef])
    }
  }

  @Test
  def loop(): AsyncResult = await {
    val buf = new ArrayBuffer[String]()

    val inputs = (1 to 5).toList
    val inputPromises = inputs.map(i => js.Promise.resolve[Int](i))

    buf += "before"
    val p = js.async {
      buf += "start async"
      var rest = inputPromises
      while (!rest.isEmpty) {
        val x = js.await(rest.head)
        buf += x.toString()
        rest = rest.tail
      }
      buf += "done"
    }
    buf += "after"

    assertArrayEquals(
        Array[AnyRef]("before", "start async", "after"),
        buf.toArray[AnyRef])

    p.toFuture.map { _ =>
      assertArrayEquals(
          ("before" :: "start async" :: "after" :: inputs.map(_.toString()) ::: "done" :: Nil).toArray[AnyRef],
          buf.toArray[AnyRef])
    }
  }

  @Test
  def tryCatch(): AsyncResult = await {
    val successfulInput = js.Promise.resolve[Int](42)
    val failedInput: js.Promise[Int] = js.Promise.reject(new IllegalArgumentException("nope"))

    val p = js.async {
      val result1 = try {
        js.await(successfulInput)
      } catch {
        case e: IllegalArgumentException =>
          throw new AssertionError(e)
      }
      val result2 = try {
        js.await(failedInput)
        throw new AssertionError("awaiting a failed Promise did not throw")
      } catch {
        case e: IllegalArgumentException =>
          56
      }
      (result1, result2)
    }

    p.toFuture.map { case (result1, result2) =>
      assertEquals(42, result1)
      assertEquals(56, result2)
    }
  }

  @Test
  def tryFinally(): AsyncResult = await {
    val successfulInput = js.Promise.resolve[Int](42)
    val failedInput: js.Promise[Int] = js.Promise.reject(new IllegalArgumentException("nope"))

    val buf = new ArrayBuffer[String]()

    val p: js.Promise[Int] = js.async {
      val result1 = try {
        js.await(successfulInput)
      } finally {
        buf += "first"
      }
      assertEquals(42, result1)
      val result2 = try {
        js.await(failedInput)
        throw new AssertionError("awaiting a failed Promise did not throw")
      } finally {
        buf += "second"
      }
      throw new AssertionError("did not rethrow after the finally")
    }

    p.toFuture.failed.map { e =>
      assertTrue(e.toString(), e.isInstanceOf[IllegalArgumentException])
      assertArrayEquals(
          Array[AnyRef]("first", "second"),
          buf.toArray[AnyRef])
    }
  }

  private val instanceFieldPromise = js.Promise.resolve[Int](654)

  @noinline
  def publicInstanceMethod(buf: ArrayBuffer[String], px: js.Promise[Int]): js.Promise[Int] = js.async {
    buf += "publicInstanceMethod"
    val x = js.await(px)
    val y = js.await(instanceFieldPromise)
    val z = (x + y) * 5
    buf += s"publicInstanceMethod: $z"
    z
  }

  /* Optimizable cases where js.async { ... } is the full body of a method or closure. */
  @Test
  def fullMethodBodyAsync(): AsyncResult = await {
    val buf = new ArrayBuffer[String]()

    // takes both an explicit param (px) and an implicit capture (buf)
    @noinline
    def privateStaticMethod(px: js.Promise[Int]): js.Promise[Int] = js.async {
      buf += "privateStaticMethod"
      val z = js.await(px) * 2
      buf += s"privateStaticMethod: $z"
      z
    }

    @noinline
    def privateInstanceMethod(px: js.Promise[Int]): js.Promise[Int] = js.async {
      buf += "privateInstanceMethod"
      val x = js.await(px)
      val y = js.await(instanceFieldPromise)
      val z = (x + y) * 3
      buf += s"privateInstanceMethod: $z"
      z
    }

    @noinline
    def arrowClosure: js.Function1[js.Promise[Int], js.Promise[Int]] = { px =>
      js.async {
        buf += "arrowClosure"
        val x = js.await(px)
        val y = js.await(instanceFieldPromise)
        val z = (x + y) * 7
        buf += s"arrowClosure: $z"
        z
      }
    }

    @noinline
    def functionClosure: js.ThisFunction0[js.Promise[Int], js.Promise[Int]] = { px =>
      js.async {
        buf += "functionClosure"
        val x = js.await(px)
        val y = js.await(instanceFieldPromise)
        val z = (x + y) * 11
        buf += s"functionClosure: $z"
        z
      }
    }

    val px = js.Promise.resolve[Int](17)

    buf += "before"
    val p = {
      val pa = privateStaticMethod(px)
      val pb = privateInstanceMethod(pa)
      val pc = publicInstanceMethod(buf, pb)
      val pd = arrowClosure(pc)
      val pe = functionClosure(pd)
      pe
    }
    buf += "after"

    assertArrayEquals(
        Array[AnyRef](
          "before",
          "privateStaticMethod",
          "privateInstanceMethod",
          "publicInstanceMethod",
          "arrowClosure",
          "functionClosure",
          "after"
        ),
        buf.toArray[AnyRef])

    p.toFuture.map { (result: Int) =>
      assertEquals(((((((((17*2)+654)*3)+654)*5)+654)*7)+654)*11, result)

      assertArrayEquals(
          Array[AnyRef](
            "before",
            "privateStaticMethod",
            "privateInstanceMethod",
            "publicInstanceMethod",
            "arrowClosure",
            "functionClosure",
            "after",
            "privateStaticMethod: 34",
            "privateInstanceMethod: 2064",
            "publicInstanceMethod: 13590",
            "arrowClosure: 99708",
            "functionClosure: 1103982"
          ),
          buf.toArray[AnyRef])
    }
  }
}
