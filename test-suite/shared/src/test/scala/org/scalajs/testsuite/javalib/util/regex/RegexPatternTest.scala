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

package org.scalajs.testsuite.javalib.util.regex

import java.util.regex.Pattern

import org.junit.Test
import org.junit.Assert._

import org.scalajs.testsuite.utils.AssertThrows.assertThrows
import org.scalajs.testsuite.utils.Platform._

class RegexPatternTest {

  private def assertArrayStringEquals(expected: Array[String],
      actual: Array[String]): Unit = {
    assertArrayEquals(expected.asInstanceOf[Array[AnyRef]],
        actual.asInstanceOf[Array[AnyRef]])
  }

  @Test def matches(): Unit = {
    assertTrue(Pattern.matches("[Scal]*\\.js", "Scala.js"))
    assertTrue(Pattern.matches(".[cal]*\\.j.", "Scala.js"))
    assertTrue(Pattern.matches(".*\\.js", "Scala.js"))
    assertTrue(Pattern.matches("ba*?", "baaaa"))
    assertFalse(Pattern.matches("S[a-z]*", "Scala.js"))
  }

  @Test def matchesWithFlags(): Unit = {
    matches("scala.js", "Scala.js")
    matches("SCALA.JS", "Scala.js")
    matches("waz*up", "WAZZZZZZZZZZZUP")

    def matches(regex: String, input: String): Unit = {
      val result = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(input)
      assertTrue(result.matches())
    }
  }

  @Test def split(): Unit = {
    val result = Pattern.compile("[aj]").split("Scala.js")
    val expected = Array("Sc", "l", ".", "s")
    assertEquals(4, result.length)
    assertArrayStringEquals(expected, result)

    // Tests from JavaDoc
    split("boo:and:foo", ":", Array("boo", "and", "foo"))
    split("boo:and:foo", "o", Array("b", "", ":and:f"))

    // Splitting the empty string must return 1 element - #987
    split("", "a", Array(""))
    split("", "a?", Array(""))
    split("", "\\*", Array(""))
    split("", "\n", Array(""))
    split("", "", Array(""))

    /* Unless splitting the empty string, all trailing empty strings are
     * removed, which can reduce it to an empty array. #2592
     */
    split("a", "a", Array())
    split("a", "a?", Array())

    /* Should remove leading empty match under some conditions - #1171, #2573
     * The behavior changed in JDK 8 (at which point it became properly
     * documented).
     */
    split("abc", "(?=a)", Array("abc"))
    split("abc", "(?=b)", Array("a", "bc"))
    split("abc", "(?=a)|b", Array("a", "c"))
    split("abc", "", Array("a", "b", "c"))
    split("abc", "(?=a)|(?=b)", Array("a", "bc"))
    split("abc", "(?=a)|(?=a)", Array("abc"))
    split("abc", "(?=a|b)", Array("a", "bc"))
    split("abc", "(?=a|d)", Array("abc"))
    split("abc", "^d*", Array("abc"))
    split("abc", "d*", Array("a", "b", "c"))
    split("a", "", Array("a"))
    split("a", "^d*", Array("a"))
    split("a", "d*", Array("a"))
    split("a", "(?=a)", Array("a"))
    split("ab", "a", Array("", "b"))

    def split(input: String, regex: String, expected: Array[String]): Unit = {
      val result = Pattern.compile(regex).split(input)
      assertArrayStringEquals(expected, result)
    }
  }

