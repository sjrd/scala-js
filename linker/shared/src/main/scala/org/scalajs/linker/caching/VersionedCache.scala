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

/** Base trait for a cache that invalidates itself based on a `Version`. */
trait VersionedCache extends Cache {
  private var _lastVersion: Version = Version.Unversioned

  override def invalidate(): Unit = {
    super.invalidate()
    _lastVersion = Version.Unversioned
  }

  protected final def updateVersion(version: Version): Boolean = {
    markUsed()
    if (_lastVersion.sameVersion(version)) {
      false
    } else {
      invalidate()
      _lastVersion = version
      true
    }
  }
}
