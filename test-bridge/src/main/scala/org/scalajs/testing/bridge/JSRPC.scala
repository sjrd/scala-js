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
import scala.scalajs.js.annotation._
import scala.scalajs.LinkingInfo._
import scala.scalajs.LinkingInfo.ModuleKind.MinimalWasmModule
import scala.scalajs.wasm.annotation._

import scala.concurrent.duration._

import org.scalajs.testing.common.RPCCore

/** JS RPC Core. Uses `scalajsCom`. */
private[bridge] final object JSRPC extends RPCCore {
  linkTimeIf(moduleKind == MinimalWasmModule) {
    ()
  } {
    Com.init(handleMessage _)
  }

  override protected def send(msg: String): Unit = {
    linkTimeIf(moduleKind == MinimalWasmModule) {
      val codeUnits = new Array[Short](msg.length)
      var i = 0
      while (i != codeUnits.length) {
        codeUnits(i) = msg.charAt(i).toShort
        i += 1
      }
      WasmCom.send(codeUnits)
    } {
      Com.send(msg)
    }
  }

  @WasmExport("scalajs:testing/com/receive")
  def receive(msg: Array[Short]): Unit = {
    val chars = new Array[Char](msg.length)
    var i = 0
    while (i != chars.length) {
      chars(i) = msg(i).toChar
      i += 1
    }

    handleMessage(new String(chars))
  }

  @js.native
  @JSGlobal("scalajsCom")
  private object Com extends js.Object {
    def init(onReceive: js.Function1[String, Unit]): Unit = js.native
    def send(msg: String): Unit = js.native
    // We support close, but do not use it. The JS side just terminates.
    // def close(): Unit = js.native
  }

  private object WasmCom {
    @WasmImport("scalajs:testing/com", "send")
    def send(msg: Array[Short]): Unit = scala.scalajs.wasm.native
  }
}
