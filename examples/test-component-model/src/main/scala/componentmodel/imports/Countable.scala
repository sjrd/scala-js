package componentmodel.imports

import scala.scalajs.{component => cm}
import scala.scalajs.component.annotation._

import java.util.Optional

object Countable {
  @ComponentResourceImport("component:testing/countable", "counter")
  trait Counter {
    @ComponentResourceMethod("up")
    def up(): Unit = cm.native

    @ComponentResourceMethod("down")
    def down(): Unit = cm.native

    @ComponentResourceMethod("value-of")
    def valueOf(): Int = cm.native

    @ComponentResourceDrop
    def close(): Unit = cm.native
  }
  object Counter {
    @ComponentResourceConstructor
    def apply(i: Int): Counter = cm.native

    @ComponentResourceStaticMethod("sum")
    def sum(a: Counter, b: Counter): Counter = cm.native
  }

  @ComponentImport("component:testing/countable", "try-create-counter")
  def tryCreateCounter(value: Int): cm.Result[Counter, String] = cm.native

  @ComponentImport("component:testing/countable", "maybe-get-counter")
  def maybeGetCounter(): Optional[Counter] = cm.native
}