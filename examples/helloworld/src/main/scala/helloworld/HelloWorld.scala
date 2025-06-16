/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

object HelloWorld {
  val AllPropertiesProducedByPatternCompiler = List(
    // General categories
    "Lu", "Ll", "Lt", "LC", "Lm", "Lo", "L",
    "Mn", "Mc", "Me", "M",
    "Nd", "Nl", "No", "N",
    "Pc", "Pd", "Ps", "Pe", "Pi", "Pf", "Po", "P",
    "Sm", "Sc", "Sk", "So", "S",
    "Zs", "Zl", "Zp", "Z",
    "Cc", "Cf", "Cs", "Co", "Cn", "C",

    // Properties
    "Alphabetic",
    "Assigned",
    "Bidi_Mirrored",
    "Control",
    "Hex_Digit",
    "ID_Continue",
    "ID_Start",
    "Ideographic",
    "Join_Control",
    "Letter",
    "Lowercase",
    "Noncharacter_Code_Point",
    "Punctuation",
    "Uppercase",
    "White_Space",

    // Scripts supported by the ECMAScript spec
  )
  def main(args: Array[String]): Unit = {
    val scriptsURL = new java.net.URL("www.unicode.org/Public/16.0.0/ucd/Scripts.txt")
    val allScripts: Set[String] = scala.io.Source.fromURL(url, "UTF-8")
      .getLines()
      .filter(line => !line.isEmpty() && !line.startsWith("#"))
      .map(_.split(';'))
      .filter(arr => arr(1) == " C" || arr(1) == " S")
      .map { arr =>
        val cp = Integer.parseInt(arr(0), 16)
        val mappedTo = Integer.parseInt(arr(2).trim(), 16)
        cp -> mappedTo
      }
      .toList
  }
}
