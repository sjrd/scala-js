package scala.scalajs.wasi.filesystem

package object types {

  // Type definitions
  type InputStream = scala.scalajs.wasi.io.streams.InputStream

  type OutputStream = scala.scalajs.wasi.io.streams.OutputStream

  type Error = scala.scalajs.wasi.io.error.Error

  type Datetime = scala.scalajs.wasi.clocks.wall_clock.Datetime

  type Filesize = scala.scalajs.wit.unsigned.ULong

  @scala.scalajs.wit.annotation.WitVariant
  sealed trait DescriptorType
  object DescriptorType {
    case object Unknown extends DescriptorType
    case object BlockDevice extends DescriptorType
    case object CharacterDevice extends DescriptorType
    case object Directory extends DescriptorType
    case object Fifo extends DescriptorType
    case object SymbolicLink extends DescriptorType
    case object RegularFile extends DescriptorType
    case object Socket extends DescriptorType
  }

  @scala.scalajs.wit.annotation.WitFlags(6)
  final case class DescriptorFlags(value: Int) {
    def |(other: DescriptorFlags): DescriptorFlags = DescriptorFlags(value | other.value)
    def &(other: DescriptorFlags): DescriptorFlags = DescriptorFlags(value & other.value)
    def ^(other: DescriptorFlags): DescriptorFlags = DescriptorFlags(value ^ other.value)
    def unary_~ : DescriptorFlags = DescriptorFlags(~value)
    def contains(other: DescriptorFlags): Boolean = (value & other.value) == other.value
  }
  object DescriptorFlags {
    val read = DescriptorFlags(1 << 0)
    val write = DescriptorFlags(1 << 1)
    val fileIntegritySync = DescriptorFlags(1 << 2)
    val dataIntegritySync = DescriptorFlags(1 << 3)
    val requestedWriteSync = DescriptorFlags(1 << 4)
    val mutateDirectory = DescriptorFlags(1 << 5)
  }

  @scala.scalajs.wit.annotation.WitFlags(1)
  final case class PathFlags(value: Int) {
    def |(other: PathFlags): PathFlags = PathFlags(value | other.value)
    def &(other: PathFlags): PathFlags = PathFlags(value & other.value)
    def ^(other: PathFlags): PathFlags = PathFlags(value ^ other.value)
    def unary_~ : PathFlags = PathFlags(~value)
    def contains(other: PathFlags): Boolean = (value & other.value) == other.value
  }
  object PathFlags {
    val symlinkFollow = PathFlags(1 << 0)
  }

  @scala.scalajs.wit.annotation.WitFlags(4)
  final case class OpenFlags(value: Int) {
    def |(other: OpenFlags): OpenFlags = OpenFlags(value | other.value)
    def &(other: OpenFlags): OpenFlags = OpenFlags(value & other.value)
    def ^(other: OpenFlags): OpenFlags = OpenFlags(value ^ other.value)
    def unary_~ : OpenFlags = OpenFlags(~value)
    def contains(other: OpenFlags): Boolean = (value & other.value) == other.value
  }
  object OpenFlags {
    val create = OpenFlags(1 << 0)
    val directory = OpenFlags(1 << 1)
    val exclusive = OpenFlags(1 << 2)
    val truncate = OpenFlags(1 << 3)
  }

  type LinkCount = scala.scalajs.wit.unsigned.ULong

  @scala.scalajs.wit.annotation.WitRecord
  final case class DescriptorStat(`type`: DescriptorType, linkCount: scala.scalajs.wit.unsigned.ULong, size: scala.scalajs.wit.unsigned.ULong, dataAccessTimestamp: java.util.Optional[scala.scalajs.wasi.clocks.wall_clock.Datetime], dataModificationTimestamp: java.util.Optional[scala.scalajs.wasi.clocks.wall_clock.Datetime], statusChangeTimestamp: java.util.Optional[scala.scalajs.wasi.clocks.wall_clock.Datetime])

  @scala.scalajs.wit.annotation.WitVariant
  sealed trait NewTimestamp
  object NewTimestamp {
    case object NoChange extends NewTimestamp
    case object Now extends NewTimestamp
    final case class Timestamp(value: scala.scalajs.wasi.clocks.wall_clock.Datetime) extends NewTimestamp
  }

  @scala.scalajs.wit.annotation.WitRecord
  final case class DirectoryEntry(`type`: DescriptorType, name: String)

  @scala.scalajs.wit.annotation.WitVariant
  sealed trait ErrorCode
  object ErrorCode {
    case object Access extends ErrorCode
    case object WouldBlock extends ErrorCode
    case object Already extends ErrorCode
    case object BadDescriptor extends ErrorCode
    case object Busy extends ErrorCode
    case object Deadlock extends ErrorCode
    case object Quota extends ErrorCode
    case object Exist extends ErrorCode
    case object FileTooLarge extends ErrorCode
    case object IllegalByteSequence extends ErrorCode
    case object InProgress extends ErrorCode
    case object Interrupted extends ErrorCode
    case object Invalid extends ErrorCode
    case object Io extends ErrorCode
    case object IsDirectory extends ErrorCode
    case object Loop extends ErrorCode
    case object TooManyLinks extends ErrorCode
    case object MessageSize extends ErrorCode
    case object NameTooLong extends ErrorCode
    case object NoDevice extends ErrorCode
    case object NoEntry extends ErrorCode
    case object NoLock extends ErrorCode
    case object InsufficientMemory extends ErrorCode
    case object InsufficientSpace extends ErrorCode
    case object NotDirectory extends ErrorCode
    case object NotEmpty extends ErrorCode
    case object NotRecoverable extends ErrorCode
    case object Unsupported extends ErrorCode
    case object NoTty extends ErrorCode
    case object NoSuchDevice extends ErrorCode
    case object Overflow extends ErrorCode
    case object NotPermitted extends ErrorCode
    case object Pipe extends ErrorCode
    case object ReadOnly extends ErrorCode
    case object InvalidSeek extends ErrorCode
    case object TextFileBusy extends ErrorCode
    case object CrossDevice extends ErrorCode
  }

  @scala.scalajs.wit.annotation.WitVariant
  sealed trait Advice
  object Advice {
    case object Normal extends Advice
    case object Sequential extends Advice
    case object Random extends Advice
    case object WillNeed extends Advice
    case object DontNeed extends Advice
    case object NoReuse extends Advice
  }

  @scala.scalajs.wit.annotation.WitRecord
  final case class MetadataHashValue(lower: scala.scalajs.wit.unsigned.ULong, upper: scala.scalajs.wit.unsigned.ULong)

  // Resources
  @scala.scalajs.wit.annotation.WitResourceImport("wasi:filesystem/types@0.2.0", "descriptor")
  trait Descriptor {
    @scala.scalajs.wit.annotation.WitResourceMethod("read-via-stream")
    def readViaStream(offset: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[InputStream, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("write-via-stream")
    def writeViaStream(offset: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[OutputStream, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("append-via-stream")
    def appendViaStream(): scala.scalajs.wit.Result[OutputStream, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("advise")
    def advise(offset: scala.scalajs.wit.unsigned.ULong, length: scala.scalajs.wit.unsigned.ULong, advice: Advice): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("sync-data")
    def syncData(): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("get-flags")
    def getFlags(): scala.scalajs.wit.Result[DescriptorFlags, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("get-type")
    def getType(): scala.scalajs.wit.Result[DescriptorType, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("set-size")
    def setSize(size: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("set-times")
    def setTimes(dataAccessTimestamp: NewTimestamp, dataModificationTimestamp: NewTimestamp): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("read")
    def read(length: scala.scalajs.wit.unsigned.ULong, offset: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[scala.scalajs.wit.Tuple2[Array[scala.scalajs.wit.unsigned.UByte], Boolean], ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("write")
    def write(buffer: Array[scala.scalajs.wit.unsigned.UByte], offset: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.ULong, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("read-directory")
    def readDirectory(): scala.scalajs.wit.Result[DirectoryEntryStream, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("sync")
    def sync(): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("create-directory-at")
    def createDirectoryAt(path: String): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("stat")
    def stat(): scala.scalajs.wit.Result[DescriptorStat, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("stat-at")
    def statAt(pathFlags: PathFlags, path: String): scala.scalajs.wit.Result[DescriptorStat, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("set-times-at")
    def setTimesAt(pathFlags: PathFlags, path: String, dataAccessTimestamp: NewTimestamp, dataModificationTimestamp: NewTimestamp): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("link-at")
    def linkAt(oldPathFlags: PathFlags, oldPath: String, newDescriptor: Descriptor, newPath: String): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("open-at")
    def openAt(pathFlags: PathFlags, path: String, openFlags: OpenFlags, flags: DescriptorFlags): scala.scalajs.wit.Result[Descriptor, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("readlink-at")
    def readlinkAt(path: String): scala.scalajs.wit.Result[String, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("remove-directory-at")
    def removeDirectoryAt(path: String): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("rename-at")
    def renameAt(oldPath: String, newDescriptor: Descriptor, newPath: String): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("symlink-at")
    def symlinkAt(oldPath: String, newPath: String): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("unlink-file-at")
    def unlinkFileAt(path: String): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("is-same-object")
    def isSameObject(other: Descriptor): Boolean = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("metadata-hash")
    def metadataHash(): scala.scalajs.wit.Result[MetadataHashValue, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceMethod("metadata-hash-at")
    def metadataHashAt(pathFlags: PathFlags, path: String): scala.scalajs.wit.Result[MetadataHashValue, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceDrop
    def close(): Unit = scala.scalajs.wit.native
  }
  object Descriptor {
  }

  @scala.scalajs.wit.annotation.WitResourceImport("wasi:filesystem/types@0.2.0", "directory-entry-stream")
  trait DirectoryEntryStream {
    @scala.scalajs.wit.annotation.WitResourceMethod("read-directory-entry")
    def readDirectoryEntry(): scala.scalajs.wit.Result[java.util.Optional[DirectoryEntry], ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceDrop
    def close(): Unit = scala.scalajs.wit.native
  }
  object DirectoryEntryStream {
  }

  // Functions
  @scala.scalajs.wit.annotation.WitImport("wasi:filesystem/types@0.2.0", "filesystem-error-code")
  def filesystemErrorCode(err: Error): java.util.Optional[ErrorCode] = scala.scalajs.wit.native

}
