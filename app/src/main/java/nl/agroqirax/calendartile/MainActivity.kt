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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
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
        }
        requestTileUpdate()
    }

    private val permissionGrantedState = mutableStateOf(false)
    private val calendarsState = mutableStateOf<List<CalendarInfo>>(emptyList())
    private val requireUnlockState = mutableStateOf(false)
    private val tileOnboardingCompleteState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionGrantedState.value = CalendarHelper.hasPermission(this)
        if (permissionGrantedState.value) {
            calendarsState.value = CalendarHelper.getCalendars(this)
        }
        requireUnlockState.value = CalendarPrefs.isRequireUnlockEnabled(this)
        tileOnboardingCompleteState.value = CalendarPrefs.isTileOnboardingComplete(this)

        setContent {
            CalendarTileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CalendarTileApp(
                        permissionGranted = permissionGrantedState.value,
                        tileOnboardingComplete = tileOnboardingCompleteState.value,
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
                        onAddTileClick = { requestAddTile() },
                        onAddTileOnboardingClick = { requestAddTile(onResult = ::completeTileOnboarding) },
                        onSkipTileOnboarding = { completeTileOnboarding() }
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
        tileOnboardingCompleteState.value = CalendarPrefs.isTileOnboardingComplete(this)
    }

    private fun completeTileOnboarding() {
        CalendarPrefs.setTileOnboardingComplete(this, true)
        tileOnboardingCompleteState.value = true
    }

    private fun requestTileUpdate() {
        TileService.requestListeningState(
            this,
            ComponentName(this, CalendarTileService::class.java)
        )
    }

    private fun requestAddTile(onResult: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult()
            return
        }

        val statusBarManager = getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            onResult()
            return
        }
        statusBarManager.requestAddTileService(
            ComponentName(this, CalendarTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_calendar),
            mainExecutor
        ) { /* result code ignored; system already no-ops if the tile is already added */
            onResult()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTileApp(
    permissionGranted: Boolean,
    tileOnboardingComplete: Boolean,
    calendars: List<CalendarInfo>,
    ignoredIds: Set<Long>,
    requireUnlock: Boolean,
    onRequestPermission: () -> Unit,
    onToggleCalendar: (Long, Boolean) -> Unit,
    onToggleRequireUnlock: (Boolean) -> Unit,
    onAddTileClick: () -> Unit,
    onAddTileOnboardingClick: () -> Unit,
    onSkipTileOnboarding: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        }
    ) { padding ->
        when {
            !permissionGranted -> {
                OnboardingScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    iconRes = R.drawable.ic_calendar,
                    headline = stringResource(R.string.permission_headline),
                    body = stringResource(R.string.permission_explanation),
                    primaryButtonLabel = stringResource(R.string.grant_permission),
                    onPrimaryButtonClick = onRequestPermission
                )
            }

            !tileOnboardingComplete -> {
                AddTileScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onAddTileClick = onAddTileOnboardingClick,
                    onSkip = onSkipTileOnboarding
                )
            }

            else -> {
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
}

@Composable
fun OnboardingIcon(iconRes: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(96.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    iconRes: Int,
    headline: String,
    body: String,
    primaryButtonLabel: String,
    onPrimaryButtonClick: () -> Unit,
    secondaryButtonLabel: String? = null,
    onSecondaryButtonClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OnboardingIcon(iconRes = iconRes)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onPrimaryButtonClick) {
                Text(primaryButtonLabel)
            }
            if (secondaryButtonLabel != null && onSecondaryButtonClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onSecondaryButtonClick) {
                    Text(secondaryButtonLabel)
                }
            }
        }
    }
}

@Composable
fun AddTileScreen(
    modifier: Modifier = Modifier,
    onAddTileClick: () -> Unit,
    onSkip: () -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        OnboardingScreen(
            modifier = modifier,
            iconRes = R.drawable.ic_widgets,
            headline = stringResource(R.string.add_tile_headline),
            body = stringResource(R.string.add_tile_explanation_auto),
            primaryButtonLabel = stringResource(R.string.add_tile_button),
            onPrimaryButtonClick = onAddTileClick,
            secondaryButtonLabel = stringResource(R.string.skip_for_now),
            onSecondaryButtonClick = onSkip
        )
    } else {
        OnboardingScreen(
            modifier = modifier,
            iconRes = R.drawable.ic_widgets,
            headline = stringResource(R.string.add_tile_headline),
            body = stringResource(R.string.add_tile_instructions),
            primaryButtonLabel = stringResource(R.string.continue_button),
            onPrimaryButtonClick = onSkip
        )
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.add_tile_section_label),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.add_tile_instructions),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onAddTileClick) {
                    Text(stringResource(R.string.add_tile_button))
                }
            }
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
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = {
                    checked = it
                    onToggleRequireUnlock(it)
                }
            )
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
            onCheckedChange = null
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
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = {
                    checked = it
                    onToggle(it)
                }
            )
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
            onCheckedChange = null
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
