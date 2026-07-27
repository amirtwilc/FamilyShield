package com.familyshield.mobile.kid

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.familyshield.mobile.Locales
import com.familyshield.mobile.R
import com.familyshield.mobile.net.Monitor
import com.familyshield.mobile.permissions.MonitoredPermissionsCard
import com.familyshield.mobile.permissions.toMonitoredPermissionItems
import com.familyshield.mobile.push.openNotificationPolicyAccessSettings
import com.familyshield.mobile.push.openUrgentNotificationSettings
import com.familyshield.mobile.push.ChatPushDestination
import com.familyshield.mobile.ui.OsmMap
import com.familyshield.mobile.ui.formatMessageDate
import com.familyshield.mobile.ui.formatMessageTime
import com.familyshield.mobile.ui.messageLocalDate
import com.familyshield.mobile.ui.theme.Danger
import com.familyshield.mobile.ui.theme.Green
import com.familyshield.mobile.ui.theme.GreenTint
import com.familyshield.mobile.ui.theme.Navy
import com.familyshield.mobile.ui.theme.NavyContainer
import com.familyshield.mobile.ui.theme.Orange
import com.familyshield.mobile.ui.theme.SkyBright
import com.familyshield.mobile.ui.theme.SkyTint
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

@Composable
fun KidApp(
    onBack: () -> Unit,
    chatDestination: ChatPushDestination? = null,
    onChatDestinationConsumed: (ChatPushDestination) -> Unit = {},
    onKidPaired: () -> Unit,
    onKidUnpaired: () -> Unit,
    vm: KidViewModel = viewModel(factory = KidViewModel.factory(LocalContext.current)),
) {
    val context = LocalContext.current
    var hadDeviceToken by remember { mutableStateOf(vm.deviceToken != null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var chatMonitor by remember { mutableStateOf<Monitor?>(null) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val backgroundLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestKidNotificationPermission(context, notificationLauncher)
    }
    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (Build.VERSION.SDK_INT >= 29 && hasKidForegroundLocationPermission(context) && !hasKidBackgroundLocationPermission(context)) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            requestKidNotificationPermission(context, notificationLauncher)
        }
    }
    LaunchedEffect(vm.deviceToken) {
        if (vm.deviceToken != null) {
            hadDeviceToken = true
            onKidPaired()
            requestKidMonitoringPermissions(context, foregroundLocationLauncher, backgroundLocationLauncher, notificationLauncher)
            startKidMonitoring(context)
        } else if (hadDeviceToken) {
            hadDeviceToken = false
            stopKidMonitoring(context)
            onKidUnpaired()
        }
    }
    LaunchedEffect(chatDestination?.key, vm.deviceToken, vm.monitors) {
        if (vm.deviceToken == null) return@LaunchedEffect
        val destination = chatDestination ?: return@LaunchedEffect
        val monitor = vm.monitors.firstOrNull { it.parentId == destination.parentId }
        if (monitor == null) {
            vm.refreshMonitoring()
        } else {
            settingsOpen = false
            chatMonitor = monitor
            onChatDestinationConsumed(destination)
        }
    }
    if (settingsOpen) {
        KidSettingsScreen(onBack = { settingsOpen = false })
    } else if (vm.deviceToken == null) {
        ConnectScreen(vm, onBack, onSettings = { settingsOpen = true })
    } else {
        if (chatMonitor != null) KidChatScreen(vm, monitor = chatMonitor!!, onBack = { chatMonitor = null })
        else DeviceDashboard(vm, onSettings = { settingsOpen = true }, onChat = { chatMonitor = it })
    }
}

private fun requestKidMonitoringPermissions(
    context: Context,
    foregroundLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    backgroundLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    notificationLauncher: androidx.activity.result.ActivityResultLauncher<String>,
) {
    if (!hasKidForegroundLocationPermission(context)) {
        foregroundLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        return
    }
    if (Build.VERSION.SDK_INT >= 29 && !hasKidBackgroundLocationPermission(context)) {
        backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        return
    }
    requestKidNotificationPermission(context, notificationLauncher)
}

