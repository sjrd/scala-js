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

}
