package testSuiteWASI

import library._

object LibraryTest {
  def run(): Unit = {
    locally {
      val test = new ReflectTest
      import test._
      testClassRuntimeClass()
      testObjectRuntimeClass()
      testClassCannotBeFound()
      testObjectCannotBeFound()
      testClassNoArgCtor()
      testClassNoArgCtorErrorCase()
      testClassCtorWithArgs()
      testInnerClass()
      testLocalClass()
      testObjectLoad()
      testInnerObjectWithEnableReflectiveInstantiation_Issue3228()
      testLocalClassWithReflectiveInstantiationInLambda_Issue3227()
    }
  }
}
