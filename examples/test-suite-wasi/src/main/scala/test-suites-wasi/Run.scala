package testSuiteWASI

object Run {
  def main(args: Array[String]): Unit = {
    JavalibLangTest.run()
    JavalibUtilTest.run()
    LibraryTest.run()

    println("Test suite completed")
  }
}
