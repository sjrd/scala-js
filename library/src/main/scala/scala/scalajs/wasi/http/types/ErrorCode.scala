package scala.scalajs.wasi.http.types

import java.util.Optional
import scala.scalajs.{component => cm}
import cm.annotation._
import cm.unsigned._

// variant error-code {
//   DNS-timeout,
//   DNS-error(DNS-error-payload),
//   destination-not-found,
//   destination-unavailable,
//   destination-IP-prohibited,
//   destination-IP-unroutable,
//   connection-refused,
//   connection-terminated,
//   connection-timeout,
//   connection-read-timeout,
//   connection-write-timeout,
//   connection-limit-reached,
//   TLS-protocol-error,
//   TLS-certificate-error,
//   TLS-alert-received(TLS-alert-received-payload),
//   HTTP-request-denied,
//   HTTP-request-length-required,
//   HTTP-request-body-size(option<u64>),
//   HTTP-request-method-invalid,
//   HTTP-request-URI-invalid,
//   HTTP-request-URI-too-long,
//   HTTP-request-header-section-size(option<u32>),
//   HTTP-request-header-size(option<field-size-payload>),
//   HTTP-request-trailer-section-size(option<u32>),
//   HTTP-request-trailer-size(field-size-payload),
//   HTTP-response-incomplete,
//   HTTP-response-header-section-size(option<u32>),
//   HTTP-response-header-size(field-size-payload),
//   HTTP-response-body-size(option<u64>),
//   HTTP-response-trailer-section-size(option<u32>),
//   HTTP-response-trailer-size(field-size-payload),
//   HTTP-response-transfer-coding(option<string>),
//   HTTP-response-content-coding(option<string>),
//   HTTP-response-timeout,
//   HTTP-upgrade-failed,
//   HTTP-protocol-error,
//   loop-detected,
//   configuration-error,
//   /// This is a catch-all error for anything that doesn't fit cleanly into a
//   /// more specific case. It also includes an optional string for an
//   /// unstructured description of the error. Users should not depend on the
//   /// string for diagnosing errors, as it's not required to be consistent
//   /// between implementations.
//   internal-error(option<string>)
// }
sealed trait ErrorCode extends cm.Variant
object ErrorCode {
  object DNS_timeout extends ErrorCode {
    val _index: Int = 0
    val value = ()
    type T = Unit
  }

  class DNSError(val value: DNSErrorPayload) extends ErrorCode {
    val _index: Int = 1
    type T = DNSErrorPayload
  }

  object DestinationNotFound extends ErrorCode {
    val _index: Int = 2
    val value = ()
    type T = Unit
  }

  object DestinationUnavailable extends ErrorCode {
    val _index: Int = 3
    val value = ()
    type T = Unit
  }

  object DestinationIPProhibited extends ErrorCode {
    val _index: Int = 4
    val value = ()
    type T = Unit
  }

  object DestinationIPUnroutable extends ErrorCode {
    val _index: Int = 5
    val value = ()
    type T = Unit
  }

  object ConnectionRefused extends ErrorCode {
    val _index: Int = 6
    val value = ()
    type T = Unit
  }
  object ConnectionTerminated extends ErrorCode {
    val _index: Int = 7
    val value = ()
    type T = Unit
  }
  object ConnectionTimeout extends ErrorCode {
    val _index: Int = 8
    val value = ()
    type T = Unit
  }
  object ConnectionReadTimeout extends ErrorCode {
    val _index: Int = 9
    val value = ()
    type T = Unit
  }
  object ConnectionWriteTimeout extends ErrorCode {
    val _index: Int = 10
    val value = ()
    type T = Unit
  }
  object ConnectionLimitReached extends ErrorCode {
    val _index: Int = 11
    val value = ()
    type T = Unit
  }
  object TLSProtocolError extends ErrorCode {
    val _index: Int = 12
    val value = ()
    type T = Unit
  }
  object TLSCertificateError extends ErrorCode {
    val _index: Int = 13
    val value = ()
    type T = Unit
  }
  class TLSAlertReceived(val value: TLSAlertReceivedPayload) extends ErrorCode {
    val _index: Int = 14
    type T = TLSAlertReceivedPayload
  }

