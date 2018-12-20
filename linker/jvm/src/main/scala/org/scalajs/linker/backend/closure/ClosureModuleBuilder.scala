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

package org.scalajs.linker.backend.closure

import org.scalajs.ir
import ir.Position.NoPosition

import org.scalajs.linker.backend.javascript.{Trees => js}
import org.scalajs.linker.backend.javascript.Trees.Tree
import org.scalajs.linker.backend.javascript.JSBuilder

import com.google.javascript.rhino._
import com.google.javascript.jscomp._

import scala.collection.mutable

import java.net.URI

private[closure] class ClosureModuleBuilder(compiler: Compiler,
    isModule: Boolean, relativizeBaseURI: Option[URI] = None)
    extends JSBuilder {

  private val transformer = new ClosureAstTransformer(relativizeBaseURI)
  private val treeBuf = mutable.ListBuffer.empty[Node]
  private val module = new JSModule("Scala.js")

  def addJSTree(tree: Tree): Unit = {
    tree match {
      case js.Block(stats) =>
        for (stat <- transformer.transformBlockStats(stats)(NoPosition))
          treeBuf += stat
      case js.Skip() =>
        // ignore
      case _ =>
        treeBuf += transformer.transformStat(tree)(NoPosition)
    }
  }

  def addStatement(originalLocation: URI, code: String): Unit = {
    /* Parse the code and turn it into a list of Nodes that we can add to our
     * treeBuf. This is particularly important for ES modules, since the parsed
     * code must be part of the MODULE_BODY node for proper scoping.
     */
    val path = URIUtil.sourceURIToString(relativizeBaseURI, originalLocation)
    val sourceFile = SourceFile.fromCode(path, code)
    val ast = new JsAst(sourceFile)
    val root = ast.getAstRoot(compiler)
    assert(root.isScript(), root.getToken)

    val parseResult = root.getProp(Node.PARSE_RESULTS).asInstanceOf[JsAst.ParseResult]
    if (parseResult != null && !parseResult.errors.isEmpty()) {
      // Ouch, the code could not be parsed. This should not happen.
      val msg = new java.lang.StringBuilder(
          "There was a parse error while parsing code generated internally " +
          "by the Scala.js linker. This is a bug. The error was")
      parseResult.errors.forEach(new java.util.function.Consumer[JsAst.RhinoError] {
        def accept(error: JsAst.RhinoError): Unit = {
          msg.append(
              s"\n${error.sourceName}:${error.line}:${error.lineOffset}: ${error.message}")
        }
      })
      throw new AssertionError(msg.toString())
    }

    var child = root.removeFirstChild()
    while (child != null) {
      treeBuf += child
      child = root.removeFirstChild()
    }
  }

  def complete(): Unit = flushTrees()

  def result(): JSModule = {
    complete()
    module
  }

  private def flushTrees(): Unit = {
    import transformer.setNodePosition

    if (treeBuf.nonEmpty) {
      val script = setNodePosition(new Node(Token.SCRIPT), NoPosition)
      if (isModule) {
        val moduleBody = setNodePosition(new Node(Token.MODULE_BODY), NoPosition)
        for (tree <- treeBuf)
          moduleBody.addChildToBack(tree)
        script.addChildToBack(moduleBody)
      } else {
        for (tree <- treeBuf)
          script.addChildToBack(tree)
      }
      treeBuf.clear()

      val ast = new ClosureModuleBuilder.ScalaJSSourceAst(script)
      module.add(new CompilerInput(ast, ast.getInputId(), false))
    }
  }
}

private object ClosureModuleBuilder {
  // Dummy Source AST class

  private class ScalaJSSourceAst(root: Node) extends SourceAst {
    def getAstRoot(compiler: AbstractCompiler): Node = root
    def clearAst(): Unit = () // Just for GC. Nonsensical here.
    def getInputId(): InputId = root.getInputId()
    def getSourceFile(): SourceFile =
      root.getStaticSourceFile().asInstanceOf[SourceFile]
    def setSourceFile(file: SourceFile): Unit =
      if (getSourceFile() ne file) throw new IllegalStateException
  }
}
