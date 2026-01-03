package componentmodel.imports

import scala.scalajs.wit
import scala.scalajs.wit.annotation._

import java.util.Optional

object Countable {
  @WitResourceImport("component:testing/countable", "counter")
  trait Counter {
    @WitResourceMethod("up")
    def up(): Unit = wit.native

    @WitResourceMethod("down")
    def down(): Unit = wit.native

    @WitResourceMethod("value-of")
    def valueOf(): Int = wit.native

    @WitResourceDrop
    def close(): Unit = wit.native
  }
  object Counter {
    @WitResourceConstructor
    def apply(i: Int): Counter = wit.native

    @WitResourceStaticMethod("sum")
    def sum(a: Counter, b: Counter): Counter = wit.native
  }

  @WitImport("component:testing/countable", "try-create-counter")
  def tryCreateCounter(value: Int): wit.Result[Counter, String] = wit.native

  @WitImport("component:testing/countable", "maybe-get-counter")
  def maybeGetCounter(): Optional[Counter] = wit.native
}