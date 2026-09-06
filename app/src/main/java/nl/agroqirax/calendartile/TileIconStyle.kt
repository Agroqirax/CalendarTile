package nl.agroqirax.calendartile

import androidx.annotation.StringRes

/**
 * What the Quick Settings tile draws as its icon.
 *
 * Persisted by [CalendarPrefs] under its [name], so these constant names are
 * part of the stored format — renaming one silently resets that user's choice.
 */
enum class TileIconStyle(@get:StringRes val labelRes: Int) {

    /** The calendar glyph on its own, as the tile looked before this setting existed. */
    PLAIN(R.string.tile_icon_style_plain),

    /** Calendar frame with today's day of the month inside. */
    TODAY(R.string.tile_icon_style_today),

    /**
     * Calendar frame with the next event's day of the month inside. Differs from
     * [TODAY] whenever the next event is not today, which is common — the lookahead
     * window is a week.
     */
    EVENT_DATE(R.string.tile_icon_style_event_date),

    /**
     * A glyph matched to the event itself, e.g. a plane for a flight. Falls back
     * to [EVENT_DATE] when nothing matches, which is the common case.
     */
    SMART(R.string.tile_icon_style_smart);

    companion object {
        val DEFAULT = EVENT_DATE

        /**
         * Tolerates unknown and missing values so a stale or hand-edited preference
         * degrades to [DEFAULT] instead of breaking the tile.
         */
        fun fromKey(key: String?): TileIconStyle =
            entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}
