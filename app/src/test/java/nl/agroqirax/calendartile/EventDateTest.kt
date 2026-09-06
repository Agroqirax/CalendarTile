package nl.agroqirax.calendartile

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * All-day events are stored as midnight UTC standing in for a nominal calendar
 * date, so every one of these cases used to be off by a day in any zone west of
 * UTC. The New York cases are the regression; the Amsterdam ones prove the fix
 * did not break the zones that always happened to work.
 */
class EventDateTest {

    private lateinit var originalZone: TimeZone

    @Before
    fun captureZone() {
        originalZone = TimeZone.getDefault()
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(originalZone)
    }

    private fun inZone(id: String) = TimeZone.setDefault(TimeZone.getTimeZone(id))

    /** Midnight UTC on a date — how the provider stores an all-day event. */
    private fun utcDate(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day)
        }.timeInMillis

    /** A wall-clock moment in whatever zone is currently default. */
    private fun localTime(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, 0)
        }.timeInMillis

    private fun allDayEvent(beginMillis: Long) = NextEvent(
        eventId = 1L,
        title = "Conference",
        timeLabel = "",
        beginTimeMillis = beginMillis,
        endTimeMillis = beginMillis + 86_400_000L,
        location = null,
        allDay = true
    )

    @Test
    fun `all-day event today reads as today west of UTC`() {
        inZone("America/New_York")
        val bucket = dayBucketFor(
            beginMillis = utcDate(2026, Calendar.AUGUST, 25),
            allDay = true,
            nowMillis = localTime(2026, Calendar.AUGUST, 25, 12)
        )
        assertEquals(DayBucket.TODAY, bucket)
    }

    @Test
    fun `all-day event tomorrow reads as tomorrow west of UTC`() {
        inZone("America/New_York")
        val bucket = dayBucketFor(
            beginMillis = utcDate(2026, Calendar.AUGUST, 26),
            allDay = true,
            nowMillis = localTime(2026, Calendar.AUGUST, 25, 12)
        )
        assertEquals(DayBucket.TOMORROW, bucket)
    }

    @Test
    fun `all-day event still reads correctly east of UTC`() {
        inZone("Europe/Amsterdam")
        val now = localTime(2026, Calendar.AUGUST, 25, 12)
        assertEquals(
            DayBucket.TODAY,
            dayBucketFor(utcDate(2026, Calendar.AUGUST, 25), allDay = true, nowMillis = now)
        )
        assertEquals(
            DayBucket.TOMORROW,
            dayBucketFor(utcDate(2026, Calendar.AUGUST, 26), allDay = true, nowMillis = now)
        )
    }

    @Test
    fun `all-day event further out is neither today nor tomorrow`() {
        inZone("America/New_York")
        val bucket = dayBucketFor(
            beginMillis = utcDate(2026, Calendar.AUGUST, 29),
            allDay = true,
            nowMillis = localTime(2026, Calendar.AUGUST, 25, 12)
        )
        assertEquals(DayBucket.OTHER, bucket)
    }

    @Test
    fun `timed events are read in the local zone`() {
        inZone("America/New_York")
        // 11pm local today is still today, even though it is already tomorrow in UTC.
        val lateTonight = localTime(2026, Calendar.AUGUST, 25, 23)
        val bucket = dayBucketFor(
            beginMillis = lateTonight,
            allDay = false,
            nowMillis = localTime(2026, Calendar.AUGUST, 25, 9)
        )
        assertEquals(DayBucket.TODAY, bucket)
    }

    @Test
    fun `all-day start day of month is the nominal date, not the local one`() {
        inZone("America/New_York")
        val event = allDayEvent(utcDate(2026, Calendar.AUGUST, 25))
        assertEquals(25, event.startDayOfMonth)
    }

    @Test
    fun `all-day start day of month survives a month boundary`() {
        inZone("America/New_York")
        // Reading this locally would give 31 August instead of 1 September.
        val event = allDayEvent(utcDate(2026, Calendar.SEPTEMBER, 1))
        assertEquals(1, event.startDayOfMonth)
    }

    @Test
    fun `timed start day of month uses the local zone`() {
        inZone("America/New_York")
        val event = allDayEvent(localTime(2026, Calendar.AUGUST, 25, 23))
            .copy(allDay = false)
        assertEquals(25, event.startDayOfMonth)
    }
}
