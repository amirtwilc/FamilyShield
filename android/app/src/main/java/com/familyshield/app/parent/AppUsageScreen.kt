package com.familyshield.app.parent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyshield.app.R
import com.familyshield.app.net.AppUsageEntry
import com.familyshield.app.net.Child
import androidx.compose.ui.res.stringResource
import com.familyshield.app.ui.theme.Green
import com.familyshield.app.ui.theme.Navy
import com.familyshield.app.ui.theme.SkyBright
import com.familyshield.app.ui.theme.SkyTint
import kotlinx.coroutines.launch

private fun fmtDur(min: Int): String {
    val h = min / 60; val m = min % 60
    return when { h > 0 && m > 0 -> "${h}h ${m}m"; h > 0 -> "${h}h"; else -> "${m}m" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageScreen(vm: ParentViewModel, initialChildId: String, onBack: () -> Unit) {
    LaunchedEffect(initialChildId) { vm.loadAppUsage(initialChildId) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val limitsMsg = stringResource(R.string.appusage_limits_soon)
    val usage = vm.appUsage

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
                } else if (usage.totalTodayMin == 0 && usage.apps.isEmpty()) {
                    Text(stringResource(R.string.appusage_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SummaryCard(usage.totalTodayMin, usage.yesterdayMin)
                    WeeklyTrend(usage.week.map { it.dow to it.min }, usage.avgWeekMin)
                    Breakdown(usage.apps)
                    LimitsCard { scope.launch { snackbar.showSnackbar(limitsMsg) } }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ChildSegment(child: Child, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent) {
        Text(child.displayName, Modifier.padding(horizontal = 18.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryCard(totalMin: Int, yesterdayMin: Int) {
    val pct = if (yesterdayMin > 0) Math.round((totalMin - yesterdayMin) * 100f / yesterdayMin) else 0
    Surface(color = Navy, shape = MaterialTheme.shapes.large, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.appusage_total_today), style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.75f), letterSpacing = 1.5.sp)
                Text(fmtDur(totalMin), style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (pct != 0) Icon(if (pct > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null,
                        tint = SkyBright, modifier = Modifier.size(16.dp))
                    Text(
                        when {
                            pct > 0 -> stringResource(R.string.appusage_more, pct)
                            pct < 0 -> stringResource(R.string.appusage_less, -pct)
                            else -> stringResource(R.string.appusage_same)
                        },
                        style = MaterialTheme.typography.labelLarge, color = SkyBright,
                    )
                }
            }
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Schedule, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun WeeklyTrend(days: List<Pair<String, Int>>, avgMin: Int) {
    val maxMin = (days.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.appusage_weekly_trend), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.appusage_avg, fmtDur(avgMin)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { i, (dow, min) ->
                    val today = i == days.lastIndex
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth(0.62f).fillMaxHeight((min.toFloat() / maxMin).coerceIn(0.04f, 1f))
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(if (today) SkyBright else SkyBright.copy(alpha = 0.3f)))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(dow, style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                            color = if (today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Breakdown(apps: List<AppUsageEntry>) {
    val maxApp = (apps.maxOfOrNull { it.min } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.appusage_breakdown), style = MaterialTheme.typography.headlineSmall)
        apps.forEach { e -> AppRow(e, maxApp) }
    }
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
private fun AppRow(e: AppUsageEntry, maxApp: Int) {
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
                    Text(e.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(fmtDur(e.min), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Box(Modifier.width(80.dp).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Box(Modifier.fillMaxWidth((e.min.toFloat() / maxApp).coerceIn(0.05f, 1f)).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                }
            }
        }
    }
}

@Composable
private fun LimitsCard(onClick: () -> Unit) {
    Surface(onClick = onClick, color = SkyTint, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.secondary)
                }
                Column {
                    Text(stringResource(R.string.appusage_limits_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.appusage_limits_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}