private fun requestKidNotificationPermission(
    context: Context,
    notificationLauncher: androidx.activity.result.ActivityResultLauncher<String>,
) {
    if (Build.VERSION.SDK_INT >= 33 && !hasKidNotificationPermission(context)) {
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/* ------------------------------- Connect to Parent ------------------------------- */

@Composable
private fun ConnectScreen(vm: KidViewModel, onBack: () -> Unit, onSettings: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var parentName by remember { mutableStateOf("") }
    var disclosureAccepted by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Hero: logo + device↔family connection visual
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                Icon(Icons.Filled.Settings, stringResource(R.string.cd_settings), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(NavyContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Shield, null, tint = Color(0xFF8690EE))
                    }
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(36.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PhoneAndroid, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(30.dp))
                    }
                    PulseDots()
                    Box(Modifier.size(80.dp).rotate(3f).clip(RoundedCornerShape(24.dp)).background(Navy), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.FamilyRestroom, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        // Glass card with the code entry
        Surface(
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp,
            modifier = Modifier.offset(y = (-12).dp).padding(horizontal = 16.dp).widthIn(max = 460.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.kid_connect_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.kid_connect_body),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(Modifier.height(24.dp))
                MonitoringDisclosure(checked = disclosureAccepted, onCheckedChange = { disclosureAccepted = it })
                Spacer(Modifier.height(18.dp))
                ParentNameInput(parentName, { parentName = it })
                Spacer(Modifier.height(18.dp))
                CodeBoxes(code) { code = it.filter { c -> c.isDigit() }.take(6) }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { vm.pair(code, "android", parentName) },
                    enabled = disclosureAccepted && parentName.isNotBlank() && code.length == 6 && !vm.busy,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    else {
                        Text(stringResource(R.string.kid_connect_action), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(20.dp))
                    }
                }
                TextButton(onClick = { }) { Text(stringResource(R.string.kid_find_code), color = MaterialTheme.colorScheme.secondary) }
                vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        // Info cards
        Row(Modifier.padding(horizontal = 16.dp).widthIn(max = 460.dp).fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(Modifier.weight(1f), Icons.Filled.VerifiedUser, stringResource(R.string.kid_privacy_label), stringResource(R.string.kid_privacy_title), GreenTint, Green)
            InfoCard(Modifier.weight(1f), Icons.Filled.Bolt, stringResource(R.string.kid_sync_label), stringResource(R.string.kid_sync_title), SkyTint, MaterialTheme.colorScheme.secondary)
        }

        Spacer(Modifier.height(24.dp))
        // Waiting footer
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PingDot(MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.kid_waiting), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.kid_version), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CodeBoxes(code: String, onChange: (String) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BasicTextField(
            value = code, onValueChange = onChange, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            textStyle = TextStyle(color = Color.Transparent, textDirection = TextDirection.Ltr),
            decorationBox = {
                // Weighted boxes so the 6-digit field fits any phone width (narrow → wide).
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(6) { i ->
                        val ch = code.getOrNull(i)?.toString() ?: ""
                        val active = i == code.length
                        Box(
                            Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(if (active) 2.dp else 1.dp,
                                    if (active) SkyBright else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(ch, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ParentNameInput(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.take(80)) },
            label = { Text(stringResource(R.string.kid_parent_name_label)) },
            supportingText = {
                Text(
                    stringResource(
                        if (value.isBlank()) R.string.kid_parent_name_required else R.string.kid_parent_name_helper,
                    ),
                )
            },
            isError = value.isBlank(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.kid_parent_name_mom, R.string.kid_parent_name_dad).forEach { res ->
                val label = stringResource(res)
                AssistChip(
                    onClick = { onChange(label) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun InfoCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, title: String, bg: Color, fg: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = bg) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = fg)
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MonitoringDisclosure(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Spacer(Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.kid_monitoring_disclosure_title),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.kid_monitoring_disclosure_body),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PulseDots() {
    val t = rememberInfiniteTransition(label = "dots")
    val a by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "a")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.graphicsLayer { alpha = a }) {
        repeat(3) { Box(Modifier.size(8.dp).clip(CircleShape).background(SkyBright)) }
    }
}

@Composable
private fun PingDot(color: Color) {
    val t = rememberInfiniteTransition(label = "ping")
    val s by t.animateFloat(1f, 2.2f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "s")
    val a by t.animateFloat(0.6f, 0f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "a")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(14.dp)) {
        Box(Modifier.size(12.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = a }.clip(CircleShape).background(color))
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
    }
}

/* ----------------------------- Monitored device ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDashboard(vm: KidViewModel, onSettings: () -> Unit, onChat: (Monitor) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    var addParentOpen by remember { mutableStateOf(false) }
    var pendingUnpair by remember { mutableStateOf<Monitor?>(null) }
    var editingMonitor by remember { mutableStateOf<Monitor?>(null) }
    var monitoringStatus by remember { mutableStateOf(backgroundMonitoringStatus(context)) }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it); vm.clearError() } }
    LaunchedEffect(Unit) {
        vm.refreshMonitoring()
        vm.refreshAppUsageAccess(context)
        vm.refreshSosState()
        while (true) {
            vm.refreshTelemetry(context)
            vm.refreshSosState()
            monitoringStatus = backgroundMonitoringStatus(context)
            delay(30_000)
        }
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshAppUsageAccess(context)
                monitoringStatus = backgroundMonitoringStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (addParentOpen) AddParentDialog(vm, onDismiss = { addParentOpen = false })
    editingMonitor?.let { monitor ->
        EditParentNameDialog(
            initialName = monitor.childFacingName,
            onDismiss = { editingMonitor = null },
            onSave = { name ->
                vm.updateMonitorName(monitor.parentId, name)
                editingMonitor = null
            },
            saving = vm.busy,
        )
    }
    pendingUnpair?.let { monitor ->
        AlertDialog(
            onDismissRequest = { pendingUnpair = null },
            title = { Text(stringResource(R.string.kid_unpair_confirm_title)) },
            text = { Text(stringResource(R.string.kid_unpair_confirm_body, monitor.childFacingName)) },
            confirmButton = {
                TextButton(onClick = { vm.removeMonitor(monitor.parentId); pendingUnpair = null }) {
                    Text(stringResource(R.string.kid_unpair), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnpair = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kid_my_device), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.cd_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 184.dp)
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val currentLat = vm.lat
            val currentLng = vm.lng
            KidSosActiveCard(vm)
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.kid_monitored_by), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        FilledTonalIconButton(onClick = { addParentOpen = true }) {
                            Icon(Icons.Filled.PersonAdd, stringResource(R.string.kid_pair_another_parent))
                        }
                    }
                    if (vm.monitors.isEmpty()) {
                        Text(stringResource(R.string.kid_monitors_loading), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        vm.monitors.forEach { monitor ->
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                monitor.childFacingName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clip(MaterialTheme.shapes.small)
                                                    .clickable { editingMonitor = monitor }
                                                    .padding(vertical = 2.dp),
                                            )
                                            Text(
                                                monitor.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        FilledTonalIconButton(onClick = { onChat(monitor) }) {
                                            Icon(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.kid_chat_title))
                                        }
                                        IconButton(onClick = { pendingUnpair = monitor }) {
                                            Icon(Icons.Filled.Delete, stringResource(R.string.kid_unpair), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                if (currentLat != null && currentLng != null) {
                    Box {
                        OsmMap(currentLat, currentLng, Modifier.fillMaxWidth().height(280.dp).clip(MaterialTheme.shapes.large),
                            description = stringResource(R.string.cd_kid_map))
                        Surface(color = Color.White.copy(alpha = 0.92f), shape = CircleShape, shadowElevation = 3.dp,
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.kid_current_location, "%.4f".format(currentLat), "%.4f".format(currentLng)),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp))
                            Text(
                                stringResource(R.string.kid_location_waiting),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Column(Modifier.padding(20.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.telemetry), style = MaterialTheme.typography.titleMedium)
                    BatteryGauge(vm.battery, vm.charging)
                    TelemetryRow(
                        Icons.Filled.LocationOn,
                        stringResource(R.string.location_update),
                        if (currentLat != null && currentLng != null) {
                            stringResource(R.string.kid_current_location, "%.4f".format(currentLat), "%.4f".format(currentLng))
                        } else {
                            stringResource(R.string.kid_location_pending)
                        },
                    )
                    TelemetryRow(Icons.Filled.Bolt, stringResource(R.string.status), stringResource(R.string.kid_auto_updates))
                }
            }
            PermissionsCard(
                status = monitoringStatus,
                onManualConfirmationChange = { confirmed ->
                    setManufacturerBackgroundAccessConfirmed(context, confirmed)
                    monitoringStatus = backgroundMonitoringStatus(context)
                },
            )
        }
            FloatingSosButton(
                active = vm.sosState.active,
                busy = vm.sosBusy,
                onStart = { vm.startSos(context) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun KidSosActiveCard(vm: KidViewModel) {
    val context = LocalContext.current
    if (!vm.sosState.active) return
    Surface(
        shape = MaterialTheme.shapes.large,
        color = Danger,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Warning, null, tint = Color.White)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.kid_sos_active_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.kid_sos_active_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }
            Button(
                onClick = { vm.endSos(context) },
                enabled = !vm.sosBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Danger),
            ) {
                Text(stringResource(R.string.kid_sos_end_action), fontWeight = FontWeight.SemiBold)
            }
            if (vm.sosBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White)
            }
        }
    }
}

private const val SOS_HOLD_DURATION_MS = 3_000L

@Composable
private fun FloatingSosButton(
    active: Boolean,
    busy: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableStateOf(0f) }
    var completed by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val instruction = stringResource(R.string.kid_sos_press_instruction)
    val sent = stringResource(R.string.kid_sos_alerts_sent)

    LaunchedEffect(active, busy) {
        if (active) {
            completed = true
            progress = 1f
        } else if (!busy) {
            completed = false
            progress = 0f
        }
    }

    val confirmed = active || completed
    val buttonColor = if (confirmed) Green else Danger
    val buttonGradient = if (confirmed) {
        Brush.verticalGradient(listOf(Green.copy(alpha = 0.95f), Green))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFD93343), Color(0xFF92001F)))
    }
    val semanticLabel = if (confirmed) sent else instruction

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(148.dp)
                .semantics {
                    contentDescription = semanticLabel
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            SosProgressRing(progress = if (confirmed) 1f else progress, color = buttonColor)
            Box(
                Modifier
                    .size(104.dp)
                    .shadow(18.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(buttonGradient)
                    .border(4.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .pointerInput(active, busy) {
                        detectTapGestures(
                            onPress = {
                            if (active || busy) {
                                    tryAwaitRelease()
                                    return@detectTapGestures
                            }
                            coroutineScope {
                                var triggered = false
                                val startedAt = SystemClock.uptimeMillis()
                                val job = launch {
                                    while (isActive) {
                                        val elapsed = SystemClock.uptimeMillis() - startedAt
                                        val nextProgress = (elapsed.toFloat() / SOS_HOLD_DURATION_MS).coerceIn(0f, 1f)
                                        progress = nextProgress
                                        if (nextProgress >= 1f) {
                                            triggered = true
                                            completed = true
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onStart()
                                            break
                                        }
                                        delay(32)
                                    }
                                }
                                    tryAwaitRelease()
                                job.cancel()
                                if (!triggered) progress = 0f
                            }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (confirmed) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(48.dp))
                } else if (progress > 0f) {
                    Text(
                        ((SOS_HOLD_DURATION_MS - (progress * SOS_HOLD_DURATION_MS)).toInt() / 1000 + 1)
                            .coerceIn(1, 3)
                            .toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                } else {
                    Text(
                        stringResource(R.string.kid_sos_title),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
        Surface(
            color = if (confirmed) GreenTint.copy(alpha = 0.96f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = if (confirmed) Green else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = CircleShape,
            shadowElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        ) {
            Text(
                if (confirmed) sent else instruction,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SosProgressRing(progress: Float, color: Color) {
    Canvas(Modifier.size(142.dp)) {
        val strokeWidth = 5.dp.toPx()
        val inset = strokeWidth / 2f
        drawCircle(
            color = color.copy(alpha = 0.14f),
            radius = (size.minDimension - strokeWidth) / 2f,
            style = Stroke(width = strokeWidth),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KidSettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var monitoringStatus by remember { mutableStateOf(backgroundMonitoringStatus(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) monitoringStatus = backgroundMonitoringStatus(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kid_settings_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PermissionsCard(
                status = monitoringStatus,
                onManualConfirmationChange = { confirmed ->
                    setManufacturerBackgroundAccessConfirmed(context, confirmed)
                    monitoringStatus = backgroundMonitoringStatus(context)
                },
            )
            KidLanguageCard()
        }
    }
}

@Composable
private fun PermissionsCard(
    status: BackgroundMonitoringStatus,
    onManualConfirmationChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    MonitoredPermissionsCard(
        ready = status.ready,
        body = stringResource(R.string.kid_permissions_body),
        items = status.toMonitoredPermissionItems(
            onLocationClick = { openAppPermissionSettings(context) },
            onNotificationsClick = { openAppNotificationSettings(context) },
            onUrgentNotificationsClick = { openUrgentNotificationSettings(context) },
            onDndBypassClick = { openNotificationPolicyAccessSettings(context) },
            onBatteryClick = { openBatteryOptimizationSettings(context) },
            onAppUsageClick = { context.startActivity(AppUsageTelemetry.usageAccessIntent()) },
            onManufacturerBackgroundClick = { openManufacturerBackgroundSettings(context) },
            onManualConfirmationChange = onManualConfirmationChange,
        ),
    )
}

@Composable
private fun KidLanguageCard() {
    val context = LocalContext.current
    val current = Locales.currentTag
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Language, null, tint = MaterialTheme.colorScheme.secondary)
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            KidLanguageOption(stringResource(R.string.lang_system), current == "") { Locales.apply(context, "") }
            KidLanguageOption(stringResource(R.string.lang_english), current == "en") { Locales.apply(context, "en") }
            KidLanguageOption(stringResource(R.string.lang_hebrew), current == "he") { Locales.apply(context, "he") }
        }
    }
}

@Composable
private fun KidLanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BatteryGauge(level: Int?, charging: Boolean?) {
    if (level == null) {
        Text(
            stringResource(R.string.kid_battery_pending),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val color = when {
        level <= 15 -> MaterialTheme.colorScheme.error
        level <= 35 -> Orange
        else -> Green
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$level%", style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
        if (charging == true) Icon(Icons.Filled.Bolt, stringResource(R.string.charging), tint = Orange, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).height(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(Modifier.fillMaxWidth(level / 100f).fillMaxHeight().clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun TelemetryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun AddParentDialog(vm: KidViewModel, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var parentName by remember { mutableStateOf("") }
    var disclosureAccepted by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kid_pair_another_parent)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MonitoringDisclosure(checked = disclosureAccepted, onCheckedChange = { disclosureAccepted = it })
                ParentNameInput(parentName, { parentName = it })
                CodeBoxes(code) { code = it.filter { c -> c.isDigit() }.take(6) }
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.addParent(code, "android", parentName); onDismiss() },
                enabled = disclosureAccepted && parentName.isNotBlank() && code.length == 6 && !vm.busy,
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun EditParentNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    saving: Boolean,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kid_edit_parent_name_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.kid_edit_parent_name_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ParentNameInput(name, { name = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) },
                enabled = name.isNotBlank() && !saving,
            ) { Text(stringResource(R.string.kid_edit_parent_name_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/* ----------------------------------- Chat ----------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KidChatScreen(vm: KidViewModel, monitor: Monitor, onBack: () -> Unit) {
    DisposableEffect(monitor.parentId) { vm.startChat(monitor.parentId); onDispose { vm.stopChat() } }
    LaunchedEffect(vm.error) { vm.error?.let { vm.clearError() } }
    BackHandler(onBack = onBack)
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.chatMessages.size) {
        if (vm.chatMessages.isNotEmpty()) listState.animateScrollToItem(vm.chatMessages.size - 1)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kid_chat_title_with, monitor.childFacingName), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (vm.chatMessages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.kid_chat_empty), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                    itemsIndexed(vm.chatMessages, key = { _, m -> m.id }) { index, m ->
                        val currentDate = messageLocalDate(m.createdAt)
                        val previousDate = vm.chatMessages.getOrNull(index - 1)?.createdAt?.let { messageLocalDate(it) }
                        if (index == 0 || currentDate != previousDate) KidDateChip(kidChatDateLabel(m.createdAt))
                        KidBubble(m.sender == "child", m.body, m.createdAt, m.readAt != null)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.qr_im_safe, R.string.qr_on_my_way, R.string.qr_pick_me_up).forEach { res ->
                    val q = stringResource(res); KidQuickReplyChip(q) { vm.sendChat(q) }
                }
            }
            KidChatInput(input, { input = it }, vm.sending) { if (input.isNotBlank()) { vm.sendChat(input); input = "" } }
        }
    }
}

@Composable
private fun kidChatDateLabel(createdAt: String): String {
    val date = messageLocalDate(createdAt)
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.chat_today)
        today.minusDays(1) -> stringResource(R.string.chat_yesterday)
        null -> formatMessageDate(createdAt)
        else -> formatMessageDate(createdAt, locale = Locale.getDefault())
    }
}

@Composable
private fun KidDateChip(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = CircleShape) {
            Text(text, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KidBubble(mine: Boolean, body: String, createdAt: String, read: Boolean) {
    val bg = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (mine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(16.dp, 16.dp, if (mine) 4.dp else 16.dp, if (mine) 16.dp else 4.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Surface(color = bg, shape = shape, shadowElevation = if (mine) 3.dp else 1.dp, modifier = Modifier.widthIn(max = 300.dp)) {
            Text(body, color = fg, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        Row(
            Modifier.padding(top = 4.dp, start = 6.dp, end = 6.dp).heightIn(min = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(formatMessageTime(createdAt), style = MaterialTheme.typography.labelMedium, color = metaColor)
            if (mine) Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(14.dp),
                tint = if (read) MaterialTheme.colorScheme.secondary else metaColor)
        }
    }
}

@Composable
private fun KidQuickReplyChip(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun KidChatInput(value: String, onChange: (String) -> Unit, sending: Boolean, onSend: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value, onChange, modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_hint)) }, shape = CircleShape, maxLines = 4)
            FilledIconButton(onClick = onSend, enabled = value.isNotBlank() && !sending, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.cd_send))
            }
        }
    }
}
