package nl.agroqirax.calendartile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import java.util.Calendar
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws a calendar frame with text inside it, for the [TileIconStyle.TODAY],
 * [TileIconStyle.EVENT_DATE], and [TileIconStyle.NEXT_EVENT_WEEKDAY] tile icons.
 */
object TileIconRenderer {

    /**
     * The frame's inner box in `ic_calendar_today`'s 960x960 viewport: the area
     * below the header bar, which is where the number goes.
     */
    private const val VIEWPORT = 960f
    private const val INTERIOR_LEFT = 200f
    private const val INTERIOR_TOP = 400f
    private const val INTERIOR_RIGHT = 760f
    private const val INTERIOR_BOTTOM = 800f

    /** How much of the inner box the digits may occupy, leaving a little breathing room. */
    private const val FILL_FRACTION = 0.82f

    /** Arbitrary size to measure at; the result is scaled to fit, so the value only affects precision. */
    private const val MEASURE_TEXT_SIZE = 100f

    private data class CacheKey(val size: Int, val dayOfMonth: Int)

    /**
     * Keyed by size and day rather than a single slot: the settings dialog can render
     * TODAY and EVENT_DATE previews at once, and a single-entry cache would just have
     * them evict each other on every recomposition. Bounded implicitly — at most 31
     * days per icon size actually occur.
     */
    private val cache = HashMap<CacheKey, Bitmap>()

    private data class WeekdayCacheKey(val size: Int, val dayOfWeek: Int)

    /** Same reasoning as [cache], for the weekday variant. */
    private val weekdayCache = HashMap<WeekdayCacheKey, Bitmap>()

    /**
     * Renders [dayOfMonth] inside a calendar frame.
     *
     * Cached across calls: the tile re-renders on every pull of the shade, and
     * the day almost never changes between two of them.
     */
    @Synchronized
    fun renderDayBitmap(context: Context, dayOfMonth: Int): Bitmap {
        val size = iconSizePx(context)
        val key = CacheKey(size, dayOfMonth)
        cache[key]?.let { return it }

        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)

        drawFrame(context, canvas, size)
        drawCentered(canvas, size, dayOfMonth.toString())

        cache[key] = bitmap
        return bitmap
    }

    fun renderDayIcon(context: Context, dayOfMonth: Int): Icon =
        Icon.createWithBitmap(renderDayBitmap(context, dayOfMonth))

    /**
     * Renders [dayOfWeek]'s localized abbreviation (from [R.array.weekday_abbrev])
     * inside a calendar frame. [dayOfWeek] follows [Calendar.DAY_OF_WEEK] convention:
     * [Calendar.SUNDAY] (1) through [Calendar.SATURDAY] (7). Cached for the same
     * reason as [renderDayBitmap].
     */
    @Synchronized
    fun renderWeekdayBitmap(context: Context, dayOfWeek: Int): Bitmap {
        val size = iconSizePx(context)
        val key = WeekdayCacheKey(size, dayOfWeek)
        weekdayCache[key]?.let { return it }

        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)

        drawFrame(context, canvas, size)
        drawCentered(canvas, size, weekdayAbbrev(context, dayOfWeek))

        weekdayCache[key] = bitmap
        return bitmap
    }

    fun renderWeekdayIcon(context: Context, dayOfWeek: Int): Icon =
        Icon.createWithBitmap(renderWeekdayBitmap(context, dayOfWeek))

    /**
     * [R.array.weekday_abbrev] is ordered Monday-first (index 0) through Sunday
     * (index 6); [Calendar.DAY_OF_WEEK] is Sunday-first (1) through Saturday (7).
     */
    private fun weekdayAbbrev(context: Context, dayOfWeek: Int): String {
        val mondayFirstIndex = (dayOfWeek + 5) % 7
        return context.resources.getStringArray(R.array.weekday_abbrev)[mondayFirstIndex]
    }

    /**
     * Twice the nominal 24dp the tile draws its icon at, so the system's downscale
     * has pixels to work with. Floored for the benefit of very low-density displays.
     */
    private fun iconSizePx(context: Context): Int {
        val density = context.resources.displayMetrics.density
        return maxOf(96, (24f * density * 2f).roundToInt())
    }

    private fun drawFrame(context: Context, canvas: Canvas, size: Int) {
        // mutate() so tinting this instance does not tint the shared constant
        // state, which the settings screen also draws from.
        val frame = ContextCompat.getDrawable(context, R.drawable.ic_calendar_today)
            ?.mutate() ?: return
        frame.setBounds(0, 0, size, size)
        frame.setTint(Color.WHITE)
        frame.draw(canvas)
    }

    private fun drawCentered(canvas: Canvas, size: Int, text: String) {
        val scale = size / VIEWPORT

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            // Condensed keeps two characters comfortable in a box this narrow.
            // Typeface.create falls back to the default family if it is missing.
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = MEASURE_TEXT_SIZE
        }

        val maxWidth = (INTERIOR_RIGHT - INTERIOR_LEFT) * scale * FILL_FRACTION
        val maxHeight = (INTERIOR_BOTTOM - INTERIOR_TOP) * scale * FILL_FRACTION

        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val fitted = min(maxWidth / bounds.width(), maxHeight / bounds.height())
        paint.textSize = MEASURE_TEXT_SIZE * fitted

        // Centre the glyphs' ink box rather than the font's line box. Digits have
        // no descenders and rarely reach the full ascent, so font-metric centring
        // leaves them visibly high in the frame.
        paint.getTextBounds(text, 0, text.length, bounds)
        val centreX = (INTERIOR_LEFT + INTERIOR_RIGHT) / 2f * scale
        val centreY = (INTERIOR_TOP + INTERIOR_BOTTOM) / 2f * scale

        canvas.drawText(text, centreX, centreY - bounds.exactCenterY(), paint)
    }
}
