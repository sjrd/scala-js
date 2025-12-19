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
@ComponentVariant
sealed trait ErrorCode
object ErrorCode {
  case object DNS_timeout extends ErrorCode
  final case class DNSError(value: DNSErrorPayload) extends ErrorCode
  case object DestinationNotFound extends ErrorCode
  case object DestinationUnavailable extends ErrorCode
  case object DestinationIPProhibited extends ErrorCode
  case object DestinationIPUnroutable extends ErrorCode
  case object ConnectionRefused extends ErrorCode
  case object ConnectionTerminated extends ErrorCode
  case object ConnectionTimeout extends ErrorCode
  case object ConnectionReadTimeout extends ErrorCode
  case object ConnectionWriteTimeout extends ErrorCode
  case object ConnectionLimitReached extends ErrorCode
  case object TLSProtocolError extends ErrorCode
  case object TLSCertificateError extends ErrorCode
  final case class TLSAlertReceived(value: TLSAlertReceivedPayload) extends ErrorCode
  case object HTTPRequestDenied extends ErrorCode
  case object HTTPRequestLengthRequired extends ErrorCode
  final case class HTTPRequestBodySize(value: Optional[ULong]) extends ErrorCode
  case object HTTPRequestMethodInvalid extends ErrorCode
  case object HTTPRequestURIInvalid extends ErrorCode
  case object HTTPRequestURITooLong extends ErrorCode
  final case class HTTPRequestHeaderSectionSize(value: Optional[UInt]) extends ErrorCode
  final case class HTTPRequestHeaderSize(value: Optional[FieldSizePayload]) extends ErrorCode
  final case class HTTPRequestTrailerSectionSize(value: Optional[UInt]) extends ErrorCode
  final case class HTTPRequestTrailerSize(value: Optional[FieldSizePayload]) extends ErrorCode
  case object HTTPResponseIncomplete extends ErrorCode
  final case class HTTPResponseHeaderSectionSize(value: Optional[UInt]) extends ErrorCode
  final case class HTTPResponseHeaderSize(value: Optional[FieldSizePayload]) extends ErrorCode
  final case class HTTPResponseBodySize(value: Optional[ULong]) extends ErrorCode
  final case class HTTPResponseTrailerSectionSize(value: Optional[UInt]) extends ErrorCode
  final case class HTTPResponseTrailerSize(value: Optional[FieldSizePayload]) extends ErrorCode
  final case class HTTPResponseTransferCoding(value: Optional[String]) extends ErrorCode
  final case class HTTPResponseContentCoding(value: Optional[String]) extends ErrorCode
  case object HTTPResponseTimeout extends ErrorCode
  case object HTTPUpgradeFailed extends ErrorCode
  case object HTTPProtocolError extends ErrorCode
  case object LoopDetected extends ErrorCode
  case object ConfigurationError extends ErrorCode

  /** This is a catch-all error for anything that doesn't fit cleanly into a
    * more specific case. It also includes an optional string for an
    * unstructured description of the error. Users should not depend on the
    * string for diagnosing errors, as it's not required to be consistent
    * between implementations.
    */
  final case class InternalError(value: Optional[String]) extends ErrorCode



}

@ComponentRecord
final case class DNSErrorPayload(rcode: Optional[String], infoCode: Optional[UShort])

@ComponentRecord
final case class TLSAlertReceivedPayload(alertId: Optional[UByte], alertMessage: Optional[String])

@ComponentRecord
final case class FieldSizePayload(fieldName: Optional[String], fieldSize: Optional[UInt])
