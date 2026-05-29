package com.primitives.stoicboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.primitives.stoicboard.data.StoicSettings

enum class AppClass { SAFE, MESSENGER, WORK }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: MainViewModel = viewModel()
) {
    val saved by vm.settings.collectAsState()
    val apps by vm.apps.collectAsState()

    var draft by remember(saved) { mutableStateOf(saved) }
    var picker by remember { mutableStateOf<AppClass?>(null) }
    var query by remember { mutableStateOf("") }

    val appLabelMap = remember(apps) {
        apps.associate { it.packageName to it.label }
    }

    val activePicker = picker
    if (activePicker != null) {
        AppPicker(
            appClass = activePicker,
            draft = draft,
            apps = apps,
            query = query,
            onQuery = { query = it },
            onToggle = { packageName ->
                draft = when (activePicker) {
                    AppClass.SAFE -> draft.copy(safeZonePackages = draft.safeZonePackages.toggle(packageName))
                    AppClass.MESSENGER -> draft.copy(messengerPackages = draft.messengerPackages.toggle(packageName))
                    AppClass.WORK -> draft.copy(workAppPackages = draft.workAppPackages.toggle(packageName))
                }
            },
            onClose = {
                picker = null
                query = ""
            }
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.settings),
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.settings_subtitle),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SChip(text = stringResource(R.string.back), color = TextSecondary) { onBack() }
                    SChip(text = stringResource(R.string.save), color = StoicGreen) {
                        vm.save(draft)
                        onBack()
                    }
                }
            }
        }

        item { SSection(title = stringResource(R.string.typing_languages), subtitle = stringResource(R.string.typing_languages_sub)) }

        item {
            SCard {
                val availableLangs = listOf(
                    "EN" to "English",
                    "RU" to "Русский",
                    "ES" to "Español",
                    "UK" to "Українська"
                )
                availableLangs.forEachIndexed { index, (code, name) ->
                    SSwitchRow(
                        label = name,
                        checked = draft.typingLanguages.contains(code),
                        accent = StoicBlue,
                        onChange = { checked ->
                            val updated = if (checked) draft.typingLanguages + code
                            else draft.typingLanguages - code
                            if (updated.isNotEmpty()) draft = draft.copy(typingLanguages = updated)
                        }
                    )
                    if (index < availableLangs.size - 1) SDivider()
                }
            }
        }

        item { SSection(title = stringResource(R.string.app_language), subtitle = stringResource(R.string.app_language_sub)) }

        item {
            SCard {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val langs = listOf(
                        "system" to stringResource(R.string.lang_system),
                        "en" to "English",
                        "ru" to "Русский",
                        "es" to "Español",
                        "uk" to "Українська"
                    )
                    langs.forEach { (code, name) ->
                        val selected = draft.appLanguage == code
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) StoicPurple.copy(alpha = 0.2f) else Surface2)
                                .border(1.dp, if (selected) StoicPurple.copy(alpha = 0.5f) else Border, RoundedCornerShape(8.dp))
                                .clickable { draft = draft.copy(appLanguage = code) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = name,
                                color = if (selected) StoicPurple else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        item { SSection(title = stringResource(R.string.delay_windows), subtitle = stringResource(R.string.delay_windows_sub)) }

        item {
            SCard {
                SRangeRow(
                    title = stringResource(R.string.cat_default),
                    minMs = draft.defaultMinMs,
                    maxMs = draft.defaultMaxMs,
                    accent = StoicGreen,
                    onMin = { draft = draft.copy(defaultMinMs = it) },
                    onMax = { draft = draft.copy(defaultMaxMs = it) }
                )
                SDivider()
                SRangeRow(
                    title = stringResource(R.string.cat_messenger),
                    minMs = draft.messengerMinMs,
                    maxMs = draft.messengerMaxMs,
                    accent = StoicBlue,
                    onMin = { draft = draft.copy(messengerMinMs = it) },
                    onMax = { draft = draft.copy(messengerMaxMs = it) }
                )
                SDivider()
                SRangeRow(
                    title = stringResource(R.string.cat_work),
                    minMs = draft.workMinMs,
                    maxMs = draft.workMaxMs,
                    accent = StoicPurple,
                    onMin = { draft = draft.copy(workMinMs = it) },
                    onMax = { draft = draft.copy(workMaxMs = it) }
                )
                SDivider()
                Text(
                    text = stringResource(R.string.safe_zone_hint),
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        item { SSection(title = stringResource(R.string.heat_control), subtitle = stringResource(R.string.heat_control_sub)) }

        item {
            SCard {
                SSliderRow(
                    label = stringResource(R.string.speed_limit),
                    value = draft.heatThresholdApm.toFloat(),
                    range = 120f..700f,
                    steps = 57,
                    accent = StoicRed,
                    display = { "${it.toInt()} APM" },
                    onValue = { draft = draft.copy(heatThresholdApm = it.toInt()) }
                )
                SDivider()
                SSliderRow(
                    label = stringResource(R.string.sensitivity),
                    value = draft.heatSensitivity,
                    range = 0.5f..2.5f,
                    steps = 19,
                    accent = StoicAmber,
                    display = { "x${String.format("%.1f", it)}" },
                    onValue = { draft = draft.copy(heatSensitivity = it) }
                )
                SDivider()
                SSliderRow(
                    label = stringResource(R.string.cooldown),
                    value = draft.rageCooldownMs / 1000f,
                    range = 0.5f..10f,
                    steps = 18,
                    accent = StoicAmber,
                    display = { "${String.format("%.1f", it)} s" },
                    onValue = { draft = draft.copy(rageCooldownMs = (it * 1000).toLong()) }
                )
                SDivider()
                SSwitchRow(
                    label = stringResource(R.string.show_heat_bar),
                    checked = draft.heatBarEnabled,
                    accent = StoicAmber,
                    onChange = { draft = draft.copy(heatBarEnabled = it) }
                )
            }
        }

        item { SSection(title = stringResource(R.string.backspace), subtitle = stringResource(R.string.backspace_sub)) }

        item {
            SCard {
                val alwaysActive = stringResource(R.string.always_active)
                val charsUnit = stringResource(R.string.chars_unit)
                SSliderRow(
                    label = stringResource(R.string.session_typed),
                    value = draft.backspaceActivationChars.toFloat(),
                    range = 0f..200f,
                    steps = 39,
                    accent = StoicAmber,
                    display = { if (it.toInt() == 0) alwaysActive else "${it.toInt()} $charsUnit" },
                    onValue = { draft = draft.copy(backspaceActivationChars = it.toInt()) }
                )
                SDivider()
                SSliderRow(
                    label = stringResource(R.string.hold_delay),
                    value = draft.backspaceHoldMs.toFloat(),
                    range = 120f..900f,
                    steps = 25,
                    accent = StoicGreen,
                    display = { "${it.toInt()} ms" },
                    onValue = { draft = draft.copy(backspaceHoldMs = it.toLong()) }
                )
                SDivider()
                SSliderRow(
                    label = stringResource(R.string.repeat_speed),
                    value = draft.backspaceRepeatMs.toFloat(),
                    range = 35f..140f,
                    steps = 20,
                    accent = StoicBlue,
                    display = { "${it.toInt()} ms" },
                    onValue = { draft = draft.copy(backspaceRepeatMs = it.toLong()) }
                )
                SDivider()
                SSliderRow(
                    label = stringResource(R.string.bulk_delete),
                    value = draft.backspaceAbuseChars.toFloat(),
                    range = 6f..60f,
                    steps = 53,
                    accent = StoicRed,
                    display = { "${it.toInt()} $charsUnit" },
                    onValue = { draft = draft.copy(backspaceAbuseChars = it.toInt()) }
                )
            }
        }

        item { SSection(title = stringResource(R.string.app_classes), subtitle = stringResource(R.string.app_classes_sub)) }

        item {
            SCard {
                SSwitchRow(
                    label = stringResource(R.string.make_unlisted_safe),
                    checked = draft.defaultIsSafeZone,
                    accent = StoicGreen,
                    onChange = { draft = draft.copy(defaultIsSafeZone = it) }
                )
                SDivider()
                SZoneRow(
                    title = stringResource(R.string.safe_zone),
                    subtitle = stringResource(R.string.no_delay),
                    count = draft.safeZonePackages.size,
                    color = StoicGreen,
                    onEdit = { picker = AppClass.SAFE }
                )
                SPackages(packages = draft.safeZonePackages, appLabelMap = appLabelMap) {
                    draft = draft.copy(safeZonePackages = draft.safeZonePackages - it)
                }
            }
        }

        item {
            SCard {
                SZoneRow(
                    title = stringResource(R.string.cat_messenger),
                    subtitle = stringResource(R.string.messenger_delay_sub),
                    count = draft.messengerPackages.size,
                    color = StoicBlue,
                    onEdit = { picker = AppClass.MESSENGER }
                )
                SPackages(packages = draft.messengerPackages, appLabelMap = appLabelMap) {
                    draft = draft.copy(messengerPackages = draft.messengerPackages - it)
                }
            }
        }

        item {
            SCard {
                SZoneRow(
                    title = stringResource(R.string.cat_work),
                    subtitle = stringResource(R.string.work_delay_sub),
                    count = draft.workAppPackages.size,
                    color = StoicPurple,
                    onEdit = { picker = AppClass.WORK }
                )
                SPackages(packages = draft.workAppPackages, appLabelMap = appLabelMap) {
                    draft = draft.copy(workAppPackages = draft.workAppPackages - it)
                }
                SDivider()
                SSwitchRow(
                    label = stringResource(R.string.block_double_excl),
                    checked = draft.blockDoubleExclamationInWorkApps,
                    accent = StoicPurple,
                    onChange = { draft = draft.copy(blockDoubleExclamationInWorkApps = it) }
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1C0A0A))
                    .border(1.dp, StoicRed.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable { draft = StoicSettings() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.reset_defaults),
                    color = StoicRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AppPicker(
    appClass: AppClass,
    draft: StoicSettings,
    apps: List<AppInfo>,
    query: String,
    onQuery: (String) -> Unit,
    onToggle: (String) -> Unit,
    onClose: () -> Unit
) {
    val selected = when (appClass) {
        AppClass.SAFE -> draft.safeZonePackages
        AppClass.MESSENGER -> draft.messengerPackages
        AppClass.WORK -> draft.workAppPackages
    }

    val color = when (appClass) {
        AppClass.SAFE -> StoicGreen
        AppClass.MESSENGER -> StoicBlue
        AppClass.WORK -> StoicPurple
    }

    val title = when (appClass) {
        AppClass.SAFE -> stringResource(R.string.safe_zone)
        AppClass.MESSENGER -> stringResource(R.string.cat_messenger)
        AppClass.WORK -> stringResource(R.string.cat_work)
    }

    val filtered = remember(query, apps) {
        apps.filter {
            query.isBlank()
                    || it.label.contains(query, ignoreCase = true)
                    || it.packageName.contains(query, ignoreCase = true)
        }
    }

    val doneLabel = stringResource(R.string.done)
    val searchPlaceholder = stringResource(R.string.search_placeholder)
    val appsUnit = stringResource(R.string.apps_unit)
    val selectedUnit = stringResource(R.string.selected_unit)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            SChip(text = doneLabel, color = color, onClick = onClose)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (query.isEmpty()) {
                Text(text = searchPlaceholder, color = TextMuted, fontSize = 13.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                cursorBrush = SolidColor(color),
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "${filtered.size} $appsUnit · ${selected.size} $selectedUnit",
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                val isSelected = app.packageName in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) color.copy(alpha = 0.13f) else Surface1)
                        .border(1.dp, if (isSelected) color.copy(alpha = 0.4f) else Border, RoundedCornerShape(12.dp))
                        .clickable { onToggle(app.packageName) }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = app.packageName,
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(color, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .border(1.dp, Border, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun SSection(title: String, subtitle: String) {
    Column {
        Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(text = subtitle, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
fun SChip(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Border)
    )
}

@Composable
fun SRangeRow(
    title: String,
    minMs: Long,
    maxMs: Long,
    accent: Color,
    onMin: (Long) -> Unit,
    onMax: (Long) -> Unit
) {
    val minLabel = stringResource(R.string.min_delay)
    val maxLabel = stringResource(R.string.max_delay)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        SSliderRow(
            label = minLabel,
            value = minMs / 1000f,
            range = 0.5f..15f,
            steps = 28,
            accent = accent,
            display = { "${String.format("%.1f", it)} s" },
            onValue = { onMin((it * 1000).toLong()) }
        )
        SSliderRow(
            label = maxLabel,
            value = maxMs / 1000f,
            range = 1f..25f,
            steps = 47,
            accent = accent,
            display = { "${String.format("%.1f", it)} s" },
            onValue = { onMax((it * 1000).toLong()) }
        )
    }
}

@Composable
fun SSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    accent: Color,
    display: (Float) -> String,
    onValue: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = TextSecondary, fontSize = 12.sp)
            Text(
                text = display(value),
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Surface3
            )
        )
    }
}

@Composable
fun SSwitchRow(
    label: String,
    checked: Boolean,
    accent: Color,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedTrackColor = Surface3
            )
        )
    }
}

@Composable
fun SZoneRow(
    title: String,
    subtitle: String,
    count: Int,
    color: Color,
    onEdit: () -> Unit
) {
    val editLabel = stringResource(R.string.edit)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
                Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(text = "$count", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = subtitle, color = TextMuted, fontSize = 12.sp)
        }
        SChip(text = editLabel, color = color, onClick = onEdit)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SPackages(
    packages: Set<String>,
    appLabelMap: Map<String, String>,
    onRemove: (String) -> Unit
) {
    if (packages.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        packages.take(8).forEach { packageName ->
            val label = appLabelMap[packageName] ?: packageName.substringAfterLast('.')
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2)
                    .border(1.dp, Border, RoundedCornerShape(8.dp))
                    .clickable { onRemove(packageName) }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "$label ×",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        val overflow = packages.size - 8
        if (overflow > 0) {
            Text(text = "+$overflow more", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value