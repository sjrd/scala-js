package testSuiteWASI

object Run {
  def main(args: Array[String]): Unit = {
    LibraryTest.run()

    println("Test suite completed")
  }
}
