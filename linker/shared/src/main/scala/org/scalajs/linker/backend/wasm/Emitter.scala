package org.scalajs.linker.backend.wasm

import org.scalajs.io._
import org.scalajs.logging._

import org.scalajs.linker._
import org.scalajs.linker.standard._

final class Emitter(config: CommonPhaseConfig) {

  val symbolRequirements: SymbolRequirement =
    Emitter.symbolRequirements(config.coreSpec)

  def emitAll(unit: LinkingUnit, builder: WasmModuleBuilder,
      logger: Logger): Unit = {
  }

}

object Emitter {

  private def symbolRequirements(coreSpec: CoreSpec): SymbolRequirement = {
    import coreSpec.semantics._
    import CheckedBehavior._

    val factory = SymbolRequirement.factory("emitter")
    import factory._

    def cond(p: Boolean)(v: => SymbolRequirement): SymbolRequirement =
      if (p) v else none()

    def assumingES6: Boolean = coreSpec.esFeatures.useECMAScript2015

    multiple(
        instantiateClass("O", "init___"),
        classData("O"),

        instantiateClass("jl_CloneNotSupportedException", "init___"),

        cond(asInstanceOfs != Unchecked) {
          instantiateClass("jl_ClassCastException", "init___T")
        },

        cond(arrayIndexOutOfBounds != Unchecked) {
          instantiateClass("jl_ArrayIndexOutOfBoundsException", "init___T")
        },

        cond(asInstanceOfs == Fatal || arrayIndexOutOfBounds == Fatal) {
          instantiateClass("sjsr_UndefinedBehaviorError", "init___jl_Throwable")
        },

        cond(moduleInit == Fatal) {
          instantiateClass("sjsr_UndefinedBehaviorError", "init___T")
        },

        instantiateClass("jl_Class", "init___O")
    )
  }

}
