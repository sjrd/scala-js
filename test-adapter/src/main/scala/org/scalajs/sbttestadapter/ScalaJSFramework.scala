/*                     __                                               *\
**     ________ ___   / /  ___      __ ____  Scala.js sbt plugin        **
**    / __/ __// _ | / /  / _ | __ / // __/  (c) 2013, LAMP/EPFL        **
**  __\ \/ /__/ __ |/ /__/ __ |/_// /_\ \    http://scala-js.org/       **
** /____/\___/_/ |_/____/_/ | |__/ /____/                               **
**                          |/____/                                     **
\*                                                                      */


package org.scalajs.testadapter

import org.scalajs.core.ir.Utils.escapeJS

import org.scalajs.core.tools.io._
import org.scalajs.core.tools.json._
import org.scalajs.core.tools.logging._
import org.scalajs.core.tools.linker.backend.ModuleIdentifier
import org.scalajs.jsenv._

import sbt.testing.{Logger => _, _}

final class ScalaJSFramework(
    private[testadapter] val frameworkName: String,
    private[testadapter] val libEnv: ComJSEnv,
    private[testadapter] val moduleIdentifier: ModuleIdentifier,
    private[testadapter] val logger: Logger,
    private[testadapter] val jsConsole: JSConsole
) extends Framework {

  def this(frameworkName: String, libEnv: ComJSEnv, logger: Logger,
      jsConsole: JSConsole) = {
    this(frameworkName, libEnv, ModuleIdentifier.NoModule, logger, jsConsole)
  }

  private[this] val frameworkInfo = fetchFrameworkInfo()

  private[this] var _isRunning = false

  val name: String = frameworkInfo.name

  def fingerprints: Array[Fingerprint] = frameworkInfo.fingerprints.toArray

  def runner(args: Array[String], remoteArgs: Array[String],
      testClassLoader: ClassLoader): Runner = synchronized {

    if (_isRunning) {
      throw new IllegalStateException(
        "Scala.js test frameworks do not support concurrent runs")
    }

    _isRunning = true

    new ScalaJSRunner(this, args, remoteArgs)
  }

  private[testadapter] def runDone(): Unit = synchronized(_isRunning = false)

  private def fetchFrameworkInfo() = {
    val runner = libEnv.comRunner(frameworkInfoLauncher)
    runner.start(logger, jsConsole)

    try {
      val msg = readJSON(runner.receive())
      fromJSON[FrameworkInfo](msg)
    } finally {
      runner.close()
      runner.await(VMTermTimeout)
    }
  }

  private def frameworkInfoLauncher = {
    val prefix = optionalExportsNamespacePrefix
    val name = jsonToString(frameworkName.toJSON)
    val code = s"""
      new ${prefix}org.scalajs.testinterface.internal.InfoSender($name).initAndSend();
    """
    new MemVirtualJSFile(s"testFrameworkInfo.js").withContent(code)
  }

  private[testadapter] def optionalExportsNamespacePrefix: String = {
    // !!! DUPLICATE code with ScalaJSPlugin.makeExportsNamespaceExpr
    moduleIdentifier match {
      case ModuleIdentifier.NoModule =>
        ""

      case ModuleIdentifier.NodeJSModule(moduleIdent) =>
        s"""require("${escapeJS(moduleIdent)}").""" // note the final '.'
    }
  }
}
