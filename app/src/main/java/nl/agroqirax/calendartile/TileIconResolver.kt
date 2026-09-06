package nl.agroqirax.calendartile

import androidx.annotation.DrawableRes
import java.util.Calendar
import java.util.TimeZone

/**
 * What the tile icon should show, before anything is drawn.
 *
 * Deliberately neither an [android.graphics.drawable.Icon] nor a Compose painter:
 * the tile needs the former and the settings preview needs the latter, so only the
 * *decision* is shared and each side does its own rendering.
 */
sealed interface TileIconSpec {
    /** A drawable, ready to resolve. */
    data class Glyph(@get:DrawableRes val resId: Int) : TileIconSpec

    /** A day of the month, to be drawn into the calendar frame. */
    data class Day(val dayOfMonth: Int) : TileIconSpec

    /**
     * A weekday, to be drawn (localized, abbreviated) into the calendar frame.
     * [Calendar.DAY_OF_WEEK] convention: [Calendar.SUNDAY] (1) through [Calendar.SATURDAY] (7).
     */
    data class Weekday(val dayOfWeek: Int) : TileIconSpec
}

/**
 * The single source of truth for which icon an event gets — both the tile and the
 * settings preview go through here, so they cannot drift apart.
 */
object TileIconResolver {

    fun resolve(
        style: TileIconStyle,
        event: NextEvent?,
        customRules: List<CustomIconRule>,
        nowMillis: Long = System.currentTimeMillis()
    ): TileIconSpec = when (style) {
        TileIconStyle.PLAIN ->
            TileIconSpec.Glyph(R.drawable.ic_today)

        TileIconStyle.TODAY ->
            TileIconSpec.Day(dayOfMonth(nowMillis))

        TileIconStyle.EVENT_DATE ->
            TileIconSpec.Day(event?.startDayOfMonth ?: dayOfMonth(nowMillis))

        TileIconStyle.NEXT_EVENT_WEEKDAY ->
            TileIconSpec.Weekday(dayOfWeek(event, nowMillis))

        TileIconStyle.SMART -> {
            val iconRes = event
                ?.let { EventIconMapper.iconNameFor(it.title, it.location, customRules) }
                ?.let { TileIcons.resIdFor(it) }
            if (iconRes != null) {
                TileIconSpec.Glyph(iconRes)
            } else {
                // Most events match no keyword, so this is the usual path, not an
                // error case — degrade to the event's date rather than something
                // that looks broken.
                resolve(TileIconStyle.EVENT_DATE, event, customRules, nowMillis)
            }
        }
    }

    private fun dayOfMonth(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)

    /**
     * The event's weekday, read in its own time zone like [NextEvent.startDayOfMonth]
     * does — an all-day event's UTC midnight must not shift a day under a
     * behind-UTC device zone. Falls back to today's weekday when there is no event.
     */
    private fun dayOfWeek(event: NextEvent?, nowMillis: Long): Int {
        val zone = if (event != null) eventTimeZone(event.allDay) else TimeZone.getDefault()
        val millis = event?.beginTimeMillis ?: nowMillis
        return Calendar.getInstance(zone).apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)
    }
}