  @Test def splitWithLimit(): Unit = {
    // Tests from JavaDoc
    splitWithLimit("boo:and:foo", ":", 2, Array("boo", "and:foo"))
    splitWithLimit("boo:and:foo", ":", 5, Array("boo", "and", "foo"))
    splitWithLimit("boo:and:foo", ":", -2, Array("boo", "and", "foo"))
    splitWithLimit("boo:and:foo", "o", 5, Array("b", "", ":and:f", "", ""))
    splitWithLimit("boo:and:foo", "o", -2, Array("b", "", ":and:f", "", ""))
    splitWithLimit("boo:and:foo", "o", 0, Array("b", "", ":and:f"))

    // Splitting the empty string must return 1 element - #987
    splitWithLimit("", "a", 0, Array(""))
    splitWithLimit("", "a?", 0, Array(""))
    splitWithLimit("", "\\*", 5, Array(""))
    splitWithLimit("", "\n", -2, Array(""))
    splitWithLimit("", "", 1, Array(""))

    /* Unless splitting the empty string, if `limit` is 0, all trailing empty
     * strings are removed, which can reduce it to an empty array. #2592
     */
    splitWithLimit("a", "a", 0, Array())
    splitWithLimit("a", "a?", 0, Array())
    splitWithLimit("a", "a", -1, Array("", ""))
    splitWithLimit("a", "a?", -1, Array("", "", ""))

    /* Should remove leading empty match under some conditions - #1171, #2573
     * The behavior changed in JDK 8 (at which point it became properly
     * documented).
     */
    splitWithLimit("abc", "", 2, Array("a", "bc"))
    splitWithLimit("abc", "", -1, Array("a", "b", "c", ""))
    splitWithLimit("abc", "(?=a)", 2, Array("abc"))
    splitWithLimit("ab", "a", 1, Array("ab"))

    def splitWithLimit(input: String, regex: String, limit: Int,
        expected: Array[String]): Unit = {
      val result = Pattern.compile(regex).split(input, limit)
      assertArrayStringEquals(expected, result)
    }
  }

  @Test def flags(): Unit = {
    import Pattern._

    assertEquals(0, Pattern.compile("a").flags())
    assertEquals(0, Pattern.compile("a", 0).flags())
    assertEquals(CASE_INSENSITIVE | DOTALL,
        Pattern.compile("a", CASE_INSENSITIVE | DOTALL).flags())

    // flags() reflects value of embedded flag expressions as well
    assertEquals(CASE_INSENSITIVE | DOTALL,
        Pattern.compile("(?i-x)ab", DOTALL | COMMENTS).flags())

    if (regexSupportsUnicodeCharacterClasses) {
      assertEquals(UNICODE_CASE | UNICODE_CHARACTER_CLASS, Pattern.compile("(?U)a").flags())

      if (!executingInJVM) // the JVM does not like the 'U' after the '-', but it makes no sense
        assertEquals(UNICODE_CASE, Pattern.compile("(?U-U)a").flags())

      /* Somehow, we can produce a state where flags have
       * UNICODE_CHARACTER_CLASS without UNICODE_CASE.
       */
      assertEquals(UNICODE_CHARACTER_CLASS,
          Pattern.compile("(?-u)a", UNICODE_CHARACTER_CLASS | UNICODE_CASE).flags())
      assertEquals(UNICODE_CHARACTER_CLASS,
          Pattern.compile("(?-u)a", UNICODE_CHARACTER_CLASS).flags())
    }
  }

  @Test def patternAndToString(): Unit = {
    def checkPatternAndToString(regex: String): Unit = {
      val pattern0 = Pattern.compile(regex)
      assertEquals(regex, pattern0.pattern)
      assertEquals(regex, pattern0.toString)

      val pattern1 = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
      assertEquals(regex, pattern1.pattern)
      assertEquals(regex, pattern1.toString)
    }

    checkPatternAndToString("a*b+c")
    checkPatternAndToString("\\S[(a1]a.js")
  }

  @Test def quote(): Unit = {
    val splitWithQuote = Pattern.compile(Pattern.quote("$1&$2")).split("Scala$1&$2.js")
    val splitNoQuote = Pattern.compile("$1&$2").split("Scala$1&$2.js")
    assertEquals("Scala.js", splitWithQuote.mkString)
    assertEquals("Scala$1&$2.js", splitNoQuote.mkString)

    // Tricky case with a \E sequence in the string to quote
    assertTrue(Pattern.matches(Pattern.quote("a[\\n\\E(e"), "a[\\n\\E(e"))
  }

  @Test def compileInvalidPatternThrows_Issue1718(): Unit = {
    assertThrows(classOf[Throwable], Pattern.compile("*"))
  }

}
