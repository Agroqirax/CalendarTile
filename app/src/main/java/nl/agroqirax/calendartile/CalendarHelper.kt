package nl.agroqirax.calendartile

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import java.util.Calendar

data class NextEvent(
    val eventId: Long,
    val title: String,
    val timeLabel: String,
    val beginTimeMillis: Long,
    val endTimeMillis: Long
)

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int
)

object CalendarHelper {

    private const val LOOKAHEAD_MILLIS = 1000L * 60 * 60 * 24 * 14 // 14 days

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
            CalendarContract.Instances.CALENDAR_ID
        )

        val cursor = context.contentResolver.query(
            uri,
            projection,
            "${CalendarContract.Instances.END} >= ?",
            arrayOf(now.toString()),
            "${CalendarContract.Instances.BEGIN} ASC"
        ) ?: return null

        cursor.use {
            while (it.moveToNext()) {
                val calendarId = it.getLong(5)
                if (calendarId in ignoredCalendarIds) continue

                val title = it.getString(0)?.takeIf { t -> t.isNotBlank() } ?: context.getString(R.string.event_no_title)
                val begin = it.getLong(1)
                val endTime = it.getLong(2)
                val allDay = it.getInt(3) == 1
                val eventId = it.getLong(4)

                val timeLabel = if (allDay) {
                    val cal = Calendar.getInstance().apply { timeInMillis = begin }
                    val today = Calendar.getInstance()
                    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

                    when {
                        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) ->
                            context.getString(R.string.day_today)

                        cal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR) ->
                            context.getString(R.string.day_tomorrow)

                        else ->
                            DateFormat.getMediumDateFormat(context).format(cal.time)
                    }
                } else {
                    val cal = Calendar.getInstance().apply { timeInMillis = begin }
                    val endCal = Calendar.getInstance().apply { timeInMillis = endTime }
                    val today = Calendar.getInstance()
                    val isToday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)

                    val startStr = DateFormat.getTimeFormat(context).format(cal.time)
                    val endStr = DateFormat.getTimeFormat(context).format(endCal.time)

                    if (isToday) {
                        "$startStr–$endStr"
                    } else {
                        val dateStr = DateFormat.getMediumDateFormat(context).format(cal.time)
                        "$dateStr $startStr–$endStr"
                    }
                }

                return NextEvent(eventId, title, timeLabel, begin, endTime)
            }
        }
        return null
    }
}
