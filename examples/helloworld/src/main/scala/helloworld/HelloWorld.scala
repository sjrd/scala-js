/* Scala.js example code
 * Public domain
 * @author  Sébastien Doeraene
 */

package helloworld

import scala.scalajs.js
import js.annotation._
import scala.scalajs.js.Generator

object HelloWorld {
  @noinline def list: List[Int] = List(41, 2)

  def myGenerator(n: Int): js.Generator[Int, String, Int] = js.Generator[Int, String, Int] { implicit ev =>
    println("one")
    js.Generator.`yield`(42)
    println("two")
    var i = 0
    var j = 0
    while (i != n) {
      try {
        j += js.Generator.`yield`(j)
        /*if (j > 2)
          throw new IllegalStateException("boom")*/
        if (i == 4)
          throw new IllegalArgumentException("internal exception")
      } catch {
        case th: IllegalArgumentException =>
          println("gen: " + th.getMessage())
      }
      i += 1
    }
    println(5 * (list match {
      case head :: 2 :: Nil =>
        head + js.Generator.`yield`(head)
      case _ =>
        -2
    }))
    println(5 * (list match {
      case head :: 3 :: Nil =>
        head + js.Generator.`yield`(head)
      case _ =>
        -2
    }))
    while (js.Generator.`yield`(j) != 10) {
      j += 1
    }
    "result"
  }

  def main(args: Array[String]): Unit = {
    try {
      println("hello")

      val g = myGenerator(5)
      println("zero")
      for (k <- 0 until 13) {
        try {
          if (k == 4)
            g.`throw`(new IllegalArgumentException("injected exception"))
            //js.Dynamic.global.console.log("main2: ", g.`return`("early return"))
          else
            js.Dynamic.global.console.log("main1: ", g.next(k))
        } catch {
          case th: IllegalStateException =>
            println("main: " + th)
        }
      }
      println("done")
    } catch {
      case th: Throwable =>
        println("final catch: " + th)
    }
  }
}
