package scala.scalajs.wasi.http.types

import scala.scalajs.wit
import scala.scalajs.wit.annotation._

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
@WitResourceImport("wasi:http/types@0.2.0", "fields")
trait Fields {

  // get: func(name: field-name) -> list<field-value>;
  @WitResourceMethod("get")
  def get(name: FieldName): Array[FieldValue] = wit.native

  // has: func(name: field-name) -> bool;
  @WitResourceMethod("has")
  def has(name: FieldName): Boolean = wit.native

  // set: func(name: field-name, value: list<field-value>) -> result<_, header-error>;
  @WitResourceMethod("set")
  def set(name: FieldName, value: Array[FieldValue]): wit.Result[Unit, HeaderError] = wit.native

  // append: func(name: field-name, value: field-value) -> result<_, header-error>;
  @WitResourceMethod("append")
  def append(name: FieldName, value: FieldValue): wit.Result[Unit, HeaderError] = wit.native

  // entries: func() -> list<tuple<field-name,field-value>>;
  @WitResourceMethod("entries")
  def entries(): Array[wit.Tuple2[FieldName, FieldValue]] = wit.native

  // clone: func() -> fields;
  @WitResourceMethod("clone")
  def cloneFields(): Fields = wit.native
}
object Fields {
  @WitResourceConstructor
  def apply(): Fields = wit.native

  @WitResourceStaticMethod("from-list")
  def fromList(entries: Array[wit.Tuple2[FieldName, FieldValue]]): Fields = wit.native
}