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

package scala.scalajs.concurrent

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executor

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.LinkingInfo
import scala.scalajs.LinkingInfo.linkTimeIf

object QueueExecutionContext {
  def timeouts(): ExecutionContextExecutor =
    new TimeoutsExecutionContext

  def promises(): ExecutionContextExecutor =
    new PromisesExecutionContext

  def single(): ExecutionContextExecutor =
    new SingleThreadedExecutionContext

  def apply(): ExecutionContextExecutor =
    linkTimeIf(LinkingInfo.targetPureWasm) {
      single()
    } {
      if (js.typeOf(js.Dynamic.global.Promise) == "undefined") timeouts()
      else promises()
    }

  private final class SingleThreadedExecutionContext extends ExecutionContextExecutor {

    def execute(runnable: Runnable): Unit =
      runnable.run()

    def reportFailure(t: Throwable): Unit =
      t.printStackTrace()
  }

  private final class TimeoutsExecutionContext extends ExecutionContextExecutor {
    def execute(runnable: Runnable): Unit = {
      js.Dynamic.global.setTimeout({ () =>
        try {
          runnable.run()
        } catch {
          case t: Throwable => reportFailure(t)
        }
      }, 0)
    }

    def reportFailure(t: Throwable): Unit =
      t.printStackTrace()
  }

  private final class PromisesExecutionContext extends ExecutionContextExecutor {
    private val resolvedUnitPromise = js.Promise.resolve[Unit](())

    def execute(runnable: Runnable): Unit = {
      resolvedUnitPromise.`then` { (_: Unit) =>
        try {
          runnable.run()
        } catch {
          case t: Throwable => reportFailure(t)
        }
        (): Unit | js.Thenable[Unit]
      }
    }

    def reportFailure(t: Throwable): Unit =
      t.printStackTrace()
  }
}
