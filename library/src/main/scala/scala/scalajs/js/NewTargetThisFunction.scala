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

package scala.scalajs.js

import scala.scalajs.js

/** A JavaScript function that captures `new.target` as first parameter, and
 *  `this` as second parameter.
 */
trait NewTargetThisFunction extends js.ThisFunction