  object HTTPRequestDenied extends ErrorCode {
    val _index: Int = 15
    val value = ()
    type T = Unit
  }
  object HTTPRequestLengthRequired extends ErrorCode {
    val _index: Int = 16
    val value = ()
    type T = Unit
  }
  class HTTPRequestBodySize(val value: Optional[ULong]) extends ErrorCode {
    val _index: Int = 17
    type T = Optional[ULong]
  }
  object HTTPRequestMethodInvalid extends ErrorCode {
    val _index: Int = 18
    val value = ()
    type T = Unit
  }
  object HTTPRequestURIInvalid extends ErrorCode {
    val _index: Int = 19
    val value = ()
    type T = Unit
  }
  object HTTPRequestURITooLong extends ErrorCode {
    val _index: Int = 20
    val value = ()
    type T = Unit
  }
  class HTTPRequestHeaderSectionSize(val value: Optional[UInt]) extends ErrorCode {
    val _index: Int = 21
    type T = Optional[UInt]
  }
  class HTTPRequestHeaderSize(val value: Optional[FieldSizePayload]) extends ErrorCode {
    val _index: Int = 22
    type T = Optional[FieldSizePayload]
  }
  class HTTPRequestTrailerSectionSize(val value: Optional[UInt]) extends ErrorCode {
    val _index: Int = 23
    type T = Optional[UInt]
  }
  class HTTPRequestTrailerSize(val value: Optional[FieldSizePayload]) extends ErrorCode {
    val _index: Int = 24
    type T = Optional[FieldSizePayload]
  }
  object HTTPResponseIncomplete extends ErrorCode {
    val _index: Int = 25
    val value = ()
    type T = Unit
  }

  class HTTPResponseHeaderSectionSize(val value: Optional[UInt]) extends ErrorCode {
    val _index: Int = 26
    type T = Optional[UInt]
  }
  class HTTPResponseHeaderSize(val value: Optional[FieldSizePayload]) extends ErrorCode {
    val _index: Int = 27
    type T = Optional[FieldSizePayload]
  }
  class HTTPResponseBodySize(val value: Optional[ULong]) extends ErrorCode {
    val _index: Int = 28
    type T = Optional[ULong]
  }
  class HTTPResponseTrailerSectionSize(val value: Optional[UInt]) extends ErrorCode {
    val _index: Int = 29
    type T = Optional[UInt]
  }
  class HTTPResponseTrailerSize(val value: Optional[FieldSizePayload]) extends ErrorCode {
    val _index: Int = 30
    type T = Optional[FieldSizePayload]
  }
  class HTTPResponseTransferCoding(val value: Optional[String]) extends ErrorCode {
    val _index: Int = 31
    type T = Optional[String]
  }
  class HTTPResponseContentCoding(val value: Optional[String]) extends ErrorCode {
    val _index: Int = 32
    type T = Optional[String]
  }
  object HTTPResponseTimeout extends ErrorCode {
    val _index: Int = 33
    val value = ()
    type T = Unit
  }
  object HTTPUpgradeFailed extends ErrorCode {
    val _index: Int = 34
    val value = ()
    type T = Unit
  }
  object HTTPProtocolError extends ErrorCode {
    val _index: Int = 35
    val value = ()
    type T = Unit
  }
  object LoopDetected extends ErrorCode {
    val _index: Int = 36
    val value = ()
    type T = Unit
  }
  object ConfigurationError extends ErrorCode {
    val _index: Int = 37
    val value = ()
    type T = Unit
  }

  /** This is a catch-all error for anything that doesn't fit cleanly into a
    * more specific case. It also includes an optional string for an
    * unstructured description of the error. Users should not depend on the
    * string for diagnosing errors, as it's not required to be consistent
    * between implementations.
    */
  class InternalError(val value: Optional[String]) extends ErrorCode {
    val _index: Int = 38
    type T = Optional[String]
  }



}

@ComponentRecord
final case class DNSErrorPayload(rcode: Optional[String], infoCode: Optional[UShort])

@ComponentRecord
final case class TLSAlertReceivedPayload(alertId: Optional[UByte], alertMessage: Optional[String])

@ComponentRecord
final case class FieldSizePayload(fieldName: Optional[String], fieldSize: Optional[UInt])
