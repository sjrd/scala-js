package org.scalajs.linker.interface

import Fingerprint.FingerprintBuilder

final class WasmFeatures private (
  _exceptionHandling: Boolean,
  _targetPureWasm: Boolean,
  _componentModel: Boolean,
  _witDirectory: Option[String],
  _witWorld: Option[String]
) {
  import WasmFeatures._

  private def this() = {
    this(
      _exceptionHandling = true,
      _targetPureWasm = false,
      _componentModel = false,
      _witDirectory = None,
      _witWorld = None
    )
  }

  val exceptionHandling = _exceptionHandling
  val targetPureWasm = _targetPureWasm
  val componentModel = _componentModel
  val witDirectory = _witDirectory
  val witWorld = _witWorld

  def withExceptionHandling(exceptionHandling: Boolean): WasmFeatures =
    copy(exceptionHandling = exceptionHandling)

  def withTargetPureWasm(targetPureWasm: Boolean): WasmFeatures =
    copy(targetPureWasm = targetPureWasm)

  def withComponentModel(componentModel: Boolean): WasmFeatures =
    copy(componentModel = componentModel)

  def withWitDirectory(witDirectory: Option[String]): WasmFeatures =
    copy(witDirectory = witDirectory)

  def withWitWorld(witWorld: Option[String]): WasmFeatures =
    copy(witWorld = witWorld)

  override def equals(that: Any): Boolean = that match {
    case that: WasmFeatures =>
      this.exceptionHandling == that.exceptionHandling &&
      this.targetPureWasm == that.targetPureWasm &&
      this.componentModel == that.componentModel &&
      this.witDirectory == that.witDirectory &&
      this.witWorld == that.witWorld
    case _ =>
      false
  }

  override def hashCode(): Int = {
    import scala.util.hashing.MurmurHash3._
    var acc = HashSeed
    acc = mix(acc, exceptionHandling.##)
    acc = mix(acc, targetPureWasm.##)
    acc = mix(acc, componentModel.##)
    acc = mix(acc, witDirectory.##)
    acc = mixLast(acc, witWorld.##)
    finalizeHash(acc, 5)
  }

  override def toString(): String = {
    s"""WasmFeatures(
       |  exceptionHandling = $exceptionHandling,
       |  targetPureWasm = $targetPureWasm,
       |  componentModel = $componentModel,
       |  witDirectory = $witDirectory,
       |  witWorld = $witWorld
       |)""".stripMargin
  }

  private def copy(
      exceptionHandling: Boolean = this.exceptionHandling,
      targetPureWasm: Boolean = this.targetPureWasm,
      componentModel: Boolean = this.componentModel,
      witDirectory: Option[String] = this.witDirectory,
      witWorld: Option[String] = this.witWorld
  ): WasmFeatures = {
    new WasmFeatures(
        _exceptionHandling = exceptionHandling,
        _targetPureWasm = targetPureWasm,
        _componentModel = componentModel,
        _witDirectory = witDirectory,
        _witWorld = witWorld
    )
  }
}

object WasmFeatures {
  private val HashSeed =
    scala.util.hashing.MurmurHash3.stringHash(classOf[WasmFeatures].getName)

  val Defaults: WasmFeatures = new WasmFeatures()

  private[interface] implicit object WasmFeaturesFingerprint
      extends Fingerprint[WasmFeatures] {

    override def fingerprint(wasmFeatures: WasmFeatures): String = {
      new FingerprintBuilder("WasmFeatures")
        .addField("exceptionHandling", wasmFeatures.exceptionHandling)
        .addField("targetPureWasm", wasmFeatures.targetPureWasm)
        .addField("componentModel", wasmFeatures.componentModel)
        .addField("witDirectory", wasmFeatures.witDirectory)
        .addField("witWorld", wasmFeatures.witWorld)
        .build()
    }
  }
}
