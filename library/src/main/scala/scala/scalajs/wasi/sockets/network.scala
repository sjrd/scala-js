package scala.scalajs.wasi.sockets

package object network {

  // Type definitions
  @scala.scalajs.wit.annotation.WitVariant
  sealed trait ErrorCode
  object ErrorCode {
    case object Unknown extends ErrorCode
    case object AccessDenied extends ErrorCode
    case object NotSupported extends ErrorCode
    case object InvalidArgument extends ErrorCode
    case object OutOfMemory extends ErrorCode
    case object Timeout extends ErrorCode
    case object ConcurrencyConflict extends ErrorCode
    case object NotInProgress extends ErrorCode
    case object WouldBlock extends ErrorCode
    case object InvalidState extends ErrorCode
    case object NewSocketLimit extends ErrorCode
    case object AddressNotBindable extends ErrorCode
    case object AddressInUse extends ErrorCode
    case object RemoteUnreachable extends ErrorCode
    case object ConnectionRefused extends ErrorCode
    case object ConnectionReset extends ErrorCode
    case object ConnectionAborted extends ErrorCode
    case object DatagramTooLarge extends ErrorCode
    case object NameUnresolvable extends ErrorCode
    case object TemporaryResolverFailure extends ErrorCode
    case object PermanentResolverFailure extends ErrorCode
  }

  @scala.scalajs.wit.annotation.WitVariant
  sealed trait IpAddressFamily
  object IpAddressFamily {
    case object Ipv4 extends IpAddressFamily
    case object Ipv6 extends IpAddressFamily
  }

  type Ipv4Address = scala.scalajs.wit.Tuple4[scala.scalajs.wit.unsigned.UByte, scala.scalajs.wit.unsigned.UByte, scala.scalajs.wit.unsigned.UByte, scala.scalajs.wit.unsigned.UByte]

  type Ipv6Address = scala.scalajs.wit.Tuple8[scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort]

  @scala.scalajs.wit.annotation.WitVariant
  sealed trait IpAddress
  object IpAddress {
    final case class Ipv4(value: scala.scalajs.wit.Tuple4[scala.scalajs.wit.unsigned.UByte, scala.scalajs.wit.unsigned.UByte, scala.scalajs.wit.unsigned.UByte, scala.scalajs.wit.unsigned.UByte]) extends IpAddress
    final case class Ipv6(value: scala.scalajs.wit.Tuple8[scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort]) extends IpAddress
  }

  @scala.scalajs.wit.annotation.WitRecord
  final case class Ipv4SocketAddress(port: scala.scalajs.wit.unsigned.UShort, address: scala.scalajs.wit.Tuple4[scala.scalajs.wit.unsigned.UByte, scala.scalajs.wit.unsigned.UByte, scala.scalajs.wit.unsigned.UByte, scala.scalajs.wit.unsigned.UByte])

  @scala.scalajs.wit.annotation.WitRecord
  final case class Ipv6SocketAddress(port: scala.scalajs.wit.unsigned.UShort, flowInfo: scala.scalajs.wit.unsigned.UInt, address: scala.scalajs.wit.Tuple8[scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort, scala.scalajs.wit.unsigned.UShort], scopeId: scala.scalajs.wit.unsigned.UInt)

  @scala.scalajs.wit.annotation.WitVariant
  sealed trait IpSocketAddress
  object IpSocketAddress {
    final case class Ipv4(value: Ipv4SocketAddress) extends IpSocketAddress
    final case class Ipv6(value: Ipv6SocketAddress) extends IpSocketAddress
  }

  // Resources
  @scala.scalajs.wit.annotation.WitResourceImport("wasi:sockets/network@0.2.0", "network")
  trait Network {
    @scala.scalajs.wit.annotation.WitResourceDrop
    def close(): Unit = scala.scalajs.wit.native
  }
  object Network {
  }

}
