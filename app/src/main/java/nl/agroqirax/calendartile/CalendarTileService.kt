package nl.agroqirax.calendartile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class CalendarTileService : TileService() {

    // Remember the event we're currently showing, so onClick() can open it directly.
    private var currentEvent: NextEvent? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (!CalendarHelper.hasPermission(this)) {
            val intent = Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchAndCollapse(intent)
            return
        }

        val event = currentEvent

        val intent = if (event != null) {
            // Open this specific event. Etar/AOSP calendar match ACTION_VIEW
            // on the event MIME type, not just the URI, so set it explicitly.
            val eventUri = Uri.withAppendedPath(
                CalendarContract.Events.CONTENT_URI,
                event.eventId.toString()
            )
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(eventUri, "vnd.android.cursor.item/event")
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginTimeMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endTimeMillis)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            // No event to show — just open the calendar app at "now"
            val timeUri = CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time")
                .appendPath(System.currentTimeMillis().toString())
                .build()
            Intent(Intent.ACTION_VIEW)
                .setData(timeUri)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        launchAndCollapse(intent)
    }

    /**
     * Launches an activity intent and collapses the Quick Settings panel,
     * using the appropriate API for the running Android version.
     * - API 34+: startActivityAndCollapse(Intent) throws, so wrap in a
     *   PendingIntent and use that overload instead.
     * - API < 34: startActivityAndCollapse(Intent) works directly.
     */
    private fun launchAndCollapse(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = android.app.PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                @Suppress("DEPRECATION")
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.toast_no_calendar_app), Toast.LENGTH_SHORT).show()
        } catch (e: Resources.NotFoundException) {
            Toast.makeText(this, getString(R.string.toast_no_calendar_app), Toast.LENGTH_SHORT).show()
        }
    }

    fun updateTile() {
        val tile = qsTile ?: return

        tile.icon = Icon.createWithResource(this, R.drawable.ic_calendar)

        if (!CalendarHelper.hasPermission(this)) {
            currentEvent = null
            tile.label = getString(R.string.app_name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.tile_tap_to_grant_permission)
            }
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
            return
        }

        val ignored = CalendarPrefs.getIgnoredCalendarIds(this)
        val nextEvent = CalendarHelper.getNextEvent(this, ignored)
        currentEvent = nextEvent

        if (nextEvent == null) {
            tile.label = getString(R.string.tile_no_upcoming_events)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = null
            }
            tile.state = Tile.STATE_INACTIVE
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.label = nextEvent.title
                tile.subtitle = nextEvent.timeLabel
            } else {
                tile.label = "${nextEvent.title} · ${nextEvent.timeLabel}"
            }
            tile.state = Tile.STATE_ACTIVE
        }

        tile.updateTile()
    }
}
