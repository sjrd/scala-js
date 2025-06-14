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

package java.util.regex

import scala.annotation.{switch, tailrec}

import java.util.{ArrayList, HashMap}
import java.util.function.Supplier

private[regex] object WasmEngine extends Engine {
  type Dictionary[V] = HashMap[String, V]

  type RegExp = WasmRegExp
  type ExecResult = WasmExecResult
  type IndicesArray = Array[Int] // flattened pairs at (2*i, 2*i + 1)

  @inline
  def dictEmpty[V](): Dictionary[V] =
    new HashMap[String, V]()

  @inline
  def dictSet[V](dict: Dictionary[V], key: String, value: V): Unit =
    dict.put(key, value)

  @inline
  def dictContains[V](dict: Dictionary[V], key: String): Boolean =
    dict.containsKey(key)

  @inline
  def dictRawApply[V](dict: Dictionary[V], key: String): V =
    dict.get(key)

  @inline
  def dictGetOrElse[V](dict: Dictionary[V], key: String)(default: Supplier[V]): V = {
    val result = dict.get(key)
    if (result != null) result
    else default.get()
  }

  @inline
  def dictGetOrElseUpdate[V](dict: Dictionary[V], key: String)(default: Supplier[V]): V =
    dict.computeIfAbsent(key, _ => default.get())

  @inline
  def featureTest(flags: String): Boolean = true

  def compile(pattern: String, flags: String): RegExp = {
    val parser = new Parser(pattern)
    val root = parser.parseTopLevel()
    val groupNodeMap = parser.groupNodeMap.toArray(new Array[Node](parser.groupNodeMap.size()))
    new WasmRegExp(root, groupNodeMap, flags)
  }

  @inline
  def getLastIndex(regexp: RegExp): Int =
    regexp.lastIndex

  @inline
  def setLastIndex(regexp: RegExp, newLastIndex: Int): Unit =
    regexp.lastIndex = newLastIndex

  @inline
  def exec(regexp: RegExp, string: String): ExecResult =
    regexp.exec(string)

  @inline
  def getIndex(result: ExecResult): Int =
    result.index

  @inline
  def getInput(result: ExecResult): String =
    result.input

  @inline
  def getIndices(result: ExecResult): IndicesArray =
    result.indices

  @inline
  def setIndices(result: ExecResult, indices: IndicesArray): Unit =
    result.indices = indices

  @inline
  def getGroup(result: ExecResult, group: Int): String =
    result.getGroup(group)

  @inline
  def getStart(indices: IndicesArray, group: Int): Int =
    indices(2 * group)

  @inline
  def getEnd(indices: IndicesArray, group: Int): Int =
    indices(2 * group + 1)

  @inline
  private def assert(condition: Boolean, message: String): Unit = {
    if (!condition)
      throw new AssertionError(message)
  }

  final class WasmExecResult(val input: String, val index: Int, val groups: Array[String]) {
    var indices: IndicesArray = _

    def getGroup(group: Int): String = ""
  }

  private final class Captures(n: Int) {
    private val ranges = new Array[Int](n * 2)

    @inline
    def get(group: Int): (Int, Int) =
      (ranges(group * 2), ranges(group * 2 + 1))

    def set(group: Int, start: Int, end: Int): Captures = {
      val result = new Captures(n)
      System.arraycopy(this.ranges, 0, result.ranges, 0, ranges.length)
      result.ranges(group * 2) = start
      result.ranges(group * 2 + 1) = end
      result
    }
  }

  final class WasmRegExp(root: Node, groupNodeMap: Array[Node], flags: String) {
    @inline def hasFlag(flag: Char): Boolean = flags.indexOf(flag) >= 0

    assert(hasFlag('u') && hasFlag('s'), s"Illegal flags: $flags")

    private val global = hasFlag('g')
    private val sticky = hasFlag('y')
    private val hasIndices = hasFlag('d')
    val unicodeCaseInsensitive = hasFlag('i')

    val capturingGroupsCount = groupNodeMap.length - 1

    var lastIndex: Int = 0

    def exec(input: String): WasmExecResult = {
      // https://tc39.es/ecma262/multipage/text-processing.html#sec-regexpbuiltinexec

      // Step 1
      val length = input.length()

      // Step 2
      var lastIndex = this.lastIndex

      // Step 7: If global is false and sticky is false, set lastIndex to 0.
      if (!global && !sticky)
        lastIndex = 0

      // Step 13
      @inline @tailrec
      def loop(): MatchState = {
        if (lastIndex > length) {
          if (global || sticky)
            lastIndex = 0
          null
        } else {
          val initState = new MatchState(input, lastIndex, new Captures(capturingGroupsCount + 1))
          val r = root(this, initState, forward = true)

          if (r == null) { // FAILURE
            if (sticky) {
              lastIndex = 0
              null
            } else {
              lastIndex =
                if (lastIndex == input.length()) lastIndex + 1
                else input.offsetByCodePoints(lastIndex, 1)
              loop()
            }
          } else {
            r
          }
        }
      }

      val r = loop()

      if (r == null) {
        null
      } else {
        // Step 14
        val e = r.endIndex

        // Step 16
        if (global || sticky)
          this.lastIndex = e

        val groups = new Array[String](groupNodeMap.length)
        groups(0) = input.substring(lastIndex, e)
        new WasmExecResult(input, lastIndex, groups)
      }
    }

    private def matcher(): MatchState =
      null
  }

  private final class MatchState(val input: String, val endIndex: Int, val captures: Captures)

  /** Node of the regex tree. */
  private abstract class Node {
    def apply(rer: WasmRegExp, state: MatchState, forward: Boolean): MatchState
  }

  /** A numbered group. */
  private final class GroupNode(val number: Int, val inner: Node) extends Node {
    def apply(rer: WasmRegExp, state: MatchState, forward: Boolean): MatchState =
      throw new UnsupportedOperationException()
  }

  /** A look-around group of the form `(?= )`, `(?! )`, `(?<= )` or `(?<! )`.
   *
   *  Look-aheads propagate from left to right, while look-behinds propagate
   *  from right to left.
   */
  private final class LookAroundNode(isLookBehind: Boolean, indicator: String, inner: Node)
      extends Node {

    def apply(rer: WasmRegExp, state: MatchState, forward: Boolean): MatchState =
      throw new UnsupportedOperationException()
  }

  /** A repeated node. */
  private final class RepeatedNode(val inner: Node, val repeater: String)
      extends Node {

    def apply(rer: WasmRegExp, state: MatchState, forward: Boolean): MatchState =
      throw new UnsupportedOperationException()
  }

  /** A leaf regex, without any subgroups. */
  private final class LeafRegexNode(val regex: String) extends Node {
    def apply(rer: WasmRegExp, state: MatchState, forward: Boolean): MatchState = {
      import state._

      regex match {
        case "" =>
          state

        case "^" =>
          if (state.endIndex == 0)
            state
          else
            null

        case "$" =>
          if (state.endIndex == input.length())
            state
          else
            null

        case "." =>
          if (forward && endIndex < input.length())
            new MatchState(input, input.offsetByCodePoints(endIndex, 1), captures)
          else if (!forward && endIndex > 0)
            new MatchState(input, input.offsetByCodePoints(endIndex, -1), captures)
          else
            null

        case "\\b" | "\\B" =>
          throw new UnsupportedOperationException(regex)

        case _ if regex.startsWith("\\p") || regex.startsWith("\\P") =>
          throw new UnsupportedOperationException(regex)

        case _ =>
          val lit =
            if (regex.charAt(0) == '\\') regex.substring(1)
            else regex

          if (forward) {
            if (input.startsWith(lit, endIndex))
              new MatchState(input, endIndex + lit.length(), captures)
            else
              null
          } else {
            val startIndex = endIndex - lit.length()
            if (startIndex >= 0 && input.startsWith(lit, startIndex))
              new MatchState(input, startIndex, captures)
            else
              null
          }
      }
    }
  }

  /** A back reference. */
  private final class BackReferenceNode(val groupNumber: Int) extends Node {
    def apply(rer: WasmRegExp, state: MatchState, forward: Boolean): MatchState =
      throw new UnsupportedOperationException()
  }

  /** A sequence of consecutive nodes. */
  private final class SequenceNode(val sequence: ArrayList[Node]) extends Node {
    def apply(rer: WasmRegExp, state: MatchState, forward: Boolean): MatchState = {
      if (forward) {
        val iter = sequence.iterator()
        var m = iter.next()(rer, state, forward)
        while (m != null && iter.hasNext())
          m = iter.next()(rer, m, forward)
        m
      } else {
        val iter = sequence.listIterator(sequence.size())
        var m = iter.previous()(rer, state, forward)
        while (m != null && iter.hasPrevious())
          m = iter.previous()(rer, m, forward)
        m
      }
    }
  }

  /** An alternatives node such as `ab|cd`. */
  private final class AlternativesNode(val alternatives: ArrayList[Node])
      extends Node {

    def apply(rer: WasmRegExp, state: MatchState, forward: Boolean): MatchState = {
      var m = alternatives.get(0)(rer, state, forward)
      var i = 1
      while (m == null && i != alternatives.size()) {
        m = alternatives.get(i)(rer, state, forward)
        i += 1
      }
      m
    }
  }

  // !!! Copy-paste from IndicesBuilder

  private final class Parser(pattern0: String) {
    /* Use an additional ')' at the end of the string so that we don't have to
     * check `pIndex < pattern.length` all the time.
     */
    private[this] val pattern: String = pattern0 + ')'

    private[this] var pIndex: Int = 0

    val groupNodeMap = new ArrayList[Node]()
    groupNodeMap.add(null) // index 0 is not used

    def parsedGroupCount: Int = groupNodeMap.size() - 1

    def parseTopLevel(): Node =
      parseInsideParensAndClosingParen()

    private def parseInsideParensAndClosingParen(): Node = {
      // scalastyle:off return
      val alternatives = new ArrayList[Node]() // completed alternatives
      var sequence = new ArrayList[Node]()     // current sequence

      // Explicitly take the sequence, otherwise we capture a `var`
      def completeSequence(sequence: ArrayList[Node]): Node = {
        sequence.size() match {
          case 0 => new LeafRegexNode("")
          case 1 => sequence.get(0)
          case _ => new SequenceNode(sequence)
        }
      }

      while (true) {
        val dispatchCP = pattern.codePointAt(pIndex)

        val baseNode = (dispatchCP: @switch) match {
          case '|' =>
            // Complete one alternative
            alternatives.add(completeSequence(sequence))
            sequence = new ArrayList[Node]()
            pIndex += 1
            null

          case ')' =>
            // Complete the last alternative
            pIndex += 1 // go past the closing paren
            val lastAlternative = completeSequence(sequence)
            if (alternatives.size() == 0) {
              return lastAlternative
            } else {
              alternatives.add(lastAlternative)
              return new AlternativesNode(alternatives)
            }

          case '(' =>
            val indicator = pattern.substring(pIndex + 1, pIndex + 3)
            if (indicator == "?=" || indicator == "?!") {
              // Look-ahead group
              pIndex += 3
              val inner = parseInsideParensAndClosingParen()
              new LookAroundNode(isLookBehind = false, indicator, inner)
            } else if (indicator == "?<") {
              // Look-behind group, which must be ?<= or ?<!
              val fullIndicator = pattern.substring(pIndex + 1, pIndex + 4)
              pIndex += 4
              val inner = parseInsideParensAndClosingParen()
              new LookAroundNode(isLookBehind = true, fullIndicator, inner)
            } else if (indicator == "?:") {
              // Non-capturing group
              pIndex += 3
              val inner = parseInsideParensAndClosingParen()
              // Wrap LeafRegexNode's so that they do not merge with their neighbors
              if (inner.isInstanceOf[LeafRegexNode]) {
                val list = new ArrayList[Node](1)
                list.add(inner)
                new SequenceNode(list)
              } else
                inner
            } else {
              // Capturing group
              pIndex += 1
              val groupIndex = groupNodeMap.size()
              groupNodeMap.add(null) // reserve slot before parsing inner
              val inner = parseInsideParensAndClosingParen()
              val groupNode = new GroupNode(groupIndex, inner)
              groupNodeMap.set(groupIndex, groupNode)
              groupNode
            }

          case '\\' =>
            @inline
            def isDigit(c: Char): Boolean = c >= '0' && c <= '9'

            val startIndex = pIndex
            val c = pattern.charAt(startIndex + 1)
            pIndex += 2

            if (isDigit(c)) {
              // it is a back reference; parse all following digits
              while (isDigit(pattern.charAt(pIndex)))
                pIndex += 1
              new BackReferenceNode(
                  Integer.parseInt(pattern.substring(startIndex + 1, pIndex)))
            } else {
              // it is a character escape, or one of \b, \B, \d, \D, \p{...} or \P{...}
              if (c == 'p' || c == 'P') {
                while (pattern.charAt(pIndex) != '}')
                  pIndex += 1
                pIndex += 1
              }
              new LeafRegexNode(pattern.substring(startIndex, pIndex))
            }

          case '[' =>
            // parse until the corresponding ']' (here surrogate pairs don't matter)
            @tailrec def loop(pIndex: Int): Int = {
              pattern.charAt(pIndex) match {
                case '\\' => loop(pIndex + 2) // this is also fine for \p{...} and \P{...}
                case ']'  => pIndex + 1
                case _    => loop(pIndex + 1)
              }
            }

            val startIndex = pIndex
            pIndex = loop(startIndex + 1)
            val regex = pattern.substring(startIndex, pIndex)
            new LeafRegexNode(regex)

          case _ =>
            val start = pIndex
            pIndex += Character.charCount(dispatchCP)
            new LeafRegexNode(pattern.substring(start, pIndex))
        }

        if (baseNode ne null) { // null if we just completed an alternative
          (pattern.charAt(pIndex): @switch) match {
            case '+' | '*' | '?' =>
              val startIndex = pIndex
              if (pattern.charAt(startIndex + 1) == '?' || pattern.charAt(startIndex + 1) == '+')
                pIndex += 2
              else
                pIndex += 1

              val repeater = pattern.substring(startIndex, pIndex)
              sequence.add(new RepeatedNode(baseNode, repeater))

            case '{' =>
              // parse until end of occurrence
              val startIndex = pIndex
              pIndex = pattern.indexOf("}", startIndex + 1) + 1
              if (pattern.charAt(pIndex) == '?' || pattern.charAt(pIndex) == '+')
                pIndex += 1
              val repeater = pattern.substring(startIndex, pIndex)
              sequence.add(new RepeatedNode(baseNode, repeater))

            case _ =>
              /*val sequenceLen = sequence.size()
              if (sequenceLen != 0 && baseNode.isInstanceOf[LeafRegexNode] &&
                  sequence.get(sequenceLen - 1).isInstanceOf[LeafRegexNode]) {
                val fused = new LeafRegexNode(
                    sequence.get(sequenceLen - 1).asInstanceOf[LeafRegexNode].regex +
                    baseNode.asInstanceOf[LeafRegexNode].regex)
                sequence.set(sequenceLen - 1, fused)
              } else {*/
                sequence.add(baseNode)
              /*}*/
          }
        }
      }

      throw null // unreachable
      // scalastyle:on return
    }
  }
}
