package scala.scalajs.wasi.sockets

package object udp {

  // Type definitions
  type Pollable = scala.scalajs.wasi.io.poll.Pollable

  type Network = scala.scalajs.wasi.sockets.network.Network

  type ErrorCode = scala.scalajs.wasi.sockets.network.ErrorCode

  type IpSocketAddress = scala.scalajs.wasi.sockets.network.IpSocketAddress

  type IpAddressFamily = scala.scalajs.wasi.sockets.network.IpAddressFamily

  @scala.scalajs.wit.annotation.WitRecord
  final case class IncomingDatagram(data: Array[scala.scalajs.wit.unsigned.UByte], remoteAddress: scala.scalajs.wasi.sockets.network.IpSocketAddress)

  @scala.scalajs.wit.annotation.WitRecord
  final case class OutgoingDatagram(data: Array[scala.scalajs.wit.unsigned.UByte], remoteAddress: java.util.Optional[scala.scalajs.wasi.sockets.network.IpSocketAddress])

  // Resources
  @scala.scalajs.wit.annotation.WitResourceImport("wasi:sockets/udp@0.2.0", "udp-socket")
  trait UdpSocket {
    @scala.scalajs.wit.annotation.WitResourceMethod("start-bind")
    def startBind(network: Network, localAddress: scala.scalajs.wasi.sockets.network.IpSocketAddress): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("finish-bind")
    def finishBind(): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("stream")
    def stream(remoteAddress: java.util.Optional[scala.scalajs.wasi.sockets.network.IpSocketAddress]): scala.scalajs.wit.Result[scala.scalajs.wit.Tuple2[IncomingDatagramStream, OutgoingDatagramStream], scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("local-address")
    def localAddress(): scala.scalajs.wit.Result[scala.scalajs.wasi.sockets.network.IpSocketAddress, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("remote-address")
    def remoteAddress(): scala.scalajs.wit.Result[scala.scalajs.wasi.sockets.network.IpSocketAddress, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("address-family")
    def addressFamily(): scala.scalajs.wasi.sockets.network.IpAddressFamily = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("unicast-hop-limit")
    def unicastHopLimit(): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.UByte, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("set-unicast-hop-limit")
    def setUnicastHopLimit(value: scala.scalajs.wit.unsigned.UByte): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("receive-buffer-size")
    def receiveBufferSize(): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.ULong, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("set-receive-buffer-size")
    def setReceiveBufferSize(value: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("send-buffer-size")
    def sendBufferSize(): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.ULong, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("set-send-buffer-size")
    def setSendBufferSize(value: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("subscribe")
    def subscribe(): Pollable = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceDrop
    def close(): Unit = scala.scalajs.wit.native
  }
  object UdpSocket {
  }

  @scala.scalajs.wit.annotation.WitResourceImport("wasi:sockets/udp@0.2.0", "incoming-datagram-stream")
  trait IncomingDatagramStream {
    @scala.scalajs.wit.annotation.WitResourceMethod("receive")
    def receive(maxResults: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[Array[IncomingDatagram], scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("subscribe")
    def subscribe(): Pollable = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceDrop
    def close(): Unit = scala.scalajs.wit.native
  }
  object IncomingDatagramStream {
  }

  @scala.scalajs.wit.annotation.WitResourceImport("wasi:sockets/udp@0.2.0", "outgoing-datagram-stream")
  trait OutgoingDatagramStream {
    @scala.scalajs.wit.annotation.WitResourceMethod("check-send")
    def checkSend(): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.ULong, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("send")
    def send(datagrams: Array[OutgoingDatagram]): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.ULong, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("subscribe")
    def subscribe(): Pollable = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceDrop
    def close(): Unit = scala.scalajs.wit.native
  }
  object OutgoingDatagramStream {
  }

}
