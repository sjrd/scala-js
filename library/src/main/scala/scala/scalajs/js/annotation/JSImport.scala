/*                     __                                               *\
**     ________ ___   / /  ___      __ ____  Scala.js API               **
**    / __/ __// _ | / /  / _ | __ / // __/  (c) 2013-2016, LAMP/EPFL   **
**  __\ \/ /__/ __ |/ /__/ __ |/_// /_\ \    http://scala-js.org/       **
** /____/\___/_/ |_/____/_/ | |__/ /____/                               **
**                          |/____/                                     **
\*                                                                      */


package scala.scalajs.js.annotation

/** Marks the annotated class or object as imported from another JS module.
 *
 *  Intuitively, this corresponds to the following ECMAScript import
 *  directive:
 *  {{{
 *  import { <name> as AnnotatedClassOrObject } from <module>
 *  }}}
 *
 *  To import the default import of a module, use `JSImport.Default` as `name`.
 */
class JSImport(module: String, name: String)
    extends scala.annotation.StaticAnnotation {

  /** Import the module itself (not one of its exported members, including its
   *  default export).
   *
   *  Intuitively, this corresponds to
   *  {{{
   *  import * as AnnotatedObject from <module>
   *  }}}
   */
  def this(module: String) = this(module, null)
}

object JSImport {
  /** Use as the `name` of a `JSImport` to use the default import.
   *
   *  The actual value of this constant, the string `"default"`, is not
   *  arbitrary. It is the name under which a default import/export is
   *  registered in the ECMAScript 2015 specification.
   */
  final val Default = "default"
}
