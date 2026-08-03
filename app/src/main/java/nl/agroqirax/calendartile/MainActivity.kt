package nl.agroqirax.calendartile

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.agroqirax.calendartile.ui.theme.CalendarTileTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGrantedState.value = granted
        if (granted) {
            calendarsState.value = CalendarHelper.getCalendars(this)
            requestAddTile()
        }
        requestTileUpdate()
    }

    private val permissionGrantedState = mutableStateOf(false)
    private val calendarsState = mutableStateOf<List<CalendarInfo>>(emptyList())
    private val requireUnlockState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionGrantedState.value = CalendarHelper.hasPermission(this)
        if (permissionGrantedState.value) {
            calendarsState.value = CalendarHelper.getCalendars(this)
        }
        requireUnlockState.value = CalendarPrefs.isRequireUnlockEnabled(this)

        setContent {
            CalendarTileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CalendarTileApp(
                        permissionGranted = permissionGrantedState.value,
                        calendars = calendarsState.value,
                        ignoredIds = CalendarPrefs.getIgnoredCalendarIds(this),
                        requireUnlock = requireUnlockState.value,
                        onRequestPermission = {
                            requestPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        },
                        onToggleCalendar = { calendarId, enabled ->
                            CalendarPrefs.setCalendarIgnored(this, calendarId, !enabled)
                            requestTileUpdate()
                        },
                        onToggleRequireUnlock = { enabled ->
                            CalendarPrefs.setRequireUnlock(this, enabled)
                            requestTileUpdate()
                        },
                        onAddTileClick = { requestAddTile() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = CalendarHelper.hasPermission(this)
        permissionGrantedState.value = granted
        if (granted) {
            calendarsState.value = CalendarHelper.getCalendars(this)
        }
        requireUnlockState.value = CalendarPrefs.isRequireUnlockEnabled(this)
    }

    private fun requestTileUpdate() {
        TileService.requestListeningState(
            this,
            ComponentName(this, CalendarTileService::class.java)
        )
    }

    private fun requestAddTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val statusBarManager = getSystemService(StatusBarManager::class.java) ?: return
        statusBarManager.requestAddTileService(
            ComponentName(this, CalendarTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_calendar),
            mainExecutor
        ) { /* result ignored; system already no-ops if the tile is already added */ }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTileApp(
    permissionGranted: Boolean,
    calendars: List<CalendarInfo>,
    ignoredIds: Set<Long>,
    requireUnlock: Boolean,
    onRequestPermission: () -> Unit,
    onToggleCalendar: (Long, Boolean) -> Unit,
    onToggleRequireUnlock: (Boolean) -> Unit,
    onAddTileClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        }
    ) { padding ->
        if (!permissionGranted) {
            PermissionRequestScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onRequestPermission = onRequestPermission
            )
        } else {
            CalendarListScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                calendars = calendars,
                ignoredIds = ignoredIds,
                requireUnlock = requireUnlock,
                onToggleCalendar = onToggleCalendar,
                onToggleRequireUnlock = onToggleRequireUnlock,
                onAddTileClick = onAddTileClick
            )
        }
    }
}

@Composable
fun PermissionRequestScreen(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = R.drawable.ic_calendar),
                contentDescription = null,
                modifier = Modifier.height(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.permission_explanation),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.grant_permission))
            }
        }
    }
}

@Composable
fun CalendarListScreen(
    modifier: Modifier = Modifier,
    calendars: List<CalendarInfo>,
    ignoredIds: Set<Long>,
    requireUnlock: Boolean,
    onToggleCalendar: (Long, Boolean) -> Unit,
    onToggleRequireUnlock: (Boolean) -> Unit,
    onAddTileClick: () -> Unit
) {
    Column(modifier = modifier) {
        AddTileSection(onAddTileClick = onAddTileClick)

        RequireUnlockSection(
            requireUnlock = requireUnlock,
            onToggleRequireUnlock = onToggleRequireUnlock
        )

        Text(
            text = stringResource(R.string.calendar_list_header),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        if (calendars.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_calendars_found),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn {
                items(calendars, key = { it.id }) { calendar ->
                    CalendarRow(
                        calendar = calendar,
                        enabled = calendar.id !in ignoredIds,
                        onToggle = { enabled -> onToggleCalendar(calendar.id, enabled) }
                    )
                }
            }
        }
    }
}

@Composable
fun AddTileSection(
    modifier: Modifier = Modifier,
    onAddTileClick: () -> Unit
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.add_tile_instructions),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onAddTileClick,
            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            Text(stringResource(R.string.add_tile_button))
        }
    }
}

@Composable
fun RequireUnlockSection(
    modifier: Modifier = Modifier,
    requireUnlock: Boolean,
    onToggleRequireUnlock: (Boolean) -> Unit
) {
    var checked by remember(requireUnlock) { mutableStateOf(requireUnlock) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.require_unlock_label),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.require_unlock_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                onToggleRequireUnlock(it)
            }
        )
    }
}

@Composable
fun CalendarRow(
    calendar: CalendarInfo,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    var checked by remember(calendar.id, enabled) { mutableStateOf(enabled) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CalendarColorDot(color = Color(calendar.color))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp)
        ) {
            Text(
                text = calendar.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (calendar.accountName.isNotBlank() && calendar.accountName != calendar.displayName) {
                Text(
                    text = calendar.accountName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                onToggle(it)
            }
        )
    }
}

@Composable
fun CalendarColorDot(color: Color) {
    Box(
        modifier = Modifier.size(14.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = color)
        }
    }
}
