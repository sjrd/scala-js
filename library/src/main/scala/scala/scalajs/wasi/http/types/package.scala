package scala.scalajs.wasi.http

import scala.scalajs.wit.unsigned._

package object types {
  /** Field values should always be ASCII strings. However, in
   *  reality, HTTP implementations often have to interpret malformed values,
   *  so they are provided as a list of bytes.
   */
  type FieldValue = Array[UByte]
  type FieldName = FieldKey
  type FieldKey = String
  type Headers = Fields
  type StatusCode = UShort
  type Trailers = Fields
}
