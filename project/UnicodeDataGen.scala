package build

import java.nio.charset.StandardCharsets.UTF_8

import scala.collection.mutable

import sbt._

object UnicodeDataGen {
  /** When updating this to a newer version, you should then regenerate the
   *  Unicode data so they stay in sync.
   *
   *  Trying to generate the Unicode data while the build is running on a
   *  different JDK version will result in an error.
   */
  final val ReferenceJDKVersion = 21

  private final class BooleanProp(val name: String, val testFun: Int => Boolean) {
    override def toString(): String = name
  }

  private val AllBooleanProps: List[BooleanProp] = List(
    new BooleanProp("LowerCase", Character.isLowerCase(_)),
    new BooleanProp("UpperCase", Character.isUpperCase(_)),
    new BooleanProp("TitleCase", Character.isTitleCase(_)),
    new BooleanProp("Digit", Character.isDigit(_)),
    new BooleanProp("Letter", Character.isLetter(_)),
    new BooleanProp("Alphabetic", Character.isAlphabetic(_)),
    new BooleanProp("Ideographic", Character.isIdeographic(_)),
    new BooleanProp("JavaIdentifierStart", Character.isJavaIdentifierStart(_)),
    new BooleanProp("JavaIdentifierPart", Character.isJavaIdentifierPart(_)),
    new BooleanProp("UnicodeIdentifierStart", Character.isUnicodeIdentifierStart(_)),
    new BooleanProp("UnicodeIdentifierPart", Character.isUnicodeIdentifierPart(_)),
    new BooleanProp("IdentifierIgnorable", Character.isIdentifierIgnorable(_)),
    new BooleanProp("SpaceChar", Character.isSpaceChar(_)),
    new BooleanProp("Whitespace", Character.isWhitespace(_)),
    new BooleanProp("Mirrored", Character.isMirrored(_)),
  )

  private final case class Data(tpe: Int, props: List[BooleanProp])

  private final case class Entry(firstCP: Int, data: Data)

  private def computeData(cp: Int): Data = {
    val tpe = Character.getType(cp).toInt
    val props = AllBooleanProps.filter(prop => prop.testFun(cp))
    Data(tpe, props)
  }

  private def dataToInt(data: Data, propFlags: Map[BooleanProp, Int]): Int = {
    var intData = data.tpe
    for (prop <- data.props)
      intData |= propFlags(prop)
    intData
  }

  def generateAll(detectedJDKVersion: Int): Unit = {
    if (detectedJDKVersion != ReferenceJDKVersion) {
      throw new MessageOnlyException(
        s"The reference JDK version to generate Unicode data is " +
        s"$ReferenceJDKVersion, but the build is running under " +
        s"$detectedJDKVersion. " +
        s"Make sure to run the build itself under JDK $ReferenceJDKVersion " +
        s"to generate Unicode data."
      )
    }

    val latin1Data = computeLatin1Data()
    val nonLatin1Data = computeNonLatin1Data()
    val propFlags = computePropFlags(nonLatin1Data)

    val flagDefinitions =
      for ((prop, flag) <- propFlags.toList.sortBy(_._2)) yield
        s"  private final val ${prop.name}Flag = 0x${flag.toHexString}"
    val flagDefinitionContent = flagDefinitions.mkString

    val latin1IntData = latin1Data.map(dataToInt(_, propFlags))
    val latin1DataContent = List(formatLargeArray(latin1IntData, "  "))

    var prevCP = 0
    val nonLatin1IntData = nonLatin1Data.flatMap { e =>
      val diff = e.firstCP - prevCP
      prevCP = e.firstCP
      Array(diff, dataToInt(e.data, propFlags))
    }
    val nonLatin1DataContent = List(formatLargeArray(nonLatin1IntData, "  "))

    patchFile("javalib/src/main/scala/java/lang/UnicodeData.scala")(
      "flag-definitions" -> flagDefinitions,
      "unicode-data-latin1" -> latin1DataContent,
      "unicode-data-non-latin1" -> nonLatin1DataContent,
    )
  }

  private def computeLatin1Data(): Array[Data] = {
    for (cp <- (0 until 256).toArray) yield
      computeData(cp)
  }

  private def computeNonLatin1Data(): Array[Entry] = {
    val firstCP = 256
    val lastCP = Character.MAX_CODE_POINT + 1

    val b = Array.newBuilder[Entry]
    var lastEntry = Entry(firstCP, computeData(firstCP))
    b += lastEntry

    for (cp <- (firstCP + 1) to lastCP) {
      val data = computeData(cp)
      if (data != lastEntry.data) {
        lastEntry = Entry(cp, data)
        b += lastEntry
      }
    }

    b.result()
  }

  /** Computes an assignment of flags for boolean props.
   *
   *  Properties that are more often set receive lower flag values, so that
   *  data values are more often short.
   */
  private def computePropFlags(nonLatin1Data: Array[Entry]): Map[BooleanProp, Int] = {
    val frequencies = mutable.HashMap(AllBooleanProps.map(prop => prop -> 0): _*)
    for (entry <- nonLatin1Data) {
      for (prop <- entry.data.props)
        frequencies(prop) += 1
    }

    val sortedProps = frequencies.toList.sortBy(-_._2)
    val allocs =
      for ((propFreq, index) <- sortedProps.zipWithIndex) yield
        propFreq._1 -> (1 << (index + 5)) // 5 bits are used for the type
    allocs.toMap
  }

  private def formatLargeArrayStr(array: Array[String], indent: String): String = {
    val indentMinus1 = indent.substring(1)
    val builder = new java.lang.StringBuilder
    builder.append(indentMinus1)
    var curLineLength = indentMinus1.length
    for (i <- 0 until array.length) {
      val toAdd = " " + array(i) + (if (i == array.length - 1) "" else ",")
      if (curLineLength + toAdd.length >= 80) {
        builder.append("\n")
        builder.append(indentMinus1)
        curLineLength = indentMinus1.length
      }
      builder.append(toAdd)
      curLineLength += toAdd.length
    }
    builder.toString()
  }

  private def formatLargeArray(array: Array[Int], indent: String): String =
    formatLargeArrayStr(array.map(_.toString()), indent)

  private def patchFile(fileName: String)(patches: (String, List[String])*): Unit = {
    val patchMap = patches.toMap
    val file = new java.io.File(fileName)
    val lines = IO.readLines(file, UTF_8)

    val newLines = patches.foldLeft(lines) { (lines, patch) =>
      val start = lines.indexOf(s"  // BEGIN GENERATED: [${patch._1}]")
      val end = lines.indexOf(s"  // END GENERATED: [${patch._1}]")

      if (start < 0 || end < 0 || end < start)
        throw new MessageOnlyException(s"Cannot locate patch [${patch._1}] in $fileName")

      val (beginningAndOld, rest) = lines.splitAt(end)
      val beginning = beginningAndOld.take(start + 1)

      beginning ::: patch._2 ::: rest
    }

    IO.writeLines(file, newLines, UTF_8)
  }
}
