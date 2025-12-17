package scala.scalajs.wasi.http.types

import scala.scalajs.{component => cm}
import scala.scalajs.component.annotation._

/** This following block defines the `fields` resource which corresponds to
 *  HTTP standard Fields. Fields are a common representation used for both
 *  Headers and Trailers.
 *
 *  A `fields` may be mutable or immutable. A `fields` created using the
 *  constructor, `from-list`, or `clone` will be mutable, but a `fields`
 *  resource given by other means (including, but not limited to,
 *  `incoming-request.headers`, `outgoing-request.headers`) might be
 *  immutable. In an immutable fields, the `set`, `append`, and `delete`
 *  operations will fail with `header-error.immutable`.
 */
@ComponentResourceImport("wasi:http/types@0.2.0", "fields")
trait Fields {

  // get: func(name: field-name) -> list<field-value>;
  @ComponentResourceMethod("get")
  def get(name: FieldName): Array[FieldValue] = cm.native

  // has: func(name: field-name) -> bool;
  @ComponentResourceMethod("has")
  def has(name: FieldName): Boolean = cm.native

  // set: func(name: field-name, value: list<field-value>) -> result<_, header-error>;
  @ComponentResourceMethod("set")
  def set(name: FieldName, value: Array[FieldValue]): cm.Result[Unit, HeaderError] = cm.native

  // append: func(name: field-name, value: field-value) -> result<_, header-error>;
  @ComponentResourceMethod("append")
  def append(name: FieldName, value: FieldValue): cm.Result[Unit, HeaderError] = cm.native

  // entries: func() -> list<tuple<field-name,field-value>>;
  @ComponentResourceMethod("entries")
  def entries(): Array[cm.Tuple2[FieldName, FieldValue]] = cm.native

  // clone: func() -> fields;
  @ComponentResourceMethod("clone")
  def cloneFields(): Fields = cm.native
}
object Fields {
  @ComponentResourceConstructor
  def apply(): Fields = cm.native

  @ComponentResourceStaticMethod("from-list")
  def fromList(entries: Array[cm.Tuple2[FieldName, FieldValue]]): Fields = cm.native
}