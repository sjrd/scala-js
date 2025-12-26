/*
 * Scala.js (https://www.scala-js.org/)
 *
 * Copyright EPFL.
 *
 * Licensed under Apache License 2.0
 * (https://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package org.scalajs.nscplugin.test

import org.scalajs.nscplugin.test.util._

import org.junit.Test

class ComponentModelInteropTest extends DirectTest with TestHelpers {

  override def preamble: String =
    """
    import scala.scalajs.{component => cm}
    import scala.scalajs.component.annotation._
    import scala.scalajs.component.unsigned._
    """

  @Test def resourceImportMustBeOnTrait: Unit = {
    """
    @ComponentResourceImport("test:module", "resource")
    class MyResource
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentResourceImport is allowed for traits
      |    class MyResource
      |          ^
    """

    """
    @ComponentResourceImport("test:module", "resource")
    object MyResource
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentResourceImport is allowed for traits
      |    object MyResource
      |           ^
    """
  }

  @Test def resourceImportCannotBeSealed: Unit = {
    """
    @ComponentResourceImport("test:module", "resource")
    sealed trait MyResource
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentResourceImport traits cannot be sealed
      |    sealed trait MyResource
      |                 ^
    """
  }

  @Test def resourceMethodsMustHaveAnnotation: Unit = {
    """
    @ComponentResourceImport("test:module", "resource")
    trait MyResource {
      def doSomething(): Unit
    }
    """ hasErrors
    """
      |newSource1.scala:8: error: Method 'doSomething' in @ComponentResourceImport trait must be annotated with @ComponentResourceMethod or @ComponentResourceDrop
      |      def doSomething(): Unit
      |          ^
    """

    """
    @ComponentResourceImport("test:module", "resource")
    trait MyResource {
      def method1(): Unit
      def method2(x: Int): String
    }
    """ hasErrors
    """
      |newSource1.scala:8: error: Method 'method1' in @ComponentResourceImport trait must be annotated with @ComponentResourceMethod or @ComponentResourceDrop
      |      def method1(): Unit
      |          ^
      |newSource1.scala:9: error: Method 'method2' in @ComponentResourceImport trait must be annotated with @ComponentResourceMethod or @ComponentResourceDrop
      |      def method2(x: Int): String
      |          ^
    """

    """
    @ComponentResourceImport("test:module", "resource")
    trait MyResource {
      @ComponentResourceMethod("annotated")
      def annotated(): Unit = cm.native

      def unannotated(): Unit = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:11: error: Method 'unannotated' in @ComponentResourceImport trait must be annotated with @ComponentResourceMethod or @ComponentResourceDrop
      |      def unannotated(): Unit = cm.native
      |          ^
    """
  }

  @Test def resourceMethodParametersMustBeCompatible: Unit = {
    """
    class NotCompatible

    @ComponentResourceImport("test:module", "resource")
    trait MyResource {
      @ComponentResourceMethod("invalid")
      def invalidMethod(x: NotCompatible): Unit = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:11: error: Parameter type 'NotCompatible' in method 'invalidMethod' is not compatible with Component Model
      |      def invalidMethod(x: NotCompatible): Unit = cm.native
      |          ^
    """
  }

  @Test def resourceMethodReturnTypeMustBeCompatible: Unit = {
    """
    class NotCompatible

    @ComponentResourceImport("test:module", "resource")
    trait MyResource {
      @ComponentResourceMethod("invalid")
      def invalidMethod(): NotCompatible = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:11: error: Return type 'NotCompatible' in method 'invalidMethod' is not compatible with Component Model
      |      def invalidMethod(): NotCompatible = cm.native
      |          ^
    """
  }

  @Test def resourceCompanionObjectMethodsMustHaveAnnotation: Unit = {
    """
    @ComponentResourceImport("test:module", "resource")
    trait MyResource {
      @ComponentResourceMethod("do-something")
      def doSomething(): Unit = cm.native
    }
    object MyResource {
      def invalidMethod(): Unit = ???
    }
    """ hasErrors
    """
      |newSource1.scala:12: error: Public method 'invalidMethod' in companion object of @ComponentResourceImport trait must be annotated with @ComponentResourceConstructor or @ComponentResourceStaticMethod
      |      def invalidMethod(): Unit = ???
      |          ^
    """
  }

  @Test def resourceDropMustHaveNoParametersAndReturnUnit: Unit = {
    """
    @ComponentResourceImport("test:module", "resource")
    trait MyResource {
      @ComponentResourceDrop
      def close(x: Int): Unit = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:9: error: @ComponentResourceDrop method must take no parameters
      |      def close(x: Int): Unit = cm.native
      |          ^
    """

    """
    @ComponentResourceImport("test:module", "resource")
    trait MyResource {
      @ComponentResourceDrop
      def close(): Int = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:9: error: @ComponentResourceDrop method must return Unit
      |      def close(): Int = cm.native
      |          ^
    """
  }

  @Test def resourceCanHaveAtMostOneDropMethod: Unit = {
    """
    @ComponentResourceImport("test:module", "resource")
    trait MyResource {
      @ComponentResourceDrop
      def close(): Unit = cm.native

      @ComponentResourceDrop
      def dispose(): Unit = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentResourceImport trait can have at most one @ComponentResourceDrop method, found 2
      |    trait MyResource {
      |          ^
    """
  }

  @Test def resourceConstructorOnlyInCompanionObject: Unit = {
    """
    @ComponentResourceImport("test:module", "resource")
    trait MyResource {
      @ComponentResourceConstructor
      def create(): MyResource = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:9: error: @ComponentResourceConstructor can only be used on apply method in companion object
      |      def create(): MyResource = cm.native
      |          ^
    """
  }

  @Test def resourceStaticMethodOnlyInCompanionObject: Unit = {
    """
    @ComponentResourceImport("test:module", "resource")
    trait MyResource {
      @ComponentResourceStaticMethod("factory")
      def factory(): MyResource = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:9: error: @ComponentResourceStaticMethod can only be used in companion object
      |      def factory(): MyResource = cm.native
      |          ^
    """
  }

  @Test def resourceConstructorMustBeOnApply: Unit = {
    """
    @ComponentResourceImport("test:module", "resource")
    trait MyResource
    object MyResource {
      @ComponentResourceConstructor
      def create(): MyResource = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:10: error: @ComponentResourceConstructor can only be used on apply method
      |      def create(): MyResource = cm.native
      |          ^
    """
  }

  @Test def resourceAnnotationsOnlyInResourceImportTraits: Unit = {
    """
    trait NotAResource {
      @ComponentResourceMethod("invalid")
      def method(): Unit = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:8: error: scala.scalajs.component.annotation.ComponentResourceMethod("invalid") is allowed in trait annotated with @ComponentResourceImport
      |      def method(): Unit = cm.native
      |          ^
    """

    """
    trait NotAResource {
      @ComponentResourceDrop
      def drop(): Unit = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:8: error: scala.scalajs.component.annotation.ComponentResourceDrop is allowed in trait annotated with @ComponentResourceImport
      |      def drop(): Unit = cm.native
      |          ^
    """
  }

  @Test def resourceConstructorAnnotationsOnlyInResourceCompanions: Unit = {
    """
    object NotAResourceCompanion {
      @ComponentResourceConstructor
      def apply(): String = ???
    }
    """ hasErrors
    """
      |newSource1.scala:8: error: scala.scalajs.component.annotation.ComponentResourceConstructor is allowed in companion object of trait annotated with @ComponentResourceImport
      |      def apply(): String = ???
      |          ^
    """

    """
    object NotAResourceCompanion {
      @ComponentResourceStaticMethod("foo")
      def foo(): String = ???
    }
    """ hasErrors
    """
      |newSource1.scala:8: error: scala.scalajs.component.annotation.ComponentResourceStaticMethod("foo") is allowed in companion object of trait annotated with @ComponentResourceImport
      |      def foo(): String = ???
      |          ^
    """
  }

  @Test def resourceValidExample: Unit = {
    """
    @ComponentResourceImport("test:io/streams", "input-stream")
    trait InputStream {
      @ComponentResourceMethod("read")
      def read(len: ULong): cm.Result[Array[UByte], String] = cm.native

      @ComponentResourceMethod("blocking-read")
      def blockingRead(len: ULong): cm.Result[Array[UByte], String] = cm.native

      @ComponentResourceDrop
      def close(): Unit = cm.native
    }

    @ComponentResourceImport("test:io/streams", "output-stream")
    trait OutputStream {
      @ComponentResourceMethod("write")
      def write(data: Array[UByte]): cm.Result[Unit, String] = cm.native

      @ComponentResourceMethod("flush")
      def flush(): cm.Result[Unit, String] = cm.native

      @ComponentResourceDrop
      def close(): Unit = cm.native
    }
    object OutputStream {
      @ComponentResourceConstructor
      def apply(): OutputStream = cm.native

      @ComponentResourceConstructor
      def apply(x: Int): OutputStream = cm.native
    }
    """.hasNoWarns()
  }

  // --- Component Variant Tests ---

  @Test def variantMustBeSealed: Unit = {
    """
    @ComponentVariant
    trait NotSealed
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentVariant can only be used on sealed traits or sealed abstract classes
      |    trait NotSealed
      |          ^
    """
  }

  @Test def variantMustHaveAtLeastOneCase: Unit = {
    """
    @ComponentVariant
    sealed trait Empty
    """ hasErrors
    """
      |newSource1.scala:7: error: Component variant 'Empty' must have at least one case
      |    sealed trait Empty
      |                 ^
    """
  }

  @Test def variantCasesMustBeCaseClassOrObject: Unit = {
    """
    @ComponentVariant
    sealed trait MyVariant
    object MyVariant {
      class NotACase extends MyVariant
    }
    """ hasErrors
    """
      |newSource1.scala:9: error: Component variant case 'NotACase' must be a case class or case object
      |      class NotACase extends MyVariant
      |            ^
    """
  }

  @Test def variantCaseClassMustHaveAtMostOneField: Unit = {
    """
    @ComponentVariant
    sealed trait MyVariant
    object MyVariant {
      final case class TooManyFields(x: Int, y: String) extends MyVariant
    }
    """ hasErrors
    """
      |newSource1.scala:9: error: Component variant case 'TooManyFields' must have exactly one field, found 2.
      |      final case class TooManyFields(x: Int, y: String) extends MyVariant
      |                       ^
    """
  }

  @Test def variantCaseFieldMustBeNamedValue: Unit = {
    """
    @ComponentVariant
    sealed trait MyVariant
    object MyVariant {
      final case class WrongName(data: Int) extends MyVariant
    }
    """ hasErrors
    """
      |newSource1.scala:9: error: Component variant case 'WrongName' field must be named 'value', found 'data'.
      |      final case class WrongName(data: Int) extends MyVariant
      |                                 ^
    """
  }

  @Test def variantCaseFieldMustBeCompatibleType: Unit = {
    """
    class NotCompatible

    @ComponentVariant
    sealed trait MyVariant
    object MyVariant {
      final case class InvalidType(value: NotCompatible) extends MyVariant
    }
    """ hasErrors
    """
      |newSource1.scala:11: error: Field 'value' has type 'NotCompatible' which is not compatible with Component Model.
      |      final case class InvalidType(value: NotCompatible) extends MyVariant
      |                                   ^
    """
  }

  @Test def variantValidCaseClassWithValue: Unit = {
    """
    @ComponentVariant
    sealed trait MyVariant
    object MyVariant {
      final case class IntValue(value: Int) extends MyVariant
      final case class StringValue(value: String) extends MyVariant
      final case object None extends MyVariant
    }
    """.hasNoWarns()
  }

  // --- Component Record Tests ---

  @Test def recordMustBeCaseClass: Unit = {
    """
    @ComponentRecord
    class NotCaseClass(x: Int, y: Int)
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentRecord can only be used on case classes
      |    class NotCaseClass(x: Int, y: Int)
      |          ^
    """

    """
    @ComponentRecord
    trait NotCaseClass
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentRecord can only be used on case classes
      |    trait NotCaseClass
      |          ^
    """

    """
    @ComponentRecord
    object NotCaseClass
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentRecord can only be used on case classes
      |    object NotCaseClass
      |           ^
    """
  }

  @Test def recordMustBeFinal: Unit = {
    """
    @ComponentRecord
    case class NotFinal(x: Int, y: Int)
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentRecord case class must be final
      |    case class NotFinal(x: Int, y: Int)
      |               ^
    """
  }

  @Test def recordFieldsMustBeCompatible: Unit = {
    """
    class NotCompatible

    @ComponentRecord
    final case class InvalidRecord(x: Int, y: NotCompatible)
    """ hasErrors
    """
      |newSource1.scala:9: error: Field 'y' has type 'NotCompatible' which is not compatible with Component Model
      |    final case class InvalidRecord(x: Int, y: NotCompatible)
      |                                           ^
    """

    """
    class NotCompatible

    @ComponentRecord
    final case class MultipleInvalid(a: NotCompatible, b: String, c: NotCompatible)
    """ hasErrors
    """
      |newSource1.scala:9: error: Field 'a' has type 'NotCompatible' which is not compatible with Component Model
      |    final case class MultipleInvalid(a: NotCompatible, b: String, c: NotCompatible)
      |                                     ^
      |newSource1.scala:9: error: Field 'c' has type 'NotCompatible' which is not compatible with Component Model
      |    final case class MultipleInvalid(a: NotCompatible, b: String, c: NotCompatible)
      |                                                                  ^
    """
  }

  @Test def recordValidExamples: Unit = {
    """
    @ComponentRecord
    final case class Point(x: Int, y: Int)

    @ComponentRecord
    final case class Person(name: String, age: Int)

    @ComponentRecord
    final case class Empty()
    """.hasNoWarns()
  }

  // --- Component Flags Tests ---

  @Test def flagsMustBeCaseClass: Unit = {
    """
    @ComponentFlags(1)
    class NotCaseClass(value: Int)
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentFlags can only be used on case classes
      |    class NotCaseClass(value: Int)
      |          ^
    """

    """
    @ComponentFlags(1)
    trait NotCaseClass
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentFlags can only be used on case classes
      |    trait NotCaseClass
      |          ^
    """
  }

  @Test def flagsMustBeFinal: Unit = {
    """
    @ComponentFlags(1)
    case class NotFinal(value: Int)
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentFlags case class must be final
      |    case class NotFinal(value: Int)
      |               ^
    """
  }

  @Test def flagsMustNotExtendAnyVal: Unit = {
    """
    @ComponentFlags(1)
    final case class ValueClass(value: Int) extends AnyVal
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentFlags case class must NOT extend AnyVal. Use a regular case class instead.
      |    final case class ValueClass(value: Int) extends AnyVal
      |                     ^
    """
  }

  @Test def flagsMustHaveOneParameter: Unit = {
    """
    @ComponentFlags(1)
    final case class NoParameters()
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentFlags case class must have exactly one parameter, found 0
      |    final case class NoParameters()
      |                     ^
    """

    """
    @ComponentFlags(2)
    final case class TwoParameters(value: Int, other: Int)
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentFlags case class must have exactly one parameter, found 2
      |    final case class TwoParameters(value: Int, other: Int)
      |                     ^
    """
  }

  @Test def flagsParameterMustBeIntNamedValue: Unit = {
    """
    @ComponentFlags(1)
    final case class WrongType(value: String)
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentFlags case class parameter must be of type Int, found 'String'
      |    final case class WrongType(value: String)
      |                               ^
    """

    """
    @ComponentFlags(1)
    final case class WrongName(data: Int)
    """ hasErrors
    """
      |newSource1.scala:7: error: @ComponentFlags case class parameter must be named 'value', found 'data'
      |    final case class WrongName(data: Int)
      |                               ^
    """
  }

  @Test def flagsValidExamples: Unit = {
    """
    @ComponentFlags(3)
    final case class MyFlags(value: Int) {
      def |(other: MyFlags): MyFlags = MyFlags(value | other.value)
      def &(other: MyFlags): MyFlags = MyFlags(value & other.value)
    }
    object MyFlags {
      val Flag0 = MyFlags(1 << 0)
      val Flag1 = MyFlags(1 << 1)
      val Flag2 = MyFlags(1 << 2)
    }

    @ComponentFlags(2)
    final case class SimpleFlags(value: Int)
    object SimpleFlags {
      val A = SimpleFlags(1 << 0)
      val B = SimpleFlags(1 << 1)
    }
    """.hasNoWarns()
  }

  // --- Component Import Function Tests ---

  @Test def componentImportMustBeInPublicObject: Unit = {
    """
    class MyClass {
      @ComponentImport("test:module", "in-class")
      def inClass(x: Int): Int = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:8: error: scala.scalajs.component.annotation.ComponentImport("test:module", "in-class") methods must be defined in a public object
      |      def inClass(x: Int): Int = cm.native
      |          ^
    """

    """
    trait MyTrait {
      @ComponentImport("test:module", "in-trait")
      def inTrait(x: Int): Int = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:8: error: scala.scalajs.component.annotation.ComponentImport("test:module", "in-trait") methods must be defined in a public object
      |      def inTrait(x: Int): Int = cm.native
      |          ^
    """

    """
    private object PrivateObject {
      @ComponentImport("test:module", "in-private")
      def inPrivate(x: Int): Int = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:8: error: scala.scalajs.component.annotation.ComponentImport("test:module", "in-private") methods must be defined in a public object
      |      def inPrivate(x: Int): Int = cm.native
      |          ^
    """
  }

  @Test def componentImportMustBePublic: Unit = {
    """
    object MyFunctions {
      @ComponentImport("test:module", "private-func")
      private def privateFunc(x: Int): Int = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:8: error: scala.scalajs.component.annotation.ComponentImport("test:module", "private-func") methods must be public
      |      private def privateFunc(x: Int): Int = cm.native
      |                  ^
    """

    """
    object MyFunctions {
      @ComponentImport("test:module", "protected-func")
      protected def protectedFunc(x: Int): Int = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:8: error: scala.scalajs.component.annotation.ComponentImport("test:module", "protected-func") methods must be public
      |      protected def protectedFunc(x: Int): Int = cm.native
      |                    ^
    """
  }

  @Test def componentImportCannotHaveTypeParameters: Unit = {
    """
    object MyFunctions {
      @ComponentImport("test:module", "generic-func")
      def genericFunc[T](x: T): Int = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:8: error: scala.scalajs.component.annotation.ComponentImport("test:module", "generic-func") methods cannot have type parameters
      |      def genericFunc[T](x: T): Int = cm.native
      |          ^
    """
  }

  @Test def componentImportCannotHaveRepeatedParameters: Unit = {
    """
    object MyFunctions {
      @ComponentImport("test:module", "varargs-func")
      def varargsFunc(xs: Int*): Unit = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:8: error: scala.scalajs.component.annotation.ComponentImport("test:module", "varargs-func") methods may not have repeated parameters
      |      def varargsFunc(xs: Int*): Unit = cm.native
      |          ^
    """
  }

  @Test def componentImportCannotHaveDefaultParameters: Unit = {
    """
    object MyFunctions {
      @ComponentImport("test:module", "default-param")
      def defaultParam(x: Int = 42): Int = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:8: error: scala.scalajs.component.annotation.ComponentImport("test:module", "default-param") methods may not have default parameters
      |      def defaultParam(x: Int = 42): Int = cm.native
      |          ^
    """
  }

  @Test def componentImportParametersMustBeCompatible: Unit = {
    """
    class NotCompatible

    object MyFunctions {
      @ComponentImport("test:module", "invalid-param")
      def invalidParam(x: NotCompatible): Unit = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:10: error: Parameter 'x' has type 'NotCompatible' which is not compatible with Component Model
      |      def invalidParam(x: NotCompatible): Unit = cm.native
      |          ^
    """
  }

  @Test def componentImportReturnTypeMustBeCompatible: Unit = {
    """
    class NotCompatible

    object MyFunctions {
      @ComponentImport("test:module", "invalid-return")
      def invalidReturn(x: Int): NotCompatible = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:10: error: Return type 'NotCompatible' is not compatible with Component Model
      |      def invalidReturn(x: Int): NotCompatible = cm.native
      |          ^
    """
  }

  @Test def componentImportCannotOverride: Unit = {
    """
    trait Base {
      def baseMethod(): Int
    }

    object MyFunctions extends Base {
      @ComponentImport("test:module", "override-func")
      def baseMethod(): Int = cm.native
    }
    """ hasErrors
    """
      |newSource1.scala:12: error: An scala.scalajs.component.annotation.ComponentImport("test:module", "override-func") member cannot implement the inherited member Base.baseMethod
      |      def baseMethod(): Int = cm.native
      |          ^
    """
  }

  @Test def componentImportCannotBeOnLocalDefinition: Unit = {
    """
    object MyFunctions {
      def outer(): Unit = {
        @ComponentImport("test:module", "local-func")
        def localFunc(x: Int): Int = cm.native
      }
    }
    """ hasErrors
    """
      |newSource1.scala:9: error: scala.scalajs.component.annotation.ComponentImport("test:module", "local-func") is not allowed on local definitions
      |        def localFunc(x: Int): Int = cm.native
      |            ^
    """
  }

  @Test def componentImportCannotBeOnConstructor: Unit = {
    """
    class MyClass @ComponentImport("test:module", "ctor")() {}
    """ hasErrors
    """
      |newSource1.scala:7: error: scala.scalajs.component.annotation.ComponentImport("test:module", "ctor") is not allowed on constructor
      |
      |   ^
    """
  }

  @Test def componentImportValidExamples: Unit = {
    """
    object MyImports {
      @ComponentImport("test:module", "add")
      def add(a: Int, b: Int): Int = cm.native

      @ComponentImport("test:module", "greet")
      def greet(name: String): String = cm.native

      @ComponentImport("test:module", "process")
      def process(data: Array[UByte]): cm.Result[Unit, String] = cm.native

      @ComponentImport("test:module", "no-params")
      def noParams(): Unit = cm.native

      @ComponentImport("test:module", "returns-optional")
      def returnsOptional(x: Int): java.util.Optional[String] = cm.native
    }

    @ComponentRecord
    final case class Point(x: Int, y: Int)

    object MoreImports {
      @ComponentImport("test:geom", "distance")
      def distance(p1: Point, p2: Point): Double = cm.native
    }
    """.hasNoWarns()
  }
}
