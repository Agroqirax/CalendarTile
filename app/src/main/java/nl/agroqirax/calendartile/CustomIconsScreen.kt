package nl.agroqirax.calendartile

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun CustomIconsScreen(
    modifier: Modifier = Modifier,
    rules: List<CustomIconRule>,
    onAddRule: (CustomIconRule) -> Unit,
    onUpdateRule: (CustomIconRule, CustomIconRule) -> Unit,
    onDeleteRule: (CustomIconRule) -> Unit
) {
    // editorTarget is null when adding, or the rule being edited otherwise.
    var editorTarget by remember { mutableStateOf<CustomIconRule?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.custom_icons_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { editorTarget = null; editorOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.custom_icons_add),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        if (rules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.custom_icons_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn {
                items(rules, key = { it.keyword }) { rule ->
                    CustomIconRow(
                        rule = rule,
                        onClick = { editorTarget = rule; editorOpen = true },
                        onDelete = { onDeleteRule(rule) }
                    )
                }
            }
        }
    }

    if (editorOpen) {
        val editing = editorTarget
        CustomIconRuleDialog(
            editing = editing,
            // Exclude the rule being edited, or changing only its icon would report
            // its own keyword as a duplicate.
            takenKeywords = rules.filter { it != editing }.map { it.keyword },
            onConfirm = { saved ->
                if (editing == null) onAddRule(saved) else onUpdateRule(editing, saved)
                editorOpen = false
            },
            onDismiss = { editorOpen = false }
        )
    }
}

@Composable
private fun CustomIconRow(rule: CustomIconRule, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TileIcons.resIdFor(rule.iconName)?.let { iconRes ->
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(text = rule.keyword, style = MaterialTheme.typography.bodyLarge)
            // Shown so a pattern is distinguishable from a plain keyword at a glance,
            // and so exact vs. normalized matching — which behave very differently
            // but look identical in the keyword itself — isn't hidden until edit.
            Text(
                text = stringResource(
                    when (rule.mode) {
                        MatchMode.WORDS -> R.string.custom_icons_mode_words
                        MatchMode.REGEX -> R.string.custom_icons_mode_regex
                    }
                ) + " \u2022 " + stringResource(
                    if (rule.exact) {
                        R.string.custom_icons_mode_exact
                    } else {
                        R.string.custom_icons_mode_normalized
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.custom_icons_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CustomIconRuleDialog(
    editing: CustomIconRule?,
    takenKeywords: List<String>,
    onConfirm: (CustomIconRule) -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf(editing?.keyword.orEmpty()) }
    var mode by remember { mutableStateOf(editing?.mode ?: MatchMode.WORDS) }
    var selectedIcon by remember { mutableStateOf(editing?.iconName ?: TileIcons.all.keys.first()) }
    var exact by remember { mutableStateOf(editing?.exact ?: false) }
    var sample by remember { mutableStateOf("") }

    val trimmed = keyword.trim()

    // Keywords are the list key, so a duplicate would crash the LazyColumn. Compared
    // case-insensitively since matching folds case anyway.
    val isDuplicate = takenKeywords.any { it.equals(trimmed, ignoreCase = true) }
    val isBadPattern = mode == MatchMode.REGEX &&
        trimmed.isNotEmpty() && !RegexMatching.isValid(trimmed)
    val canSave = trimmed.isNotEmpty() && !isDuplicate && !isBadPattern

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editing == null) R.string.custom_icons_add else R.string.custom_icons_edit
                )
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    // The field is a different thing in each mode, so it says so:
                    // "Keyword" for words, "Expression" for a pattern.
                    label = {
                        Text(
                            stringResource(
                                when (mode) {
                                    MatchMode.WORDS -> R.string.custom_icons_keyword
                                    MatchMode.REGEX -> R.string.custom_icons_expression
                                }
                            )
                        )
                    },
                    placeholder = {
                        Text(
                            stringResource(
                                when (mode) {
                                    MatchMode.WORDS -> R.string.custom_icons_keyword_hint
                                    MatchMode.REGEX -> R.string.custom_icons_expression_hint
                                }
                            )
                        )
                    },
                    isError = (trimmed.isNotEmpty() && isDuplicate) || isBadPattern,
                    supportingText = if (isBadPattern) {
                        { Text(stringResource(R.string.custom_icons_invalid_regex)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Scrolls sideways rather than wrapping: three chips plus a rule
                // will not fit a dialog at larger font scales or in German.
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MatchMode.entries.forEach { candidate ->
                        FilterChip(
                            selected = mode == candidate,
                            onClick = { mode = candidate },
                            label = {
                                Text(
                                    stringResource(
                                        when (candidate) {
                                            MatchMode.WORDS -> R.string.custom_icons_mode_words
                                            MatchMode.REGEX -> R.string.custom_icons_mode_regex
                                        }
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(
                                        when (candidate) {
                                            MatchMode.WORDS -> R.drawable.ic_match_word
                                            MatchMode.REGEX -> R.drawable.ic_regular_expression
                                        }
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    // Exact is an independent switch, not a third mode — the divider
                    // keeps it from reading as one row of three equal choices.
                    VerticalDivider(modifier = Modifier.height(24.dp))

                    FilterChip(
                        selected = exact,
                        onClick = { exact = !exact },
                        label = { Text(stringResource(R.string.custom_icons_mode_exact)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_match_case),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }

                Text(
                    text = stringResource(
                        when (mode) {
                            MatchMode.WORDS -> R.string.custom_icons_mode_words_help
                            MatchMode.REGEX -> R.string.custom_icons_mode_regex_help
                        }
                    ) + " " + stringResource(
                        if (exact) {
                            R.string.custom_icons_exact_on_help
                        } else {
                            R.string.custom_icons_exact_off_help
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )

                OutlinedTextField(
                    value = sample,
                    onValueChange = { sample = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.custom_icons_test)) },
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                )

                if (sample.isNotBlank() && trimmed.isNotEmpty()) {
                    // Runs the tile's own matcher, not an approximation of it.
                    val matches = !isBadPattern &&
                        EventIconMapper.matches(CustomIconRule(trimmed, selectedIcon, mode, exact), sample)
                    Text(
                        text = stringResource(
                            if (matches) {
                                R.string.custom_icons_test_match
                            } else {
                                R.string.custom_icons_test_no_match
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (matches) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 56.dp),
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .heightIn(max = 200.dp)
                        .selectableGroup()
                ) {
                    items(TileIcons.all.entries.toList(), key = { it.key }) { (name, iconRes) ->
                        IconChoice(
                            iconRes = iconRes,
                            selected = name == selectedIcon,
                            onClick = { selectedIcon = name }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onConfirm(CustomIconRule(trimmed, selectedIcon, mode, exact)) }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun IconChoice(iconRes: Int, selected: Boolean, onClick: () -> Unit) {
    // Shared so the tap target (the whole cell) and the ripple (drawn only on the
    // circle) react to the same press, without the hit-test area itself being
    // clipped down to the circle too.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(48.dp)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            // clip bounds where the ripple is drawn; the tap itself is already
            // handled by the outer, unclipped Box above.
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .indication(interactionSource, LocalIndication.current)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
