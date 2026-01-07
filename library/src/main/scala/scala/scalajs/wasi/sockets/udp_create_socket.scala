package scala.scalajs.wasi.sockets

package object udp_create_socket {

  // Type definitions
  type Network = scala.scalajs.wasi.sockets.network.Network

  type ErrorCode = scala.scalajs.wasi.sockets.network.ErrorCode

  type IpAddressFamily = scala.scalajs.wasi.sockets.network.IpAddressFamily

  type UdpSocket = scala.scalajs.wasi.sockets.udp.UdpSocket

  // Functions
  @scala.scalajs.wit.annotation.WitImport("wasi:sockets/udp-create-socket@0.2.0", "create-udp-socket")
  def createUdpSocket(addressFamily: scala.scalajs.wasi.sockets.network.IpAddressFamily): scala.scalajs.wit.Result[UdpSocket, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native

}
