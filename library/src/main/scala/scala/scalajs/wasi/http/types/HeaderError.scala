package scala.scalajs.wasi.http.types

import scala.scalajs.component.annotation._

/** This type enumerates the different kinds of errors that may occur when
 *  setting or appending to a `fields` resource.
 */
@ComponentVariant
sealed trait HeaderError
object HeaderError {
  case object InvalidSyntax extends HeaderError
  case object Forbidden extends HeaderError
  case object Immutable extends HeaderError
}