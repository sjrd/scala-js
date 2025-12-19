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

package scala.scalajs.component

sealed trait Result[+A, +B]

final case class Ok[A](val value: A) extends Result[A, Nothing]

final case class Err[B](val value: B) extends Result[Nothing, B]
