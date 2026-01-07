package scala.scalajs.wasi.sockets

package object tcp {

  // Type definitions
  type InputStream = scala.scalajs.wasi.io.streams.InputStream

  type OutputStream = scala.scalajs.wasi.io.streams.OutputStream

  type Pollable = scala.scalajs.wasi.io.poll.Pollable

  type Duration = scala.scalajs.wit.unsigned.ULong

  type Network = scala.scalajs.wasi.sockets.network.Network

  type ErrorCode = scala.scalajs.wasi.sockets.network.ErrorCode

  type IpSocketAddress = scala.scalajs.wasi.sockets.network.IpSocketAddress

  type IpAddressFamily = scala.scalajs.wasi.sockets.network.IpAddressFamily

  @scala.scalajs.wit.annotation.WitVariant
  sealed trait ShutdownType
  object ShutdownType {
    case object Receive extends ShutdownType
    case object Send extends ShutdownType
    case object Both extends ShutdownType
  }

  // Resources
  @scala.scalajs.wit.annotation.WitResourceImport("wasi:sockets/tcp@0.2.0", "tcp-socket")
  trait TcpSocket {
    @scala.scalajs.wit.annotation.WitResourceMethod("start-bind")
    def startBind(network: Network, localAddress: scala.scalajs.wasi.sockets.network.IpSocketAddress): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("finish-bind")
    def finishBind(): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("start-connect")
    def startConnect(network: Network, remoteAddress: scala.scalajs.wasi.sockets.network.IpSocketAddress): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("finish-connect")
    def finishConnect(): scala.scalajs.wit.Result[scala.scalajs.wit.Tuple2[InputStream, OutputStream], scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("start-listen")
    def startListen(): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("finish-listen")
    def finishListen(): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("accept")
    def accept(): scala.scalajs.wit.Result[scala.scalajs.wit.Tuple3[TcpSocket, InputStream, OutputStream], scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("local-address")
    def localAddress(): scala.scalajs.wit.Result[scala.scalajs.wasi.sockets.network.IpSocketAddress, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("remote-address")
    def remoteAddress(): scala.scalajs.wit.Result[scala.scalajs.wasi.sockets.network.IpSocketAddress, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("is-listening")
    def isListening(): Boolean = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("address-family")
    def addressFamily(): scala.scalajs.wasi.sockets.network.IpAddressFamily = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("set-listen-backlog-size")
    def setListenBacklogSize(value: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("keep-alive-enabled")
    def keepAliveEnabled(): scala.scalajs.wit.Result[Boolean, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("set-keep-alive-enabled")
    def setKeepAliveEnabled(value: Boolean): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("keep-alive-idle-time")
    def keepAliveIdleTime(): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.ULong, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("set-keep-alive-idle-time")
    def setKeepAliveIdleTime(value: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("keep-alive-interval")
    def keepAliveInterval(): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.ULong, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("set-keep-alive-interval")
    def setKeepAliveInterval(value: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("keep-alive-count")
    def keepAliveCount(): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.UInt, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("set-keep-alive-count")
    def setKeepAliveCount(value: scala.scalajs.wit.unsigned.UInt): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("hop-limit")
    def hopLimit(): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.UByte, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("set-hop-limit")
    def setHopLimit(value: scala.scalajs.wit.unsigned.UByte): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
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
    @scala.scalajs.wit.annotation.WitResourceMethod("shutdown")
    def shutdown(shutdownType: ShutdownType): scala.scalajs.wit.Result[Unit, scala.scalajs.wasi.sockets.network.ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceDrop
    def close(): Unit = scala.scalajs.wit.native
  }
  object TcpSocket {
  }

}
