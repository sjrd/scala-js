package scala.scalajs.wasi.sockets

package object tcp_create_socket {

  // Type definitions
  type Network = scala.scalajs.wasi.sockets.network.Network

  type ErrorCode = scala.scalajs.wasi.sockets.network.ErrorCode

  type IpAddressFamily = scala.scalajs.wasi.sockets.network.IpAddressFamily

  type TcpSocket = scala.scalajs.wasi.sockets.tcp.TcpSocket

  // Functions
  @scala.scalajs.wit.annotation.WitImport("wasi:sockets/tcp-create-socket@0.2.0", "create-tcp-socket")
  def createTcpSocket(addressFamily: scala.scalajs.wasi.sockets.network.IpAddressFamily): scala.scalajs.wit.Result[TcpSocket, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native

}
