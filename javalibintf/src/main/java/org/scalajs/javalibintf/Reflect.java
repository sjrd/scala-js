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

package org.scalajs.javalibintf;

import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class Reflect {
  private Reflect() {}

  public static <T> void registerLoadableModuleClass(
      String fqcn, Class<T> runtimeClass, Supplier<T> moduleSupplier) {
    throw new AssertionError("stub");
  }

  public static <T> void registerInstantiatableClass(
      String fqcn, Class<T> runtimeClass,
      Entry<Class<?>[], Function<Object[], Object>> constructors) {
    throw new AssertionError("stub");
  }

  /**
   * Reflectively looks up a loadable module class.
   *
   * A module class is the technical term referring to the class of a Scala
   * `object`. The object or one of its super types (classes or traits) must
   * be annotated with
   * [[scala.scalajs.reflect.annotation.EnableReflectiveInstantiation @EnableReflectiveInstantiation]].
   * Moreover, the object must be "static", i.e., declared at the top-level of
   * a package or inside a static object.
   *
   * If the module class cannot be found, either because it does not exist,
   * was not `@EnableReflectiveInstantiation` or was not static, this method
   * returns `None`.
   *
   * @param fqcn
   *   Fully-qualified name of the module class, including its trailing `$`
   */
  public static Optional<LoadableModuleClass> lookupLoadableModuleClass(String fqcn) {
    throw new AssertionError("stub");
  }

  /**
   * Reflectively looks up an instantiable class.
   *
   * The class or one of its super types (classes or traits) must be annotated
   * with
   * [[scala.scalajs.reflect.annotation.EnableReflectiveInstantiation @EnableReflectiveInstantiation]].
   * Moreover, the class must not be abstract, nor be a local class (i.e., a
   * class defined inside a `def`). Inner classes (defined inside another
   * class) are supported.
   *
   * If the class cannot be found, either because it does not exist,
   * was not `@EnableReflectiveInstantiation` or was abstract or local, this
   * method returns `None`.
   *
   * @param fqcn
   *   Fully-qualified name of the class
   */
  public static Optional<InstantiatableClass> lookupInstantiatableClass(String fqcn) {
    throw new AssertionError("stub");
  }

  public static interface LoadableModuleClass {
    public Class<?> getRuntimeClass();

    public Object loadModule();
  }

  public static interface InstantiatableClass {
    public Class<?> getRuntimeClass();

    public InvokableConstructor[] getDeclaredConstructors();
  }

  public static interface InvokableConstructor {
    public Class<?>[] getParameterTypes();

    public Object newInstance(Object... args);
  }
}
