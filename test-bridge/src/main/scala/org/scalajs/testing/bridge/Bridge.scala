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

package org.scalajs.testing.bridge

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSGlobalScope, JSName}
import scala.scalajs.LinkingInfo.linkTimeIf
import scala.scalajs.LinkingInfo

import org.scalajs.testing.common._

private[bridge] object Bridge {
  // Called via org.scalajs.testing.adapter.testAdapterInitializer
  def start(): Unit = mode match {
    case TestBridgeMode.FullBridge        => TestAdapterBridge.start()
    case TestBridgeMode.HTMLRunner(tests) =>
      linkTimeIf(LinkingInfo.targetPureWasm) { // shouldn't reach here
        throw new AssertionError("The HTML runner is not supported in pure Wasm.")
      } {
        HTMLRunner.start(tests)
      }
  }

  private def mode = {
    linkTimeIf(!LinkingInfo.targetPureWasm) {
      if (js.typeOf(js.Dynamic.global.__ScalaJSTestBridgeMode) == "undefined") {
        TestBridgeMode.FullBridge
      } else {
        val modeStr =
          js.Dynamic.global.__ScalaJSTestBridgeMode.asInstanceOf[String]
        Serializer.deserialize[TestBridgeMode](modeStr)
      }
    } {
      TestBridgeMode.FullBridge
    }
  }
}
