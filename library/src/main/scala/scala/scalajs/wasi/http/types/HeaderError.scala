package scala.scalajs.wasi.http.types

import scala.scalajs.{component => cm}

/** This type enumerates the different kinds of errors that may occur when
 *  setting or appending to a `fields` resource.
 */
sealed trait HeaderError extends cm.Variant
object HeaderError {
  final object InvalidSyntax extends HeaderError {
    type T = Unit
    val _index: Int = 0
    val value = ()
  }
  final object Forbidden extends HeaderError {
    type T = Unit
    val _index: Int = 1
    val value = ()
  }
  final object Immutable extends HeaderError {
    type T = Unit
    val _index: Int = 2
    val value = ()
  }
}