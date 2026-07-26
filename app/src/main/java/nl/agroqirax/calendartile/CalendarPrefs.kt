package nl.agroqirax.calendartile

import android.content.Context

/**
 * Stores the set of calendar IDs the user has toggled OFF (ignored).
 * Default: empty set, meaning all calendars are shown.
 */
object CalendarPrefs {

    private const val PREFS_NAME = "calendar_tile_prefs"
    private const val KEY_IGNORED_IDS = "ignored_calendar_ids"

    fun getIgnoredCalendarIds(context: Context): Set<Long> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_IGNORED_IDS, emptySet()) ?: emptySet()
        return stored.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun setCalendarIgnored(context: Context, calendarId: Long, ignored: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getIgnoredCalendarIds(context).toMutableSet()
        if (ignored) {
            current.add(calendarId)
        } else {
            current.remove(calendarId)
        }
        prefs.edit()
            .putStringSet(KEY_IGNORED_IDS, current.map { it.toString() }.toSet())
            .apply()
    }
}
