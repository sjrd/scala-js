package scala.scalajs.wasi.sockets

package object ip_name_lookup {

  // Type definitions
  type Pollable = scala.scalajs.wasi.io.poll.Pollable

  type Network = scala.scalajs.wasi.sockets.network.Network

  type ErrorCode = scala.scalajs.wasi.sockets.network.ErrorCode

  type IpAddress = scala.scalajs.wasi.sockets.network.IpAddress

  // Resources
  @scala.scalajs.wit.annotation.WitResourceImport("wasi:sockets/ip-name-lookup@0.2.0", "resolve-address-stream")
  trait ResolveAddressStream {
    @scala.scalajs.wit.annotation.WitResourceMethod("resolve-next-address")
    def resolveNextAddress(): scala.scalajs.wit.Result[java.util.Optional[scala.scalajs.wasi.sockets.network.IpAddress], scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("subscribe")
    def subscribe(): Pollable = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceDrop
    def close(): Unit = scala.scalajs.wit.native
  }
  object ResolveAddressStream {
  }

  // Functions
  @scala.scalajs.wit.annotation.WitImport("wasi:sockets/ip-name-lookup@0.2.0", "resolve-addresses")
  def resolveAddresses(network: Network, name: String): scala.scalajs.wit.Result[ResolveAddressStream, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native

}
