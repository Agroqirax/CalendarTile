package nl.agroqirax.calendartile

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

data class NextEvent(
    val eventId: Long,
    val title: String,
    val timeLabel: String,
    val beginTimeMillis: Long,
    val endTimeMillis: Long,
    val location: String?,
    val allDay: Boolean
) {
    val startDayOfMonth: Int
        get() = Calendar.getInstance(eventTimeZone(allDay))
            .apply { timeInMillis = beginTimeMillis }
            .get(Calendar.DAY_OF_MONTH)
}


// An all-day event is stored as midnight UTC as a stand-in for a nominal calendar date. 
internal fun eventTimeZone(allDay: Boolean): TimeZone =
    if (allDay) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()

internal enum class DayBucket { TODAY, TOMORROW, OTHER }

// The event's date is read in [eventTimeZone], while today and tomorrow are read locally.
internal fun dayBucketFor(beginMillis: Long, allDay: Boolean, nowMillis: Long): DayBucket {
    val event = Calendar.getInstance(eventTimeZone(allDay))
        .apply { timeInMillis = beginMillis }
    val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val tomorrow = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, 1)
    }

    return when {
        isSameDay(event, today) -> DayBucket.TODAY
        isSameDay(event, tomorrow) -> DayBucket.TOMORROW
        else -> DayBucket.OTHER
    }
}

private fun isMultiDay(candidate: CandidateEvent): Boolean {
    if (candidate.allDay) return false
    val beginCal = Calendar.getInstance().apply { timeInMillis = candidate.begin }
    val endCal = Calendar.getInstance().apply { timeInMillis = candidate.end }
    return !isSameDay(beginCal, endCal)
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int
)

private data class CandidateEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?
)

object CalendarHelper {

    private const val LOOKAHEAD_MILLIS = 1000L * 60 * 60 * 24 * 7 // 7 days

    fun hasPermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Returns all calendars available on the device, across all accounts. */
    fun getCalendars(context: Context): List<CalendarInfo> {
        if (!hasPermission(context)) return emptyList()

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR
        )

        val result = mutableListOf<CalendarInfo>()
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
        ) ?: return result

        cursor.use {
            while (it.moveToNext()) {
                result.add(
                    CalendarInfo(
                        id = it.getLong(0),
                        displayName = it.getString(1) ?: context.getString(R.string.calendar_unnamed),
                        accountName = it.getString(2) ?: "",
                        color = it.getInt(3)
                    )
                )
            }
        }
        return result
    }

    /**
     * Returns the next upcoming calendar event within the lookahead window,
     * excluding any calendar IDs in [ignoredCalendarIds], or null if there is none.
     */
    fun getNextEvent(context: Context, ignoredCalendarIds: Set<Long>): NextEvent? {
        if (!hasPermission(context)) return null

        val now = System.currentTimeMillis()
        val end = now + LOOKAHEAD_MILLIS

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(end.toString())
            .build()

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.EVENT_LOCATION
        )

        val cursor = context.contentResolver.query(
            uri,
            projection,
            "${CalendarContract.Instances.END} >= ?",
            arrayOf(now.toString()),
            null
        ) ?: return null

        val candidates = mutableListOf<CandidateEvent>()

        cursor.use {
            while (it.moveToNext()) {
                val calendarId = it.getLong(5)
                if (calendarId in ignoredCalendarIds) continue

                val selfAttendeeStatus = it.getInt(6)
                if (selfAttendeeStatus == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue

                val eventStatus = it.getInt(7)
                if (eventStatus == CalendarContract.Events.STATUS_CANCELED) continue

                val title = it.getString(0)?.takeIf { t -> t.isNotBlank() } ?: context.getString(R.string.event_no_title)
                val begin = it.getLong(1)
                val endTime = it.getLong(2)
                val allDay = it.getInt(3) == 1
                val eventId = it.getLong(4)
                val location = it.getString(8)

                candidates.add(CandidateEvent(eventId, title, begin, endTime, allDay, location))
            }
        }

        val winner = candidates.minWithOrNull(
            compareBy(
                // A timed event spanning multiple calendar days behaves like an all-day
                // event for ranking purposes: it shouldn't outrank a same-day event just
                // because it's already in progress.
                { candidate -> if (candidate.allDay || isMultiDay(candidate)) 1 else 0 },
                { candidate -> if (candidate.begin <= now && candidate.end > now) 0 else 1 },
                { candidate -> candidate.begin },
                { candidate -> candidate.end - candidate.begin },
                { candidate -> candidate.title }
            )
        ) ?: return null

        val title = winner.title
        val begin = winner.begin
        val endTime = winner.end
        val allDay = winner.allDay
        val eventId = winner.eventId

        val timeLabel = if (allDay) {
            when (dayBucketFor(begin, allDay = true, nowMillis = now)) {
                DayBucket.TODAY -> context.getString(R.string.day_today)
                DayBucket.TOMORROW -> context.getString(R.string.day_tomorrow)
                // The formatter has its own zone, and it defaults to the local one,
                // so it has to be pointed at UTC too — not just the field reads.
                DayBucket.OTHER -> DateFormat.getMediumDateFormat(context)
                    .apply { timeZone = eventTimeZone(allDay = true) }
                    .format(Date(begin))
            }
        } else {
            val cal = Calendar.getInstance().apply { timeInMillis = begin }
            val endCal = Calendar.getInstance().apply { timeInMillis = endTime }
            val isToday =
                dayBucketFor(begin, allDay = false, nowMillis = now) == DayBucket.TODAY

            val startStr = DateFormat.getTimeFormat(context).format(cal.time)
            val endStr = DateFormat.getTimeFormat(context).format(endCal.time)

            if (isToday) {
                val time = "$startStr–$endStr"
                // Only a same-day event's label has room to spare for it — any
                // other day already spends that space on the date.
                val location = winner.location?.takeIf { it.isNotBlank() }
                if (location != null) "$time \u2022 $location" else time
            } else {
                val dateStr = DateFormat.getMediumDateFormat(context).format(cal.time)
                "$dateStr $startStr–$endStr"
            }
        }

        return NextEvent(eventId, title, timeLabel, begin, endTime, winner.location, allDay)
    }
}
