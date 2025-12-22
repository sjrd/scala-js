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

package java.lang

private[lang] object UnicodeData {
  private final val TypeMask = 0x1f // 5 bits; types go from 0 to 30

  private final val SpaceCharFlag = 1 << 5
  private final val WhitespaceFlag = 1 << 6
  private final val LetterFlag = 1 << 7
  private final val LowerCaseFlag = 1 << 8
  private final val UpperCaseFlag = 1 << 9
  private final val TitleCaseFlag = 1 << 10
  private final val DigitFlag = 1 << 11
  private final val IdeographicFlag = 1 << 12
  private final val AlphabeticFlag = 1 << 13
  private final val JavaIdentifierStartFlag = 1 << 14
  private final val JavaIdentifierPartFlag = 1 << 15
  private final val UnicodeIdentifierStartFlag = 1 << 16
  private final val UnicodeIdentifierPartFlag = 1 << 17
  private final val IdentifierIgnorableFlag = 1 << 18
  private final val MirroredFlag = 1 << 19

  @inline private def isLatin1(cp: Int): scala.Boolean =
    Integer.unsigned_<(cp, 256)

  private def getDataNonLatin1(cp: Int): Int = {
    // TODO
    0
  }

  @inline
  private def getData(cp: Int): Int = {
    if (isLatin1(cp))
      dataLatin1(cp)
    else
      getDataNonLatin1(cp)
  }

  @inline private def dataType(data: Int): Int =
    data & TypeMask

  @inline private def dataHasAnyFlag(data: Int, flags: Int): scala.Boolean =
    (data & flags) != 0

  @inline private def cpType(cp: Int): Int =
    dataType(getData(cp))

  @inline private def cpHasAnyFlag(cp: Int, flags: Int): scala.Boolean =
    dataHasAnyFlag(getData(cp), flags)

  // Public methods (corresponding to methods in jl.Character)

  @noinline def isLowerCase(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, LowerCaseFlag)

  @noinline def isUpperCase(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, UpperCaseFlag)

  @noinline def isTitleCase(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, TitleCaseFlag)

  @noinline def isDigit(cp: Int): scala.Boolean =
    if (isLatin1(cp)) cp >= '0' && cp <= '9'
    else dataHasAnyFlag(getDataNonLatin1(cp), DigitFlag)

  @noinline def isDefined(cp: Int): scala.Boolean =
    isLatin1(cp) || dataType(getDataNonLatin1(cp)) != Character.UNASSIGNED

  @noinline def isLetter(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, LetterFlag)

  @noinline def isLetterOrDigit(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, LetterFlag | DigitFlag)

  @noinline def isAlphabetic(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, AlphabeticFlag)

  @noinline def isIdeographic(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, IdeographicFlag)

  @noinline def isJavaIdentifierStart(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, JavaIdentifierStartFlag)

  @noinline def isJavaIdentifierPart(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, JavaIdentifierPartFlag)

  @noinline def isUnicodeIdentifierStart(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, UnicodeIdentifierStartFlag)

  @noinline def isUnicodeIdentifierPart(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, UnicodeIdentifierPartFlag)

  @noinline def isIdentifierIgnorable(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, IdentifierIgnorableFlag)

  // TODO isEmojiXYZ, isExtendedPictographic (JDK 21)

  // TODO getNumericValue

  @noinline def isSpaceChar(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, SpaceCharFlag)

  @noinline def isWhitespace(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, WhitespaceFlag)

  // isISOControl is implemented in Character with only two range tests

  @noinline def getType(cp: Int): scala.Byte =
    cpType(cp).toByte

  // TODO getDirectionality

  @noinline def isMirrored(cp: Int): scala.Boolean =
    cpHasAnyFlag(cp, MirroredFlag)

  /* Deprecated methods, which we derive from other data instead of consuming
   * flags for them.
   */

  @deprecated("Replaced by isJavaIdentifierStart(char)", "")
  @noinline
  def isJavaLetter(cp: Int): scala.Boolean = {
    import Character._

    val data = getData(cp)
    dataHasAnyFlag(cp, LetterFlag) || {
      val tpe = dataType(data)
      tpe == LETTER_NUMBER || tpe == CURRENCY_SYMBOL || tpe == CONNECTOR_PUNCTUATION
    }
  }

  @deprecated("Replaced by isJavaIdentifierPart(char)", "")
  def isJavaLetterOrDigit(cp: Int): scala.Boolean = {
    import Character._

    val data = getData(cp)
    dataHasAnyFlag(cp, LetterFlag | IdentifierIgnorableFlag) || {
      val tpe = dataType(data)
      tpe == COMBINING_SPACING_MARK || tpe == NON_SPACING_MARK
    }
  }

  // BEGIN GENERATED: [unicode-data]
  private val dataLatin1: Array[Int] = Array(0)

  private val dataNonLatin1: Array[Int] = Array(0)
  // END GENERATED: [unicode-data]
}
