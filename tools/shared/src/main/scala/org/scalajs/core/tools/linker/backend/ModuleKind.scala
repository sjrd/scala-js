/*                     __                                               *\
**     ________ ___   / /  ___      __ ____  Scala.js tools             **
**    / __/ __// _ | / /  / _ | __ / // __/  (c) 2013-2016, LAMP/EPFL   **
**  __\ \/ /__/ __ |/ /__/ __ |/_// /_\ \    http://scala-js.org/       **
** /____/\___/_/ |_/____/_/ | |__/ /____/                               **
**                          |/____/                                     **
\*                                                                      */


package org.scalajs.core.tools.linker.backend

/** Kind of module structure emitted for the Scala.js output. */
sealed abstract class ModuleKind

object ModuleKind {

  /** No module structure.
   *
   *  With this module kind, exports are stored on the global object by
   *  default, or to a separate object specified with
   *  `__ScalaJSEnv.exportsNamespace`.
   *
   *  Imports are not supported.
   */
  case object NoModule extends ModuleKind

  /** A Node.js module.
   *
   *  Imported modules are fetched with `require`. Exports go to the `exports`
   *  module-global variable.
   */
  case object NodeJSModule extends ModuleKind

}
