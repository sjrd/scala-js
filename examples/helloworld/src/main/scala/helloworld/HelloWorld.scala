/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._

trait MyThisFunction1[-This, -T1, +R] extends js.ThisFunction {
  def apply(ths: This, x1: T1): R
}

trait MyNewTargetFunction1[-This, -T1, +R] extends js.NewTargetThisFunction {
  def apply(newTarget: Any, ths: This, x1: T1): R
}

object HelloWorld {
  def main(args: Array[String]): Unit = {
    testThisFunction()
    testNewTargetFunction()
  }

  def testThisFunction(): Unit = {
    val ctor0: MyThisFunction1[Any, Int, Unit] = { (o, i) =>
      if (js.isUndefined(o))
        throw new IllegalArgumentException("ctor must be called with 'new'")
      o.asInstanceOf[js.Dynamic].field = i
    }
    val ctor = ctor0.asInstanceOf[js.Dynamic]

    try {
      val o1 = ctor(5)
      println(o1.field)
    } catch {
      case _: IllegalArgumentException =>
        println("caught IllegalArgumentException")
    }

    val o2 = js.Dynamic.newInstance(ctor)(6)
    println(o2.field)

    val o3 = ctor.call(o2, 7)
    //println(o3.field)
    println(o2.field)
  }

  def testNewTargetFunction(): Unit = {
    val ctor0: MyNewTargetFunction1[Any, Int, Unit] = { (newTarget, o, i) =>
      if (js.isUndefined(newTarget))
        throw new IllegalArgumentException("ctor must be called with 'new'")
      o.asInstanceOf[js.Dynamic].field = i
    }
    val ctor = ctor0.asInstanceOf[js.Dynamic]

    try {
      val o1 = ctor(5)
      println(o1.field)
    } catch {
      case _: IllegalArgumentException =>
        println("caught IllegalArgumentException")
    }

    val o2 = js.Dynamic.newInstance(ctor)(6)
    println(o2.field)

    val o3 = ctor.call(o2, 7)
    println(o3.field)
    println(o2.field)
  }
}
