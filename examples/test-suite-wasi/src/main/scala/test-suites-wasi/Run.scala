package testSuiteWASI

object Run {
  def main(args: Array[String]): Unit = {
    JavalibUtilTest.run()
    LibraryTest.run()

    println("Test suite completed")
  }
}
