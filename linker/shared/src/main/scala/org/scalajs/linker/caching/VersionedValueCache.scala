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

package org.scalajs.linker.caching

import org.scalajs.ir.Version

/** A cache for a single value that gets invalidated based on a `Version`. */
trait VersionedValueCache[T] extends VersionedCache {
  private[this] var _value: T = null.asInstanceOf[T]

  override def invalidate(): Unit = {
    super.invalidate()
    _value = null.asInstanceOf[T]
  }

  protected final def getOrCompute(version: Version, computeValue: => T): T = {
    if (updateVersion(version))
      _value = computeValue
    _value
  }

  protected final def getOrComputeWithChanged(version: Version, computeValue: => T): (T, Boolean) = {
    if (updateVersion(version)) {
      _value = computeValue
      (_value, true)
    } else {
      (_value, false)
    }
  }
}
