package nl.agroqirax.calendartile

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.agroqirax.calendartile.ui.theme.CalendarTileTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGrantedState.value = granted
        if (granted) {
            calendarsState.value = CalendarHelper.getCalendars(this)
        }
        refreshNextEvent()
        requestTileUpdate()
    }

    private val permissionGrantedState = mutableStateOf(false)
    private val calendarsState = mutableStateOf<List<CalendarInfo>>(emptyList())
    private val requireUnlockState = mutableStateOf(false)
    private val tileOnboardingCompleteState = mutableStateOf(false)
    private val tileIconStyleState = mutableStateOf(TileIconStyle.DEFAULT)

    /** Only so the icon previews can show what the tile will actually render. */
    private val nextEventState = mutableStateOf<NextEvent?>(null)
    private val customIconRulesState = mutableStateOf<List<CustomIconRule>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionGrantedState.value = CalendarHelper.hasPermission(this)
        if (permissionGrantedState.value) {
            calendarsState.value = CalendarHelper.getCalendars(this)
        }
        requireUnlockState.value = CalendarPrefs.isRequireUnlockEnabled(this)
        tileOnboardingCompleteState.value = CalendarPrefs.isTileOnboardingComplete(this)
        tileIconStyleState.value = CalendarPrefs.getTileIconStyle(this)
        customIconRulesState.value = CalendarPrefs.getCustomIconRules(this)
        refreshNextEvent()

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
                        tileIconStyle = tileIconStyleState.value,
                        nextEvent = nextEventState.value,
                        onRequestPermission = {
                            requestPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        },
                        onToggleCalendar = { calendarId, enabled ->
                            CalendarPrefs.setCalendarIgnored(this, calendarId, !enabled)
                            // Changing which calendars count can change the next
                            // event, and with it the icon previews.
                            refreshNextEvent()
                            requestTileUpdate()
                        },
                        onToggleRequireUnlock = { enabled ->
                            CalendarPrefs.setRequireUnlock(this, enabled)
                            requestTileUpdate()
                        },
                        onSelectTileIconStyle = { style ->
                            CalendarPrefs.setTileIconStyle(this, style)
                            tileIconStyleState.value = style
                            requestTileUpdate()
                        },
                        customIconRules = customIconRulesState.value,
                        onAddCustomIconRule = { rule ->
                            saveCustomIconRules(customIconRulesState.value + rule)
                        },
                        onUpdateCustomIconRule = { old, new ->
                            // Replace in place so the list does not reshuffle under
                            // the user when they only changed an icon.
                            saveCustomIconRules(
                                customIconRulesState.value.map { if (it == old) new else it }
                            )
                        },
                        onDeleteCustomIconRule = { rule ->
                            saveCustomIconRules(customIconRulesState.value - rule)
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
        tileIconStyleState.value = CalendarPrefs.getTileIconStyle(this)
        customIconRulesState.value = CalendarPrefs.getCustomIconRules(this)
        refreshNextEvent()
    }

    private fun saveCustomIconRules(rules: List<CustomIconRule>) {
        CalendarPrefs.setCustomIconRules(this, rules)
        customIconRulesState.value = rules
        requestTileUpdate()
    }

    private fun refreshNextEvent() {
        if (!CalendarHelper.hasPermission(this)) {
            nextEventState.value = null
            return
        }
        lifecycleScope.launch {
            val ignoredIds = CalendarPrefs.getIgnoredCalendarIds(this@MainActivity)
            val nextEvent = withContext(Dispatchers.IO) {
                CalendarHelper.getNextEvent(this@MainActivity, ignoredIds)
            }
            nextEventState.value = nextEvent
        }
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
            Icon.createWithResource(this, R.drawable.ic_today),
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
    tileIconStyle: TileIconStyle,
    nextEvent: NextEvent?,
    onRequestPermission: () -> Unit,
    onToggleCalendar: (Long, Boolean) -> Unit,
    onToggleRequireUnlock: (Boolean) -> Unit,
    onSelectTileIconStyle: (TileIconStyle) -> Unit,
    customIconRules: List<CustomIconRule>,
    onAddCustomIconRule: (CustomIconRule) -> Unit,
    onUpdateCustomIconRule: (CustomIconRule, CustomIconRule) -> Unit,
    onDeleteCustomIconRule: (CustomIconRule) -> Unit,
    onAddTileClick: () -> Unit,
    onAddTileOnboardingClick: () -> Unit,
    onSkipTileOnboarding: () -> Unit
) {
    // Local to the composition rather than plumbed through the Activity: it is
    // pure navigation state, and the list it shows lives in the caller anyway.
    var showCustomIcons by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showCustomIcons) { showCustomIcons = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (showCustomIcons) R.string.custom_icons_label else R.string.app_name
                        )
                    )
                },
                navigationIcon = {
                    if (showCustomIcons) {
                        IconButton(onClick = { showCustomIcons = false }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            !permissionGranted -> {
                OnboardingScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    iconRes = R.drawable.ic_today,
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

            showCustomIcons -> {
                CustomIconsScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    rules = customIconRules,
                    onAddRule = onAddCustomIconRule,
                    onUpdateRule = onUpdateCustomIconRule,
                    onDeleteRule = onDeleteCustomIconRule
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
                    tileIconStyle = tileIconStyle,
                    nextEvent = nextEvent,
                    customIconRules = customIconRules,
                    onToggleCalendar = onToggleCalendar,
                    onToggleRequireUnlock = onToggleRequireUnlock,
                    onSelectTileIconStyle = onSelectTileIconStyle,
                    onOpenCustomIcons = { showCustomIcons = true },
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
    tileIconStyle: TileIconStyle,
    nextEvent: NextEvent?,
    customIconRules: List<CustomIconRule>,
    onToggleCalendar: (Long, Boolean) -> Unit,
    onToggleRequireUnlock: (Boolean) -> Unit,
    onSelectTileIconStyle: (TileIconStyle) -> Unit,
    onOpenCustomIcons: () -> Unit,
    onAddTileClick: () -> Unit
) {
    Column(modifier = modifier) {
        AddTileSection(onAddTileClick = onAddTileClick)

        RequireUnlockSection(
            requireUnlock = requireUnlock,
            onToggleRequireUnlock = onToggleRequireUnlock
        )

        TileIconSection(
            tileIconStyle = tileIconStyle,
            nextEvent = nextEvent,
            customIconRules = customIconRules,
            onSelectTileIconStyle = onSelectTileIconStyle
        )

        // Custom keyword mappings only apply when the tile is actually drawing a
        // matched glyph — hide the setting rather than let it dangle unused.
        if (tileIconStyle == TileIconStyle.SMART) {
            CustomIconsSection(onClick = onOpenCustomIcons)
        }

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
fun TileIconSection(
    modifier: Modifier = Modifier,
    tileIconStyle: TileIconStyle,
    nextEvent: NextEvent?,
    customIconRules: List<CustomIconRule>,
    onSelectTileIconStyle: (TileIconStyle) -> Unit
) {
    var dialogOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { dialogOpen = true }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tile_icon_label),
                style = MaterialTheme.typography.bodyLarge
            )
            // The summary is the current choice, the way Android's own settings
            // rows read, rather than a static description.
            Text(
                text = stringResource(tileIconStyle.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Trailing, like the switch above and the chevron below: state on the right.
        TileIconBadge {
            TileIconPreview(
                style = tileIconStyle,
                event = nextEvent,
                customIconRules = customIconRules
            )
        }
    }

    if (dialogOpen) {
        TileIconDialog(
            selected = tileIconStyle,
            nextEvent = nextEvent,
            customIconRules = customIconRules,
            onSelect = {
                onSelectTileIconStyle(it)
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false }
        )
    }
}

/**
 * The trailing affordance for a settings row that opens a dialog or another screen,
 * rather than toggling something in place.
 */
@Composable
private fun OpensSubpageChevron() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        // Decorative: the row's own label already says where it goes.
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Marks [TileIconStyle.SMART] as still being tuned, next to its label in the style
 * picker — its keyword table is the part of the app most likely to misfire.
 */
@Composable
private fun BetaBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_science),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = stringResource(R.string.tile_icon_style_beta_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1
        )
    }
}

/**
 * A circular well for a tile icon preview, so a 24dp glyph sitting on its own in a
 * settings row has some visual weight instead of floating.
 */
@Composable
private fun TileIconBadge(content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun CustomIconsSection(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.custom_icons_label),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.custom_icons_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OpensSubpageChevron()
    }
}

@Composable
private fun TileIconDialog(
    selected: TileIconStyle,
    nextEvent: NextEvent?,
    customIconRules: List<CustomIconRule>,
    onSelect: (TileIconStyle) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tile_icon_label)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                TileIconStyle.entries.forEach { style ->
                    val isSelected = style == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                }
                            )
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(style) }
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TileIconPreview(
                            style = style,
                            event = nextEvent,
                            customIconRules = customIconRules
                        )
                        Spacer(modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(style.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee(),
                            maxLines = 1
                        )
                        if (style == TileIconStyle.SMART) {
                            Spacer(modifier = Modifier.size(8.dp))
                            BetaBadge()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                // Platform string, already translated everywhere we ship.
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

/** Draws exactly what the tile would draw for [style], via [TileIconResolver]. */
@Composable
private fun TileIconPreview(
    style: TileIconStyle,
    event: NextEvent?,
    customIconRules: List<CustomIconRule>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tint = MaterialTheme.colorScheme.onSurface
    val iconModifier = modifier.size(24.dp)

    when (val spec = TileIconResolver.resolve(style, event, customIconRules)) {
        is TileIconSpec.Glyph -> Icon(
            painter = painterResource(spec.resId),
            contentDescription = null,
            modifier = iconModifier,
            tint = tint
        )

        is TileIconSpec.Day -> {
            val bitmap = remember(spec.dayOfMonth) {
                TileIconRenderer.renderDayBitmap(context, spec.dayOfMonth).asImageBitmap()
            }
            Icon(
                bitmap = bitmap,
                contentDescription = null,
                modifier = iconModifier,
                tint = tint
            )
        }
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
