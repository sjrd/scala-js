package scala.scalajs.wasi.wasi.filesystem

package object types {

  // Type definitions
  type InputStream = scala.scalajs.wasi.wasi.io.streams.InputStream

  type OutputStream = scala.scalajs.wasi.wasi.io.streams.OutputStream

  type Error = scala.scalajs.wasi.wasi.io.error.Error

  type Datetime = scala.scalajs.wasi.wasi.clocks.wall_clock.Datetime

  type Filesize = scala.scalajs.wit.unsigned.ULong

  /** The type of a filesystem object referenced by a descriptor.
   *
   *  Note: This was called `filetype` in earlier versions of WASI.
   */
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

  /** Descriptor flags.
   *
   *  Note: This was called `fdflags` in earlier versions of WASI.
   */
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

  /** Flags determining the method of how paths are resolved.
   */
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

  /** Open flags used by `open-at`.
   */
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

  /** File attributes.
   *
   *  Note: This was called `filestat` in earlier versions of WASI.
   */
  @scala.scalajs.wit.annotation.WitRecord
  final case class DescriptorStat(`type`: DescriptorType, linkCount: scala.scalajs.wit.unsigned.ULong, size: scala.scalajs.wit.unsigned.ULong, dataAccessTimestamp: java.util.Optional[scala.scalajs.wasi.wasi.clocks.wall_clock.Datetime], dataModificationTimestamp: java.util.Optional[scala.scalajs.wasi.wasi.clocks.wall_clock.Datetime], statusChangeTimestamp: java.util.Optional[scala.scalajs.wasi.wasi.clocks.wall_clock.Datetime])

  /** When setting a timestamp, this gives the value to set it to.
   */
  @scala.scalajs.wit.annotation.WitVariant
  sealed trait NewTimestamp
  object NewTimestamp {
    case object NoChange extends NewTimestamp
    case object Now extends NewTimestamp
    final case class Timestamp(value: scala.scalajs.wasi.wasi.clocks.wall_clock.Datetime) extends NewTimestamp
  }

  /** A directory entry.
   */
  @scala.scalajs.wit.annotation.WitRecord
  final case class DirectoryEntry(`type`: DescriptorType, name: String)

  /** Error codes returned by functions, similar to `errno` in POSIX.
   *  Not all of these error codes are returned by the functions provided by this
   *  API; some are used in higher-level library layers, and others are provided
   *  merely for alignment with POSIX.
   */
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

  /** File or memory access pattern advisory information.
   */
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

  /** A 128-bit hash value, split into parts because wasm doesn't have a
   *  128-bit integer type.
   */
  @scala.scalajs.wit.annotation.WitRecord
  final case class MetadataHashValue(lower: scala.scalajs.wit.unsigned.ULong, upper: scala.scalajs.wit.unsigned.ULong)

  // Resources
  /** A descriptor is a reference to a filesystem object, which may be a file,
   *  directory, named pipe, special file, or other object on which filesystem
   *  calls may be made.
   */
  @scala.scalajs.wit.annotation.WitResourceImport("wasi:filesystem/types@0.2.0", "descriptor")
  trait Descriptor {
    /** Return a stream for reading from a file, if available.
     *
     *  May fail with an error-code describing why the file cannot be read.
     *
     *  Multiple read, write, and append streams may be active on the same open
     *  file and they do not interfere with each other.
     *
     *  Note: This allows using `read-stream`, which is similar to `read` in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("read-via-stream")
    def readViaStream(offset: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[InputStream, ErrorCode] = scala.scalajs.wit.native
    /** Return a stream for writing to a file, if available.
     *
     *  May fail with an error-code describing why the file cannot be written.
     *
     *  Note: This allows using `write-stream`, which is similar to `write` in
     *  POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("write-via-stream")
    def writeViaStream(offset: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[OutputStream, ErrorCode] = scala.scalajs.wit.native
    /** Return a stream for appending to a file, if available.
     *
     *  May fail with an error-code describing why the file cannot be appended.
     *
     *  Note: This allows using `write-stream`, which is similar to `write` with
     *  `O_APPEND` in in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("append-via-stream")
    def appendViaStream(): scala.scalajs.wit.Result[OutputStream, ErrorCode] = scala.scalajs.wit.native
    /** Provide file advisory information on a descriptor.
     *
     *  This is similar to `posix_fadvise` in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("advise")
    def advise(offset: scala.scalajs.wit.unsigned.ULong, length: scala.scalajs.wit.unsigned.ULong, advice: Advice): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    /** Synchronize the data of a file to disk.
     *
     *  This function succeeds with no effect if the file descriptor is not
     *  opened for writing.
     *
     *  Note: This is similar to `fdatasync` in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("sync-data")
    def syncData(): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    /** Get flags associated with a descriptor.
     *
     *  Note: This returns similar flags to `fcntl(fd, F_GETFL)` in POSIX.
     *
     *  Note: This returns the value that was the `fs_flags` value returned
     *  from `fdstat_get` in earlier versions of WASI.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("get-flags")
    def getFlags(): scala.scalajs.wit.Result[DescriptorFlags, ErrorCode] = scala.scalajs.wit.native
    /** Get the dynamic type of a descriptor.
     *
     *  Note: This returns the same value as the `type` field of the `fd-stat`
     *  returned by `stat`, `stat-at` and similar.
     *
     *  Note: This returns similar flags to the `st_mode & S_IFMT` value provided
     *  by `fstat` in POSIX.
     *
     *  Note: This returns the value that was the `fs_filetype` value returned
     *  from `fdstat_get` in earlier versions of WASI.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("get-type")
    def getType(): scala.scalajs.wit.Result[DescriptorType, ErrorCode] = scala.scalajs.wit.native
    /** Adjust the size of an open file. If this increases the file's size, the
     *  extra bytes are filled with zeros.
     *
     *  Note: This was called `fd_filestat_set_size` in earlier versions of WASI.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("set-size")
    def setSize(size: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    /** Adjust the timestamps of an open file or directory.
     *
     *  Note: This is similar to `futimens` in POSIX.
     *
     *  Note: This was called `fd_filestat_set_times` in earlier versions of WASI.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("set-times")
    def setTimes(dataAccessTimestamp: NewTimestamp, dataModificationTimestamp: NewTimestamp): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    /** Read from a descriptor, without using and updating the descriptor's offset.
     *
     *  This function returns a list of bytes containing the data that was
     *  read, along with a bool which, when true, indicates that the end of the
     *  file was reached. The returned list will contain up to `length` bytes; it
     *  may return fewer than requested, if the end of the file is reached or
     *  if the I/O operation is interrupted.
     *
     *  In the future, this may change to return a `stream<u8, error-code>`.
     *
     *  Note: This is similar to `pread` in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("read")
    def read(length: scala.scalajs.wit.unsigned.ULong, offset: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[scala.scalajs.wit.Tuple2[Array[scala.scalajs.wit.unsigned.UByte], Boolean], ErrorCode] = scala.scalajs.wit.native
    /** Write to a descriptor, without using and updating the descriptor's offset.
     *
     *  It is valid to write past the end of a file; the file is extended to the
     *  extent of the write, with bytes between the previous end and the start of
     *  the write set to zero.
     *
     *  In the future, this may change to take a `stream<u8, error-code>`.
     *
     *  Note: This is similar to `pwrite` in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("write")
    def write(buffer: Array[scala.scalajs.wit.unsigned.UByte], offset: scala.scalajs.wit.unsigned.ULong): scala.scalajs.wit.Result[scala.scalajs.wit.unsigned.ULong, ErrorCode] = scala.scalajs.wit.native
    /** Read directory entries from a directory.
     *
     *  On filesystems where directories contain entries referring to themselves
     *  and their parents, often named `.` and `..` respectively, these entries
     *  are omitted.
     *
     *  This always returns a new stream which starts at the beginning of the
     *  directory. Multiple streams may be active on the same directory, and they
     *  do not interfere with each other.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("read-directory")
    def readDirectory(): scala.scalajs.wit.Result[DirectoryEntryStream, ErrorCode] = scala.scalajs.wit.native
    /** Synchronize the data and metadata of a file to disk.
     *
     *  This function succeeds with no effect if the file descriptor is not
     *  opened for writing.
     *
     *  Note: This is similar to `fsync` in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("sync")
    def sync(): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    /** Create a directory.
     *
     *  Note: This is similar to `mkdirat` in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("create-directory-at")
    def createDirectoryAt(path: String): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    /** Return the attributes of an open file or directory.
     *
     *  Note: This is similar to `fstat` in POSIX, except that it does not return
     *  device and inode information. For testing whether two descriptors refer to
     *  the same underlying filesystem object, use `is-same-object`. To obtain
     *  additional data that can be used do determine whether a file has been
     *  modified, use `metadata-hash`.
     *
     *  Note: This was called `fd_filestat_get` in earlier versions of WASI.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("stat")
    def stat(): scala.scalajs.wit.Result[DescriptorStat, ErrorCode] = scala.scalajs.wit.native
    /** Return the attributes of a file or directory.
     *
     *  Note: This is similar to `fstatat` in POSIX, except that it does not
     *  return device and inode information. See the `stat` description for a
     *  discussion of alternatives.
     *
     *  Note: This was called `path_filestat_get` in earlier versions of WASI.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("stat-at")
    def statAt(pathFlags: PathFlags, path: String): scala.scalajs.wit.Result[DescriptorStat, ErrorCode] = scala.scalajs.wit.native
    /** Adjust the timestamps of a file or directory.
     *
     *  Note: This is similar to `utimensat` in POSIX.
     *
     *  Note: This was called `path_filestat_set_times` in earlier versions of
     *  WASI.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("set-times-at")
    def setTimesAt(pathFlags: PathFlags, path: String, dataAccessTimestamp: NewTimestamp, dataModificationTimestamp: NewTimestamp): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    /** Create a hard link.
     *
     *  Note: This is similar to `linkat` in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("link-at")
    def linkAt(oldPathFlags: PathFlags, oldPath: String, newDescriptor: Descriptor, newPath: String): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    /** Open a file or directory.
     *
     *  The returned descriptor is not guaranteed to be the lowest-numbered
     *  descriptor not currently open/ it is randomized to prevent applications
     *  from depending on making assumptions about indexes, since this is
     *  error-prone in multi-threaded contexts. The returned descriptor is
     *  guaranteed to be less than 2**31.
     *
     *  If `flags` contains `descriptor-flags::mutate-directory`, and the base
     *  descriptor doesn't have `descriptor-flags::mutate-directory` set,
     *  `open-at` fails with `error-code::read-only`.
     *
     *  If `flags` contains `write` or `mutate-directory`, or `open-flags`
     *  contains `truncate` or `create`, and the base descriptor doesn't have
     *  `descriptor-flags::mutate-directory` set, `open-at` fails with
     *  `error-code::read-only`.
     *
     *  Note: This is similar to `openat` in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("open-at")
    def openAt(pathFlags: PathFlags, path: String, openFlags: OpenFlags, flags: DescriptorFlags): scala.scalajs.wit.Result[Descriptor, ErrorCode] = scala.scalajs.wit.native
    /** Read the contents of a symbolic link.
     *
     *  If the contents contain an absolute or rooted path in the underlying
     *  filesystem, this function fails with `error-code::not-permitted`.
     *
     *  Note: This is similar to `readlinkat` in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("readlink-at")
    def readlinkAt(path: String): scala.scalajs.wit.Result[String, ErrorCode] = scala.scalajs.wit.native
    /** Remove a directory.
     *
     *  Return `error-code::not-empty` if the directory is not empty.
     *
     *  Note: This is similar to `unlinkat(fd, path, AT_REMOVEDIR)` in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("remove-directory-at")
    def removeDirectoryAt(path: String): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    /** Rename a filesystem object.
     *
     *  Note: This is similar to `renameat` in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("rename-at")
    def renameAt(oldPath: String, newDescriptor: Descriptor, newPath: String): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    /** Create a symbolic link (also known as a "symlink").
     *
     *  If `old-path` starts with `/`, the function fails with
     *  `error-code::not-permitted`.
     *
     *  Note: This is similar to `symlinkat` in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("symlink-at")
    def symlinkAt(oldPath: String, newPath: String): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    /** Unlink a filesystem object that is not a directory.
     *
     *  Return `error-code::is-directory` if the path refers to a directory.
     *  Note: This is similar to `unlinkat(fd, path, 0)` in POSIX.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("unlink-file-at")
    def unlinkFileAt(path: String): scala.scalajs.wit.Result[Unit, ErrorCode] = scala.scalajs.wit.native
    /** Test whether two descriptors refer to the same filesystem object.
     *
     *  In POSIX, this corresponds to testing whether the two descriptors have the
     *  same device (`st_dev`) and inode (`st_ino` or `d_ino`) numbers.
     *  wasi-filesystem does not expose device and inode numbers, so this function
     *  may be used instead.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("is-same-object")
    def isSameObject(other: Descriptor): Boolean = scala.scalajs.wit.native
    /** Return a hash of the metadata associated with a filesystem object referred
     *  to by a descriptor.
     *
     *  This returns a hash of the last-modification timestamp and file size, and
     *  may also include the inode number, device number, birth timestamp, and
     *  other metadata fields that may change when the file is modified or
     *  replaced. It may also include a secret value chosen by the
     *  implementation and not otherwise exposed.
     *
     *  Implementations are encourated to provide the following properties:
     *
     *  - If the file is not modified or replaced, the computed hash value should
     *  usually not change.
     *  - If the object is modified or replaced, the computed hash value should
     *  usually change.
     *  - The inputs to the hash should not be easily computable from the
     *  computed hash.
     *
     *  However, none of these is required.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("metadata-hash")
    def metadataHash(): scala.scalajs.wit.Result[MetadataHashValue, ErrorCode] = scala.scalajs.wit.native
    /** Return a hash of the metadata associated with a filesystem object referred
     *  to by a directory descriptor and a relative path.
     *
     *  This performs the same hash computation as `metadata-hash`.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("metadata-hash-at")
    def metadataHashAt(pathFlags: PathFlags, path: String): scala.scalajs.wit.Result[MetadataHashValue, ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceDrop
    def close(): Unit = scala.scalajs.wit.native
  }
  object Descriptor {
  }

  /** A stream of directory entries.
   */
  @scala.scalajs.wit.annotation.WitResourceImport("wasi:filesystem/types@0.2.0", "directory-entry-stream")
  trait DirectoryEntryStream {
    /** Read a single directory entry from a `directory-entry-stream`.
     */
    @scala.scalajs.wit.annotation.WitResourceMethod("read-directory-entry")
    def readDirectoryEntry(): scala.scalajs.wit.Result[java.util.Optional[DirectoryEntry], ErrorCode] = scala.scalajs.wit.native
    @scala.scalajs.wit.annotation.WitResourceDrop
    def close(): Unit = scala.scalajs.wit.native
  }
  object DirectoryEntryStream {
  }

  // Functions
  /** Attempts to extract a filesystem-related `error-code` from the stream
   *  `error` provided.
   *
   *  Stream operations which return `stream-error::last-operation-failed`
   *  have a payload with more information about the operation that failed.
   *  This payload can be passed through to this function to see if there's
   *  filesystem-related information about the error to return.
   *
   *  Note that this function is fallible because not all stream-related
   *  errors are filesystem-related errors.
   */
  @scala.scalajs.wit.annotation.WitImport("wasi:filesystem/types@0.2.0", "filesystem-error-code")
  def filesystemErrorCode(err: Error): java.util.Optional[ErrorCode] = scala.scalajs.wit.native

}
