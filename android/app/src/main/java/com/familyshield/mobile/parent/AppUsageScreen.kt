package com.familyshield.mobile.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyshield.mobile.R
import com.familyshield.mobile.net.AppUsageEntry
import com.familyshield.mobile.net.AppUsageLimit
import com.familyshield.mobile.net.AppUsageLimitBody
import com.familyshield.mobile.net.Child
import com.familyshield.mobile.net.Device
import com.familyshield.mobile.net.UpdateAppUsageLimitBody
import com.familyshield.mobile.net.UsageDay
import androidx.compose.ui.res.stringResource
import com.familyshield.mobile.ui.theme.Navy
import com.familyshield.mobile.ui.theme.SkyBright
import com.familyshield.mobile.ui.theme.SkyTint
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun fmtDur(min: Int): String {
    val h = min / 60; val m = min % 60
    return when { h > 0 && m > 0 -> "${h}h ${m}m"; h > 0 -> "${h}h"; else -> "${m}m" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageScreen(vm: ParentViewModel, initialChildId: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    LaunchedEffect(initialChildId) { vm.loadAppUsage(initialChildId) }
    val snackbar = remember { SnackbarHostState() }
    val usage = vm.appUsage
    val usageChildId = vm.appUsageChildId ?: initialChildId
    var editingLimit by remember(usageChildId) { mutableStateOf<AppUsageLimitDraft?>(null) }
    val childConnected = vm.children.find { it.id == usageChildId }?.primaryDevice()?.isConnected() == true
    val selectedUsageDay = vm.appUsageDate ?: usage?.selectedDay ?: usage?.week?.lastOrNull()?.day
    val selectedDayDetail = usage?.dayDetails?.find { it.day == selectedUsageDay }
    val selectedApps = selectedDayDetail?.apps ?: usage?.apps.orEmpty()
    val selectedTotalMin = selectedDayDetail?.totalMin ?: usage?.totalTodayMin ?: 0
    val selectedPreviousMin = selectedDayDetail?.previousMin ?: usage?.yesterdayMin ?: 0
    val selectedPreviousHasData = selectedDayDetail?.previousHasData ?: usage?.yesterdayHasData ?: false
    val isCurrentUsageDay = selectedUsageDay == usage?.week?.lastOrNull()?.day
    val selectedLastUpdatedAt = if (selectedDayDetail != null) selectedDayDetail.lastUpdatedAt else usage?.lastUpdatedAt
    val comparisonDayLabel = if (isCurrentUsageDay) null else previousDayName(selectedUsageDay)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appusage_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {

                // Child switcher (segmented pills)
                if (vm.children.size > 1) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            vm.children.forEach { c -> ChildSegment(c, selected = c.id == vm.appUsageChildId) { vm.loadAppUsage(c.id) } }
                        }
                    }
                }

                if (usage == null) {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (
                    usage.totalTodayMin == 0 &&
                    usage.apps.isEmpty() &&
                    usage.week.none { it.hasData || it.min > 0 } &&
                    (!childConnected || usage.appUsageAccessGranted == false)
                ) {
                    Text(
                        stringResource(
                            when {
                                !childConnected -> R.string.appusage_connect_child
                                usage.appUsageAccessGranted == false -> R.string.appusage_permission_missing
                                else -> R.string.appusage_empty
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val totalLimit = usage.limits.firstOrNull { it.type == "total" }
                    val summaryLabel = if (isCurrentUsageDay) {
                        stringResource(R.string.appusage_total_today)
                    } else {
                        stringResource(R.string.appusage_total_usage)
                    }
                    SummaryCard(summaryLabel, selectedTotalMin, selectedPreviousMin, selectedPreviousHasData, comparisonDayLabel)
                    TotalLimitControl(
                        limit = totalLimit,
                        onEdit = { editingLimit = AppUsageLimitDraft.Total(totalLimit) },
                        onToggle = { limit, active -> vm.updateAppUsageLimit(usageChildId, limit.id, UpdateAppUsageLimitBody(active = active)) },
                    )
                    selectedLastUpdatedAt?.let { LastUpdatedText(it) }
                    WeeklyTrend(
                        days = usage.week,
                        avgMin = usage.avgWeekMin,
                        selectedDay = selectedUsageDay,
                        onDaySelected = { day -> vm.selectAppUsageDate(day) },
                    )
                    if (selectedApps.isEmpty()) {
                        Text(
                            stringResource(R.string.appusage_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Breakdown(
                            apps = selectedApps,
                            limits = usage.limits,
                            onEditLimit = { app, limit -> editingLimit = AppUsageLimitDraft.App(app, limit) },
                            onToggleLimit = { limit, active -> vm.updateAppUsageLimit(usageChildId, limit.id, UpdateAppUsageLimitBody(active = active)) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    editingLimit?.let { draft ->
        AppUsageLimitDialog(
            draft = draft,
            onDismiss = { editingLimit = null },
            onSave = { minutes, active ->
                when (draft) {
                    is AppUsageLimitDraft.Total -> {
                        val existing = draft.limit
                        if (existing == null) {
                            vm.saveAppUsageLimit(usageChildId, AppUsageLimitBody("total", minutes, active = active))
                        } else {
                            vm.updateAppUsageLimit(usageChildId, existing.id, UpdateAppUsageLimitBody(minutes, active))
                        }
                    }
                    is AppUsageLimitDraft.App -> {
                        val existing = draft.limit
                        if (existing == null) {
                            vm.saveAppUsageLimit(
                                usageChildId,
                                AppUsageLimitBody(
                                    type = "app",
                                    limitMinutes = minutes,
                                    packageName = draft.app.packageName,
                                    app = draft.app.app,
                                    category = draft.app.category,
                                    active = active,
                                ),
                            )
                        } else {
                            vm.updateAppUsageLimit(usageChildId, existing.id, UpdateAppUsageLimitBody(minutes, active))
                        }
                    }
                }
                editingLimit = null
            },
            onDelete = { limit ->
                vm.deleteAppUsageLimit(usageChildId, limit.id)
                editingLimit = null
            },
        )
    }
}

private sealed class AppUsageLimitDraft {
    abstract val limit: AppUsageLimit?

    data class Total(override val limit: AppUsageLimit?) : AppUsageLimitDraft()
    data class App(val app: AppUsageEntry, override val limit: AppUsageLimit?) : AppUsageLimitDraft()
}

@Composable
private fun previousDayName(day: String?): String? {
    if (day == null) return null
    val locale = Locale.getDefault()
    return remember(day, locale) {
        runCatching {
            LocalDate.parse(day).minusDays(1).dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        }.getOrNull()
    }
}

@Composable
private fun LastUpdatedText(iso: String) {
    val text = remember(iso) {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val formatted = runCatching {
            OffsetDateTime.parse(iso).toLocalTime().format(timeFormatter)
        }.recoverCatching {
            OffsetTime.parse(iso).toLocalTime().format(timeFormatter)
        }.getOrNull()
        formatted ?: Regex("""\d{1,2}:\d{2}:\d{2}""").find(iso)?.value ?: iso
    }
    Text(
        stringResource(R.string.appusage_last_updated, text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChildSegment(child: Child, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent) {
        Text(child.displayName, Modifier.padding(horizontal = 18.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryCard(
    label: String,
    totalMin: Int,
    previousMin: Int,
    previousHasData: Boolean,
    comparisonDayLabel: String?,
) {
    val pct = if (previousMin > 0) Math.round((totalMin - previousMin) * 100f / previousMin) else 0
    Surface(color = Navy, shape = MaterialTheme.shapes.large, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.75f), letterSpacing = 1.5.sp)
                Text(fmtDur(totalMin), style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
                if (previousHasData && comparisonDayLabel != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (pct != 0) Icon(if (pct > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null,
                            tint = SkyBright, modifier = Modifier.size(16.dp))
                        Text(
                            when {
                                pct > 0 -> stringResource(R.string.appusage_more_than_day, pct, comparisonDayLabel)
                                pct < 0 -> stringResource(R.string.appusage_less_than_day, -pct, comparisonDayLabel)
                                else -> stringResource(R.string.appusage_same_as_day, comparisonDayLabel)
                            },
                            style = MaterialTheme.typography.labelLarge, color = SkyBright,
                        )
                    }
                }
            }
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Schedule, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun TotalLimitControl(limit: AppUsageLimit?, onEdit: () -> Unit, onToggle: (AppUsageLimit, Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(SkyTint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.secondary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.appusage_total_limit_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        limit?.let { stringResource(R.string.appusage_limit_value, fmtDur(it.limitMinutes)) }
                            ?: stringResource(R.string.appusage_no_limit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (limit != null) {
                    Switch(checked = limit.active, onCheckedChange = { onToggle(limit, it) })
                }
                TextButton(onClick = onEdit) {
                    Text(stringResource(if (limit == null) R.string.appusage_set_limit else R.string.appusage_edit_limit))
                }
            }
        }
    }
}

@Composable
private fun AppUsageLimitDialog(
    draft: AppUsageLimitDraft,
    onDismiss: () -> Unit,
    onSave: (minutes: Int, active: Boolean) -> Unit,
    onDelete: (AppUsageLimit) -> Unit,
) {
    val existing = draft.limit
    var minutesText by remember(draft) { mutableStateOf(existing?.limitMinutes?.toString().orEmpty()) }
    var active by remember(draft) { mutableStateOf(existing?.active ?: true) }
    val minutes = minutesText.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (draft) {
                    is AppUsageLimitDraft.Total -> stringResource(R.string.appusage_total_limit_title)
                    is AppUsageLimitDraft.App -> stringResource(R.string.appusage_app_limit_title, draft.app.app)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { value -> minutesText = value.filter { it.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.appusage_limit_minutes_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(if (active) R.string.appusage_limit_enabled else R.string.appusage_limit_disabled))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = minutes != null && minutes in 1..1440,
                onClick = { onSave(minutes ?: return@TextButton, active) },
            ) {
                Text(stringResource(R.string.appusage_save_limit))
            }
        },
        dismissButton = {
            Row {
                existing?.let {
                    TextButton(onClick = { onDelete(it) }) {
                        Text(stringResource(R.string.appusage_delete_limit), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.appusage_cancel))
                }
            }
        },
    )
}

@Composable
private fun WeeklyTrend(days: List<UsageDay>, avgMin: Int, selectedDay: String?, onDaySelected: (String) -> Unit) {
    val maxMin = (days.maxOfOrNull { it.min } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.appusage_weekly_trend), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.appusage_avg, fmtDur(avgMin)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { i, day ->
                    val today = i == days.lastIndex
                    val selected = day.day == selectedDay
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDaySelected(day.day) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth(0.62f).fillMaxHeight((day.min.toFloat() / maxMin).coerceIn(0.04f, 1f))
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(if (selected) SkyBright else SkyBright.copy(alpha = 0.3f)))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(day.dow, style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Breakdown(
    apps: List<AppUsageEntry>,
    limits: List<AppUsageLimit>,
    onEditLimit: (AppUsageEntry, AppUsageLimit?) -> Unit,
    onToggleLimit: (AppUsageLimit, Boolean) -> Unit,
) {
    val maxApp = (apps.maxOfOrNull { it.min } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.appusage_relevant_apps), style = MaterialTheme.typography.headlineSmall)
        apps.forEach { e ->
            val limit = findLimitForApp(limits, e)
            AppRow(e, maxApp, limit, onLimitClick = { onEditLimit(e, limit) }, onToggleLimit = onToggleLimit)
        }
    }
}

private fun findLimitForApp(limits: List<AppUsageLimit>, app: AppUsageEntry): AppUsageLimit? =
    limits.firstOrNull { limit ->
        if (limit.type != "app") return@firstOrNull false
        if (limit.packageName != null && app.packageName != null) {
            limit.packageName == app.packageName
        } else {
            limit.packageName == null && limit.app == app.app
        }
    }

private fun Child.primaryDevice(): Device? =
    devices.filter { it.revokedAt == null }
        .maxByOrNull { it.lastSeenAt ?: "" }

private fun Device.isConnected(): Boolean {
    if (revokedAt != null) return false
    isOnline?.let { return it }
    val last = lastSeenAt ?: return false
    return runCatching {
        java.time.Duration.between(OffsetDateTime.parse(last).toInstant(), java.time.Instant.now()).toMinutes() < 30
    }.getOrDefault(false)
}

private data class AppVisual(val icon: ImageVector, val tint: Color, val bg: Color)

@Composable
private fun appVisual(app: String): AppVisual = when (app.lowercase()) {
    "youtube" -> AppVisual(Icons.Filled.PlayCircle, Color(0xFFFF0000), Color(0xFFFFEBEE))
    "roblox", "minecraft" -> AppVisual(Icons.Filled.SportsEsports, Color(0xFF334155), Color(0xFFF1F5F9))
    "whatsapp" -> AppVisual(Icons.AutoMirrored.Filled.Chat, Color(0xFF25D366), Color(0xFFE8F5E9))
    "tiktok" -> AppVisual(Icons.Filled.MusicNote, Color.White, Color(0xFF111111))
    "spotify" -> AppVisual(Icons.Filled.MusicNote, Color(0xFF1DB954), Color(0xFFE8F5E9))
    "instagram" -> AppVisual(Icons.Filled.PhotoCamera, Color(0xFFE1306C), Color(0xFFFCE4EC))
    "chrome" -> AppVisual(Icons.Filled.Public, Color(0xFF1A73E8), Color(0xFFE3F2FD))
    else -> AppVisual(Icons.Filled.Apps, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceContainerHigh)
}

@Composable
private fun AppRow(
    e: AppUsageEntry,
    maxApp: Int,
    limit: AppUsageLimit?,
    onLimitClick: () -> Unit,
    onToggleLimit: (AppUsageLimit, Boolean) -> Unit,
) {
    val v = appVisual(e.app)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)), shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(v.bg), contentAlignment = Alignment.Center) {
                    Icon(v.icon, null, tint = v.tint, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(e.app, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        e.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(fmtDur(e.min), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Box(Modifier.width(80.dp).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Box(Modifier.fillMaxWidth((e.min.toFloat() / maxApp).coerceIn(0.05f, 1f)).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    limit?.let { Switch(checked = it.active, onCheckedChange = { active -> onToggleLimit(it, active) }, modifier = Modifier.height(28.dp)) }
                    TextButton(onClick = onLimitClick, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                        Text(stringResource(if (limit == null) R.string.appusage_set_limit else R.string.appusage_edit_limit))
                    }
                }
            }
        }
    }
}
