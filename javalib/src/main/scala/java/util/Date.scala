/*
 * Scala.js (https://www.scala-js.org/)
 *
 * Copyright EPFL.
 *
 * Licensed under Apache License 2.0
 * (https://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package java.util

import java.lang.Cloneable
import java.time.Instant
import java.util.function._

import scalajs.js

class Date(private var millis: Long)
    extends Object with Serializable with Cloneable with Comparable[Date] {

  import Date._

  def this() = this(System.currentTimeMillis())

  @Deprecated
  def this(year: Int, month: Int, date: Int, hrs: Int, min: Int, sec: Int) =
    this(Date.UTC(year, month, date, hrs, min, sec))

  @Deprecated
  def this(year: Int, month: Int, date: Int, hrs: Int, min: Int) =
    this(year, month, date, hrs, min, 0)

  @Deprecated
  def this(year: Int, month: Int, date: Int) =
    this(year, month, date, 0, 0, 0)

  @Deprecated
  def this(date: String) = this(Date.parse(date))

  def after(when: Date): Boolean = millis > when.millis

  def before(when: Date): Boolean = millis < when.millis

  override def clone(): Object = new Date(millis)

  override def compareTo(anotherDate: Date): Int =
    java.lang.Long.compare(millis, anotherDate.millis)

  override def equals(obj: Any): Boolean = obj match {
    case d: Date => d.millis == millis
    case _       => false
  }

  override def hashCode(): Int = millis.hashCode()

  private def asDate(): js.Date = {
    if (!isSafeJSDate()) {
      throw new IllegalArgumentException(
          s"cannot convert this java.util.Date ($millis millis) to a js.Date")
    }
    new js.Date(millis.toDouble)
  }

  @inline
  private def mutDate(mutator: Consumer[js.Date]): Unit = {
    val date = asDate()
    mutator.accept(date)
    millis = safeGetTime(date)
  }

  @Deprecated
  def getDate(): Int = asDate().getDate().toInt

  @Deprecated
  def getDay(): Int = asDate().getDay().toInt

  @Deprecated
  def getHours(): Int = asDate().getHours().toInt

  @Deprecated
  def getMinutes(): Int = asDate().getMinutes().toInt

  @Deprecated
  def getMonth(): Int = asDate().getMonth().toInt

  @Deprecated
  def getSeconds(): Int = asDate().getSeconds().toInt

  def getTime(): Long = millis

  @Deprecated
  def getTimezoneOffset(): Int = new js.Date().getTimezoneOffset().toInt

  @Deprecated
  def getYear(): Int = asDate().getFullYear().toInt - 1900

  @Deprecated
  def setDate(date: Int): Unit = mutDate(_.setDate(date))

  @Deprecated
  def setHours(hours: Int): Unit = mutDate(_.setHours(hours))

  @Deprecated
  def setMinutes(minutes: Int): Unit = mutDate(_.setMinutes(minutes))

  @Deprecated
  def setMonth(month: Int): Unit = mutDate(_.setMonth(month))

  @Deprecated
  def setSeconds(seconds: Int): Unit = mutDate(_.setSeconds(seconds))

  def setTime(time: Long): Unit = millis = time

  @Deprecated
  def setYear(year: Int): Unit = mutDate(_.setFullYear(1900 + year))

  @Deprecated
  def toGMTString(): String = {
    val date = asDate()
    "" + date.getUTCDate().toInt + " " + Months(date.getUTCMonth().toInt) + " " +
      date.getUTCFullYear().toInt + " " + pad0(date.getUTCHours().toInt) + ":" +
      pad0(date.getUTCMinutes().toInt) + ":" +
      pad0(date.getUTCSeconds().toInt) + " GMT"
  }

  def toInstant(): Instant = Instant.ofEpochMilli(getTime())

  @Deprecated
  def toLocaleString(): String = {
    val date = asDate()
    "" + date.getDate().toInt + "-" + Months(date.getMonth().toInt) + "-" +
      date.getFullYear().toInt + "-" + pad0(date.getHours().toInt) + ":" +
      pad0(date.getMinutes().toInt) + ":" + pad0(date.getSeconds().toInt)
  }

  override def toString(): String = {
    if (isSafeJSDate()) {
      val date = asDate()
      val offset = -date.getTimezoneOffset().toInt
      val sign = if (offset < 0) "-" else "+"
      val hours = pad0(Math.abs(offset) / 60)
      val mins = pad0(Math.abs(offset) % 60)
      Days(date.getDay().toInt) + " " + Months(date.getMonth().toInt) + " " +
        pad0(date.getDate().toInt) + " " + pad0(date.getHours().toInt) + ":" +
        pad0(date.getMinutes().toInt) + ":" + pad0(date.getSeconds().toInt) +
        " GMT" + " " + date.getFullYear().toInt
    } else {
      s"java.util.Date($millis)"
    }
  }

  @inline
  private def isSafeJSDate(): Boolean =
    -MaxMillis <= millis && millis <= MaxMillis
}

object Date {
  /* Maximum amount of milliseconds supported in a js.Date.
   * See https://www.ecma-international.org/ecma-262/5.1/#sec-15.9.1.14
   */
  private final val MaxMillis = 8640000000000000L

  // https://262.ecma-international.org/16.0/#sec-time-related-constants
  private final val HoursPerDay = 24
  private final val MinutesPerHour = 60
  private final val SecondsPerMinute = 60
  private final val msPerSecond = 1000
  private final val msPerMinute = 60000
  private final val msPerHour = 3600000
  private final val msPerDay = 86400000

  private final val MaxYear = 292278994
  private final val MinYear = -MaxYear + 1970 + 1970 - 1

  private val Days = Array(
      "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

  private val Months = Array(
      "Jan", "Feb", "Mar", "Apr", "May", "Jun",
      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

  private def pad0(i: Int): String = {
    val str = "" + i
    if (str.length < 2) "0" + str else str
  }

  def from(instant: Instant): Date = {
    try {
      new Date(instant.toEpochMilli())
    } catch {
      case ex: ArithmeticException =>
        throw new IllegalArgumentException(ex)
    }
  }

  /** https://262.ecma-international.org/16.0/#sec-day */
  @inline
  private def Day(t: Long): Long =
    Math.floorDiv(t, msPerDay)

  /** https://262.ecma-international.org/16.0/#sec-timewithinday */
  @inline
  private def TimeWithinDay(t: Long): Int =
    Math.floorMod(t, msPerDay).toInt

  /** https://262.ecma-international.org/16.0/#sec-daysinyear */
  @inline
  private def DaysInYear(y: Int): Int =
    365 + InLeapYear(y)

  /** https://262.ecma-international.org/16.0/#sec-dayfromyear */
  private def DayFromYear(y: Int): Long = {
    /* NOTE: In the following steps, numYears1, numYears4, numYears100, and
     * numYears400 represent the number of years divisible by 1, 4, 100, and
     * 400, respectively, that occur between the epoch and the start of year y.
     * The number is negative if y is before the epoch.
     */
    val numYears1 = y - 1970
    val numYears4 = Math.floorDiv(y - 1969, 4)
    val numYears100 = Math.floorDiv(y - 1901, 100)
    val numYears400 = Math.floorDiv(y - 1601, 400)
    365L * numYears1.toLong + (numYears4 - numYears100 + numYears400).toLong
  }

  /** https://262.ecma-international.org/16.0/#sec-timefromyear */
  @inline
  private def TimeFromYear(y: Int): Long =
    msPerDay * DayFromYear(y)

  /** https://262.ecma-international.org/16.0/#sec-yearfromtime */
  private def YearFromTime(t: Long): Int = {
    // Return the largest integral Number y (closest to +∞) such that TimeFromYear(y) ≤ t.
    // TODO Do something smarter
    var min = MinYear
    var max = MaxYear + 1
    while (min + 1 < max) {
      val mid = (min + max) >>> 1
      if (TimeFromYear(mid) <= t)
        min = mid
      else
        max = mid
    }
    min
  }

  /** https://262.ecma-international.org/16.0/#sec-daywithinyear */
  private def DayWithinYear(t: Long): Int =
    (Day(t) - DayFromYear(YearFromTime(t))).toInt

  /** https://262.ecma-international.org/16.0/#sec-inleapyear */
  @inline
  private def InLeapYear(y: Int): Int = {
    val yMod400 = Math.floorMod(y, 400)
    if ((yMod400 & 3) != 0 || yMod400 == 100 || yMod400 == 200 || yMod400 == 300)
      0
    else
      1
  }

  private val MaxDaysForMonths: Array[Int] =
    Array(30, 58, 89, 119, 150, 180, 211, 242, 272, 303, 333)

  /** https://262.ecma-international.org/16.0/#sec-monthfromtime and
   *  https://262.ecma-international.org/16.0/#sec-datefromtime
   *
   *  The result is returned as (month << 8) | dayOfMonth
   */
  private def MonthAndDateFromTime(t: Long): Int = {
    val y = YearFromTime(t)
    val dayWithinYear = (Day(t) - DayFromYear(y)).toInt

    @inline def makeResult(month: Int, dayOfMonth: Int): Int =
      (month << 8) | dayOfMonth

    // First handle January
    if (dayWithinYear < 31) {
      makeResult(0, dayWithinYear)
    } else {
      // All other months are affected by the leap year
      val inLeapYear = InLeapYear(y)
      val p = Arrays.binarySearch(MaxDaysForMonths, dayWithinYear - inLeapYear)
      val month = p ^ (p >> 31) // "bitwise absolute" value

      val dayOfMonth =
        if (month == 1) dayWithinYear - 30
        else dayWithinYear - MaxDaysForMonths(month - 1) - inLeapYear

      makeResult(month, dayOfMonth)
    }
  }

  /** https://262.ecma-international.org/16.0/#sec-weekday */
  @inline
  private def WeekDay(t: Long): Int =
    Math.floorMod(Day(t) + 4, 7)

  /** https://262.ecma-international.org/16.0/#sec-hourfromtime */
  @inline
  private def HourFromTime(t: Long): Int =
    Math.floorMod(Math.floorDiv(t, msPerHour), HoursPerDay)

  /** https://262.ecma-international.org/16.0/#sec-minfromtime */
  @inline
  private def MinFromTime(t: Long): Int =
    Math.floorMod(Math.floorDiv(t, msPerMinute), MinutesPerHour)

  /** https://262.ecma-international.org/16.0/#sec-secfromtime */
  @inline
  private def SecFromTime(t: Long): Int =
    Math.floorMod(Math.floorDiv(t, msPerSecond), SecondsPerMinute)

  /** https://262.ecma-international.org/16.0/#sec-msfromtime */
  @inline
  private def msFromTime(t: Long): Int =
    Math.floorMod(t, msPerSecond)

  /** [[]] */
  private def makeTime(hour: Int, min: Int, sec: Int, ms: Int): Long = {
    0
  }

  @Deprecated
  def UTC(year: Int, month: Int, date: Int,
      hrs: Int, min: Int, sec: Int): Long = {
    js.Date.UTC(year + 1900, month, date, hrs, min, sec).toLong
  }

  @Deprecated
  def parse(string: String): Long = safeGetTime(new js.Date(string))

  private def safeGetTime(date: js.Date): Long = {
    val time = date.getTime()
    if (java.lang.Double.isNaN(time))
      throw new IllegalArgumentException
    time.toLong
  }
}
