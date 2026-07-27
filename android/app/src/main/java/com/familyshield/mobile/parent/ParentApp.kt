package com.familyshield.mobile.parent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.familyshield.mobile.BuildConfig
import com.familyshield.mobile.R
import com.familyshield.mobile.Locales
import com.familyshield.mobile.net.Alert
import com.familyshield.mobile.net.Child
import com.familyshield.mobile.net.CurrentLocation
import com.familyshield.mobile.net.Device
import com.familyshield.mobile.net.FrequentRoute
import com.familyshield.mobile.net.Geo
import com.familyshield.mobile.net.PermissionStatus
import com.familyshield.mobile.net.RoutePoint
import com.familyshield.mobile.net.SosState
import com.familyshield.mobile.net.Zone
import com.familyshield.mobile.permissions.MonitoredPermissionsContent
import com.familyshield.mobile.permissions.PERMISSION_DND_BYPASS
import com.familyshield.mobile.permissions.PERMISSION_URGENT_NOTIFICATIONS
import com.familyshield.mobile.permissions.PermissionStatusIcon
import com.familyshield.mobile.permissions.requiredPermissionsSatisfied
import com.familyshield.mobile.permissions.toMonitoredPermissionItems
import com.familyshield.mobile.push.ChatPushDestination
import com.familyshield.mobile.push.hasAppNotificationsEnabled
import com.familyshield.mobile.push.hasUrgentDndBypass
import com.familyshield.mobile.push.hasUrgentNotificationsEnabled
import com.familyshield.mobile.push.openAppNotificationSettings
import com.familyshield.mobile.push.openNotificationPolicyAccessSettings
import com.familyshield.mobile.push.openUrgentNotificationSettings
import com.familyshield.mobile.ui.Avatar
import com.familyshield.mobile.ui.FamilyMapMarker
import com.familyshield.mobile.ui.GradientButton
import com.familyshield.mobile.ui.MapMarker
import com.familyshield.mobile.ui.MapPoint
import com.familyshield.mobile.ui.OsmFamilyMap
import com.familyshield.mobile.ui.OsmLiveFamilyMap
import com.familyshield.mobile.ui.OsmMapZones
import com.familyshield.mobile.ui.PulsingDot
import com.familyshield.mobile.ui.childAvatarOptions
import com.familyshield.mobile.ui.FullScreenFamilyMap
import com.familyshield.mobile.ui.FullScreenMap
import com.familyshield.mobile.ui.FullScreenRouteMap
import com.familyshield.mobile.ui.formatMessageDate
import com.familyshield.mobile.ui.formatMessageTime
import com.familyshield.mobile.ui.messageLocalDate
import com.familyshield.mobile.ui.theme.BrandGradient
import com.familyshield.mobile.ui.theme.Danger
import com.familyshield.mobile.ui.theme.Green
import com.familyshield.mobile.ui.theme.Navy
import com.familyshield.mobile.ui.theme.Orange
import com.familyshield.mobile.ui.theme.SkyBright
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ParentApp(
    onKidDevice: () -> Unit,
    chatDestination: ChatPushDestination? = null,
    onChatDestinationConsumed: (ChatPushDestination) -> Unit = {},
    vm: ParentViewModel = viewModel(factory = ParentViewModel.factory(LocalContext.current)),
) {
    if (vm.token == null) {
        LoginScreen(vm, onKidDevice)
    } else {
        ParentLockGate(vm) {
            ParentShell(vm, chatDestination, onChatDestinationConsumed)
        }
    }
}

@Composable
private fun ParentLockGate(vm: ParentViewModel, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivityResultRegistryOwner.current as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var unlocked by remember(vm.biometricLock) { mutableStateOf(!vm.biometricLock) }
    var promptActive by remember { mutableStateOf(false) }
    var lastStoppedAt by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun requestUnlock() {
        if (!vm.biometricLock || unlocked || promptActive) return
        when (val availability = parentBiometricAvailability(context, activity)) {
            is ParentBiometricAvailability.Available -> {
                promptActive = true
                showParentBiometricPrompt(
                    availability.activity,
                    onSuccess = {
                        unlocked = true
                        promptActive = false
                        message = null
                    },
                    onCancelOrError = {
                        promptActive = false
                    },
                )
            }
            ParentBiometricAvailability.NotEnrolled -> {
                message = context.getString(R.string.parent_lock_setup_required)
                openDeviceSecuritySettings(context)
            }
            ParentBiometricAvailability.Unavailable -> {
                message = context.getString(R.string.parent_lock_unavailable)
            }
        }
    }

    LaunchedEffect(vm.biometricLock, unlocked) {
        if (vm.biometricLock && !unlocked) requestUnlock()
    }
    DisposableEffect(lifecycleOwner, vm.biometricLock) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> lastStoppedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    val stoppedAt = lastStoppedAt
                    if (vm.biometricLock && stoppedAt != null &&
                        System.currentTimeMillis() - stoppedAt > PARENT_LOCK_GRACE_MS
                    ) {
                        unlocked = false
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!vm.biometricLock || unlocked) {
        content()
    } else {
        ParentLockedScreen(
            message = message,
            onUnlock = { requestUnlock() },
            onLogout = { vm.logout() },
        )
    }
}

private const val PARENT_LOCK_GRACE_MS = 30_000L

@Composable
private fun ParentLockedScreen(message: String?, onUnlock: () -> Unit, onLogout: () -> Unit) {
    BackHandler(enabled = true) {}
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                Icons.Filled.Fingerprint,
                null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(18.dp).size(34.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.parent_lock_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.parent_lock_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        GradientButton(
            text = stringResource(R.string.parent_lock_unlock),
            onClick = onUnlock,
            modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp),
        )
        TextButton(onClick = onLogout, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.action_logout))
        }
    }
}

/* ----------------------------------- Login ----------------------------------- */

@Composable
private fun LoginScreen(vm: ParentViewModel, onKidDevice: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState()).imePadding()) {
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(BrandGradient)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Shield, null, tint = Color.White)
                    Text("FamilyShield", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(if (register) R.string.login_title_register else R.string.login_title_welcome),
                    style = MaterialTheme.typography.displaySmall, color = Color.White)
            }
        }
        Column(Modifier.offset(y = (-20).dp).padding(horizontal = 20.dp).widthIn(max = 480.dp)) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.field_email)) }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.MailOutline, null) }, shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.field_password)) }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Lock, null) }, shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    stringResource(if (showPassword) R.string.cd_hide_password else R.string.cd_show_password))
                            }
                        }, modifier = Modifier.fillMaxWidth())
                    GradientButton(stringResource(if (register) R.string.action_create_account else R.string.action_sign_in),
                        onClick = { vm.authenticate(email.trim(), password, register) },
                        enabled = email.isNotBlank() && password.isNotBlank(), loading = vm.busy,
                        modifier = Modifier.fillMaxWidth())
                    vm.error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }

                    if (BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()) {
                        val googleSignIn = rememberGoogleSignIn(vm)
                        OutlinedButton(onClick = googleSignIn, enabled = !vm.busy, shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            Icon(Icons.Filled.AccountCircle, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.google_signin), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { register = !register; vm.clearError() }) {
                    Text(stringResource(if (register) R.string.login_have_account else R.string.login_new_here))
                }
                TextButton(onClick = onKidDevice) {
                    Text(stringResource(R.string.login_setup_kid), color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

/* ------------------------------- Bottom-nav shell ------------------------------- */

private enum class Tab(@androidx.annotation.StringRes val labelRes: Int, val icon: ImageVector) {
    Dashboard(R.string.nav_dashboard, Icons.Filled.Dashboard),
    Chat(R.string.nav_chat, Icons.AutoMirrored.Filled.Chat),
    Map(R.string.nav_map, Icons.Filled.Map),
    History(R.string.nav_history, Icons.Filled.History),
    Zones(R.string.nav_zones, Icons.Filled.Place),
}

@Composable
private fun ParentShell(
    vm: ParentViewModel,
    chatDestination: ChatPushDestination?,
    onChatDestinationConsumed: (ChatPushDestination) -> Unit,
) {
    LaunchedEffect(Unit) { vm.refreshChildren() }
    // Real-time location tracking while the parent app is open.
    DisposableEffect(Unit) {
        vm.startLive()
        onDispose { vm.stopLive() }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it); vm.clearError() } }
    val scope = rememberCoroutineScope()
    val tabs = Tab.entries
    var tab by remember { mutableStateOf(Tab.Dashboard) }
    var dragTarget by remember { mutableStateOf<Tab?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val sectionOffset = remember { Animatable(0f) }
    var showSettings by remember { mutableStateOf(false) }
    var appUsageFor by remember { mutableStateOf<String?>(null) }
    var showAllAlerts by remember { mutableStateOf(false) }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val screenWidthPx = with(density) { screenWidthDp.dp.toPx() }
    val navLabelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = when {
            screenWidthDp < 320 -> 7.sp
            screenWidthDp < 360 -> 8.sp
            else -> MaterialTheme.typography.labelSmall.fontSize
        },
        lineHeight = when {
            screenWidthDp < 320 -> 9.sp
            screenWidthDp < 360 -> 10.sp
            else -> MaterialTheme.typography.labelSmall.lineHeight
        },
        letterSpacing = 0.sp,
    )
    val openSettings = { showSettings = true }
    fun openTab(next: Tab) {
        if (next == tab) return
        scope.launch {
            val widthPx = screenWidthPx.takeIf { it > 0f } ?: return@launch
            sectionOffset.stop()
            val baseOffset = sectionTargetBaseOffset(tab, next, widthPx, layoutDirection)
            dragging = false
            dragOffsetPx = 0f
            dragTarget = next
            sectionOffset.snapTo(0f)
            sectionOffset.animateTo(-baseOffset, tween(durationMillis = 240))
            tab = next
            dragTarget = null
            sectionOffset.snapTo(0f)
        }
    }
    // Stop chat polling whenever the Chat tab isn't the one on screen.
    LaunchedEffect(tab, showSettings, appUsageFor, showAllAlerts, chatDestination?.key) {
        if (chatDestination == null && (tab != Tab.Chat || showSettings || appUsageFor != null || showAllAlerts)) {
            vm.closeChat()
        }
    }
    LaunchedEffect(chatDestination?.key, vm.token) {
        val destination = chatDestination ?: return@LaunchedEffect
        showSettings = false
        appUsageFor = null
        showAllAlerts = false
        vm.openChat(destination.childId)
        openTab(Tab.Chat)
        onChatDestinationConsumed(destination)
    }

    if (showSettings) {
        SettingsScreen(vm, onBack = { showSettings = false }, onOpenZones = { showSettings = false; openTab(Tab.Zones) })
        return
    }
    if (showAllAlerts) {
        AllAlertsScreen(vm, onBack = { showAllAlerts = false })
        return
    }
    appUsageFor?.let { id ->
        AppUsageScreen(vm, id, onBack = { appUsageFor = null })
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                tabs.forEach { t ->
                    val label = stringResource(t.labelRes)
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { openTab(t) },
                        icon = { Icon(t.icon, label) },
                        label = {
                            Text(
                                label,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                style = navLabelStyle,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        val logicalOffset = if (dragging) dragOffsetPx else sectionOffset.value
        val visibleOffset = sectionDisplayOffset(logicalOffset, layoutDirection)
        Box(
            Modifier.padding(pad)
                .fillMaxSize()
                .clipToBounds()
                .draggable(
                    enabled = tab != Tab.Map,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (screenWidthPx <= 0f) return@rememberDraggableState
                        val rawOffset = dragOffsetPx + delta
                        val target = dragTarget ?: sectionDragTarget(tab, rawOffset, layoutDirection)
                        if (target == null) {
                            dragTarget = null
                            dragOffsetPx = 0f
                        } else {
                            dragTarget = target
                            val dismissOffset = -sectionTargetBaseOffset(tab, target, screenWidthPx, layoutDirection)
                            dragOffsetPx = rawOffset.coerceIn(min(0f, dismissOffset), max(0f, dismissOffset))
                        }
                    },
                    onDragStarted = {
                        scope.launch {
                            sectionOffset.stop()
                            sectionOffset.snapTo(0f)
                        }
                        dragging = true
                        dragTarget = null
                        dragOffsetPx = 0f
                    },
                    onDragStopped = {
                        val target = dragTarget
                        val endOffset = dragOffsetPx
                        dragging = false
                        scope.launch {
                            sectionOffset.stop()
                            sectionOffset.snapTo(endOffset)
                            if (target != null && abs(endOffset) > screenWidthPx * 0.28f) {
                                val baseOffset = sectionTargetBaseOffset(tab, target, screenWidthPx, layoutDirection)
                                sectionOffset.animateTo(-baseOffset, tween(durationMillis = 220))
                                tab = target
                                dragTarget = null
                                dragOffsetPx = 0f
                                sectionOffset.snapTo(0f)
                            } else {
                                sectionOffset.animateTo(0f, tween(durationMillis = 220))
                                dragTarget = null
                                dragOffsetPx = 0f
                            }
                        }
                    },
                ),
        ) {
            val hiddenOffsetWidth = screenWidthPx.takeIf { it > 0f } ?: 10_000f
            tabs.forEach { pageTab ->
                key(pageTab) {
                    val pageOffset = when {
                        pageTab == tab -> visibleOffset
                        pageTab == dragTarget -> {
                            val baseOffset = sectionTargetBaseOffset(tab, pageTab, screenWidthPx, layoutDirection)
                            sectionDisplayOffset(baseOffset, layoutDirection) + visibleOffset
                        }
                        else -> {
                            val hiddenDirection = if (pageTab.ordinal > tab.ordinal) 1f else -1f
                            sectionDisplayOffset(hiddenDirection * hiddenOffsetWidth * 2f, layoutDirection)
                        }
                    }
                    SectionPage(
                        tab = pageTab,
                        active = pageTab == tab,
                        modifier = Modifier.offset { IntOffset(pageOffset.roundToInt(), 0) },
                        vm = vm,
                        snackbar = snackbar,
                        onTimeline = { openTab(Tab.History) },
                        onAlerts = { showAllAlerts = true },
                        onSettings = openSettings,
                        onAppUsage = { id -> appUsageFor = id },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionPage(
    tab: Tab,
    active: Boolean,
    modifier: Modifier,
    vm: ParentViewModel,
    snackbar: SnackbarHostState,
    onTimeline: () -> Unit,
    onAlerts: () -> Unit,
    onSettings: () -> Unit,
    onAppUsage: (String) -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        when (tab) {
            Tab.Dashboard -> DashboardTab(vm, onTimeline = onTimeline, onAlerts = onAlerts, onSettings = onSettings,
                    onAppUsage = onAppUsage, snackbar = snackbar)
            Tab.Chat -> ChatTab(vm, onSettings = onSettings, active = active)
            Tab.Map -> MapTab(vm, snackbar, active = active)
            Tab.History -> HistoryTab(vm, onSettings = onSettings)
            Tab.Zones -> ZonesTab(vm, onSettings = onSettings)
        }
    }
}

private fun sectionDragTarget(current: Tab, offsetPx: Float, layoutDirection: LayoutDirection): Tab? {
    if (offsetPx == 0f) return null
    val movingForward = if (layoutDirection == LayoutDirection.Ltr) offsetPx < 0f else offsetPx > 0f
    val targetIndex = current.ordinal + if (movingForward) 1 else -1
    return Tab.entries.getOrNull(targetIndex)
}

private fun sectionTargetBaseOffset(current: Tab, target: Tab, widthPx: Float, layoutDirection: LayoutDirection): Float {
    val targetAfterCurrent = target.ordinal > current.ordinal
    return when (layoutDirection) {
        LayoutDirection.Ltr -> if (targetAfterCurrent) widthPx else -widthPx
        LayoutDirection.Rtl -> if (targetAfterCurrent) -widthPx else widthPx
    }
}

private fun sectionDisplayOffset(offsetPx: Float, layoutDirection: LayoutDirection): Float =
    if (layoutDirection == LayoutDirection.Rtl) -offsetPx else offsetPx

@Composable
private fun ParentTopBar(vm: ParentViewModel, onSettings: () -> Unit) {
    val child = vm.selected
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (child != null) Avatar(child.displayName, 40.dp, online = child.primaryDevice()?.isConnected() == true, avatar = child.avatar)
            else Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.cd_settings), tint = MaterialTheme.colorScheme.primary) }
    }
}

/* --------------------------------- Settings --------------------------------- */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(vm: ParentViewModel, onBack: () -> Unit, onOpenZones: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val activity = LocalActivityResultRegistryOwner.current as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsEnabled by remember { mutableStateOf(hasAppNotificationsEnabled(context)) }
    var addOpen by remember { mutableStateOf(false) }
    var showAddLimit by remember { mutableStateOf(false) }
    var permissionsFor by remember { mutableStateOf<Child?>(null) }
    var rename by remember { mutableStateOf<Child?>(null) }
    var pendingDelete by remember { mutableStateOf<Child?>(null) }
    var lockMessage by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val activeCount = vm.children.count { it.primaryDevice()?.isConnected() == true }

    LaunchedEffect(lockMessage) {
        lockMessage?.let {
            snackbar.showSnackbar(it)
            lockMessage = null
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = hasAppNotificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {

                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.manage_profiles), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.manage_profiles_body),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.your_children), style = MaterialTheme.typography.titleMedium)
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = CircleShape) {
                        Text(stringResource(R.string.n_active, activeCount), Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
                vm.children.forEach { c ->
                    ChildSettingRow(
                        child = c,
                        pairingCode = vm.pairingCode.takeIf { c.id == vm.selectedId },
                        onEdit = { rename = c },
                        onPermissions = { permissionsFor = c },
                        onGenerateCode = {
                            vm.select(c.id)
                            vm.generateCode()
                        },
                    )
                }

                Button(onClick = {
                    showAddLimit = vm.children.size >= 5
                    addOpen = true
                }, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                    Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_child), fontWeight = FontWeight.SemiBold)
                }

                // Privacy lock
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Fingerprint, null, tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.privacy_lock), style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(stringResource(R.string.privacy_lock_body), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                        }
                        Switch(
                            checked = vm.biometricLock,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    vm.updateBiometricLock(false)
                                    return@Switch
                                }
                                when (val availability = parentBiometricAvailability(context, activity)) {
                                    is ParentBiometricAvailability.Available -> {
                                        showParentBiometricPrompt(
                                            availability.activity,
                                            onSuccess = { vm.updateBiometricLock(true) },
                                            onCancelOrError = {},
                                        )
                                    }
                                    ParentBiometricAvailability.NotEnrolled -> {
                                        lockMessage = context.getString(R.string.parent_lock_setup_required)
                                        openDeviceSecuritySettings(context)
                                    }
                                    ParentBiometricAvailability.Unavailable -> {
                                        lockMessage = context.getString(R.string.parent_lock_unavailable)
                                    }
                                }
                            },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingMini(Modifier.weight(1f), Icons.Filled.Place, stringResource(R.string.settings_zones), stringResource(R.string.settings_zones_body, vm.zones.size), MaterialTheme.colorScheme.secondary, onOpenZones)
                    SettingMini(Modifier.weight(1f), Icons.Filled.NotificationsActive, stringResource(R.string.settings_alerts),
                        stringResource(if (notificationsEnabled) R.string.alerts_on else R.string.alerts_off),
                        if (notificationsEnabled) Green else MaterialTheme.colorScheme.error) { openAppNotificationSettings(context) }
                }

                UrgentAlertsPermissionCard()

                // Language
                LanguageCard()

                OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_logout))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (addOpen) {
        AddChildDialog(
            childrenCount = vm.children.size,
            showInitialLimit = showAddLimit,
            onDismiss = {
                addOpen = false
                showAddLimit = false
            },
            onAdd = { name, phoneNumber ->
                vm.addChild(name, phoneNumber)
                addOpen = false
                showAddLimit = false
            },
        )
    }
    rename?.let { c ->
        var name by remember { mutableStateOf(c.displayName) }
        var avatar by remember { mutableStateOf(c.avatar) }
        var phoneNumber by remember { mutableStateOf(c.phoneNumber.orEmpty()) }
        AlertDialog(onDismissRequest = { rename = null }, title = { Text(stringResource(R.string.edit_profile_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        phoneNumber,
                        { phoneNumber = it },
                        label = { Text(stringResource(R.string.field_phone_number_optional)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.child_avatar), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 6,
                    ) {
                        childAvatarOptions.forEach { option ->
                            FilterChip(
                                selected = avatar == option.key,
                                onClick = { avatar = option.key },
                                label = { Text(option.emoji, fontSize = MaterialTheme.typography.titleMedium.fontSize) },
                            )
                        }
                    }
                    TextButton(onClick = { pendingDelete = c; rename = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_child))
                    }
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.select(c.id); vm.updateChild(name.trim(), avatar, phoneNumber); rename = null }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { rename = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
    pendingDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_child_confirm_title)) },
            text = { Text(stringResource(R.string.delete_child_confirm_body, c.displayName)) },
            confirmButton = {
                TextButton(onClick = { vm.removeChild(c.id); pendingDelete = null }) {
                    Text(stringResource(R.string.delete_child), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    permissionsFor?.let { c ->
        ChildPermissionsDialog(child = c, onDismiss = { permissionsFor = null })
    }
}

@Composable
private fun UrgentAlertsPermissionCard() {
    val context = LocalContext.current
    val urgentEnabled = hasUrgentNotificationsEnabled(context)
    val dndBypass = hasUrgentDndBypass(context)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.colorScheme.secondary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.parent_urgent_permissions_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.parent_urgent_permissions_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ParentPermissionRow(
                Icons.Filled.NotificationsActive,
                stringResource(R.string.parent_permission_urgent_notifications),
                urgentEnabled,
                required = true,
                onClick = { openUrgentNotificationSettings(context) },
            )
            ParentPermissionRow(
                Icons.Filled.Warning,
                stringResource(R.string.parent_permission_dnd_bypass),
                dndBypass,
                required = true,
                onClick = { openNotificationPolicyAccessSettings(context) },
            )
        }
    }
}

@Composable
private fun AddChildDialog(
    childrenCount: Int,
    showInitialLimit: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    val childLimitMsg = stringResource(R.string.child_limit_reached)
    var localError by remember(showInitialLimit, childrenCount) {
        mutableStateOf(if (showInitialLimit || childrenCount >= 5) childLimitMsg else null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_child_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name,
                    {
                        name = it
                        if (childrenCount < 5) localError = null
                    },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = localError != null,
                )
                OutlinedTextField(
                    phoneNumber,
                    { phoneNumber = it },
                    label = { Text(stringResource(R.string.field_phone_number_optional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                localError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    if (childrenCount >= 5) {
                        localError = childLimitMsg
                    } else {
                        onAdd(name.trim(), phoneNumber.trim().takeIf { it.isNotEmpty() })
                    }
                },
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ChildSettingRow(
    child: Child,
    pairingCode: String?,
    onEdit: () -> Unit,
    onPermissions: () -> Unit,
    onGenerateCode: () -> Unit,
) {
    val device = child.primaryDevice()
    val online = device?.isConnected() == true
    val unpaired = device?.isUnpaired() == true
    Surface(shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(child.displayName, 44.dp, online = online, avatar = child.avatar)
                Column(Modifier.weight(1f)) {
                Text(child.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        unpaired -> stringResource(R.string.child_unpaired)
                        online -> stringResource(R.string.child_active, device?.batteryLevel?.let { " · $it%" } ?: "")
                        else -> stringResource(R.string.child_offline)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (online) Green else if (unpaired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LastActivityText(device, null)
                if (device.shouldShowBackgroundAccessHelp()) {
                    OfflineBackgroundHelpText(compact = true)
                }
                PermissionAttentionText(device)
                }
                FilledTonalIconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.cd_edit_child, child.displayName)) }
            }
            PermissionActionButton(device, onPermissions)
            OutlinedButton(onClick = onGenerateCode, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.VpnKey, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.generate_pairing_code))
            }
            if (pairingCode != null) {
                PairingCodePanel(pairingCode)
            }
        }
    }
}

@Composable
private fun PairingCodePanel(code: String) {
    val clipboard = LocalClipboardManager.current
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.pairing_code_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    code,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.pairing_code_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            FilledTonalIconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy_pairing_code))
            }
        }
    }
}

@Composable
private fun LanguageCard() {
    val context = LocalContext.current
    val current = Locales.currentTag   // "" = system, "en", "he"
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Language, null, tint = MaterialTheme.colorScheme.secondary)
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            LanguageOption(stringResource(R.string.lang_system), current == "") { Locales.apply(context, "") }
            LanguageOption(stringResource(R.string.lang_english), current == "en") { Locales.apply(context, "en") }
            LanguageOption(stringResource(R.string.lang_hebrew), current == "he") { Locales.apply(context, "he") }
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SettingMini(modifier: Modifier, icon: ImageVector, title: String, body: String, tint: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = tint)
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* --------------------------------- Dashboard --------------------------------- */

@Composable
private fun DashboardTab(
    vm: ParentViewModel,
    onTimeline: () -> Unit,
    onAlerts: () -> Unit,
    onSettings: () -> Unit,
    onAppUsage: (String) -> Unit,
    snackbar: SnackbarHostState,
) {
    var todayKey by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(todayKey) { vm.loadFamilyOverview() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(millisUntilNextLocalMidnight())
            todayKey = LocalDate.now()
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var addOpen by remember { mutableStateOf(false) }
    var showAddLimit by remember { mutableStateOf(false) }
    var permissionsFor by remember { mutableStateOf<Child?>(null) }
    var phonePromptFor by remember { mutableStateOf<Child?>(null) }
    var alertPromptFor by remember { mutableStateOf<Child?>(null) }
    val scopeId = vm.dashboardChildId
    val focused = vm.children.find { it.id == scopeId }
    val focusedDevice = focused?.primaryDevice()
    val focusedLocation = focused?.let { vm.allLocations[it.id] ?: vm.location }
    val onlineCount = vm.children.count { it.primaryDevice()?.isConnected() == true }
    val focusedOnline = focusedDevice?.isConnected() == true
    val focusedUnpaired = focusedDevice?.isUnpaired() == true
    val canPairFocusedChild = focused != null && (focusedDevice == null || focusedUnpaired || !focusedOnline)
    val scopeActive = if (focused == null) onlineCount > 0 else focusedOnline
    val scopeStatusColor = when {
        scopeActive -> Green
        focusedUnpaired -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val scopeStatusText = when {
        focused == null -> stringResource(if (onlineCount == 1) R.string.child_online else R.string.children_online, onlineCount)
        focusedUnpaired -> stringResource(R.string.child_unpaired)
        focusedOnline -> stringResource(R.string.chat_online)
        else -> stringResource(R.string.child_offline)
    }
    val monitoringText = when {
        scopeActive -> stringResource(R.string.active_monitoring)
        focusedUnpaired -> stringResource(R.string.child_unpaired)
        else -> stringResource(R.string.child_offline)
    }
    val noDialerMsg = stringResource(R.string.ring_no_phone_app)

    val markers = remember(vm.allLocations, vm.children, scopeId) {
        val src = if (scopeId == null) vm.children else vm.children.filter { it.id == scopeId }
        src.mapNotNull { c -> vm.allLocations[c.id]?.let { MapMarker(it.lat, it.lng, c.displayName) } }
    }
    var bigMap by remember { mutableStateOf(false) }
    if (bigMap && markers.isNotEmpty()) FullScreenFamilyMap(markers) { bigMap = false }
    if (addOpen) {
        AddChildDialog(
            childrenCount = vm.children.size,
            showInitialLimit = showAddLimit,
            onDismiss = {
                addOpen = false
                showAddLimit = false
            },
            onAdd = { name, phoneNumber ->
                vm.addChild(name, phoneNumber)
                addOpen = false
                showAddLimit = false
            },
        )
    }
    permissionsFor?.let { child ->
        ChildPermissionsDialog(child = child, onDismiss = { permissionsFor = null })
    }
    phonePromptFor?.let { child ->
        var phoneNumber by remember(child.id) { mutableStateOf(child.phoneNumber.orEmpty()) }
        AlertDialog(
            onDismissRequest = { phonePromptFor = null },
            title = { Text(stringResource(R.string.ring_phone_prompt_title, child.displayName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.ring_phone_prompt_body), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        phoneNumber,
                        { phoneNumber = it },
                        label = { Text(stringResource(R.string.field_phone_number)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = phoneNumber.isNotBlank(),
                    onClick = {
                        val number = phoneNumber.trim()
                        vm.select(child.id)
                        vm.updateChild(child.displayName, child.avatar, number)
                        phonePromptFor = null
                        if (!openDialer(context, number)) {
                            scope.launch { snackbar.showSnackbar(noDialerMsg) }
                        }
                    },
                ) { Text(stringResource(R.string.ring_call_action)) }
            },
            dismissButton = { TextButton(onClick = { phonePromptFor = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    alertPromptFor?.let { child ->
        val presets = listOf(
            stringResource(R.string.parent_alert_preset_call),
            stringResource(R.string.parent_alert_preset_answer),
            stringResource(R.string.parent_alert_preset_safe),
        )
        var body by remember(child.id) { mutableStateOf(presets.first()) }
        val urgentMissing = child.primaryDevice()?.permissionStatus.hasMissingUrgentAccess()
        val alertSent = stringResource(R.string.parent_alert_sent)
        AlertDialog(
            onDismissRequest = { alertPromptFor = null },
            title = { Text(stringResource(R.string.parent_alert_title, child.displayName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.parent_alert_body), style = MaterialTheme.typography.bodyMedium)
                    if (urgentMissing) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.parent_alert_child_permission_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    presets.forEach { preset ->
                        FilterChip(
                            selected = body == preset,
                            onClick = { body = preset },
                            label = { Text(preset) },
                        )
                    }
                    OutlinedTextField(
                        body,
                        { body = it },
                        label = { Text(stringResource(R.string.parent_alert_message_label)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = body.isNotBlank() && !vm.urgentSending,
                    onClick = {
                        vm.sendUrgentAlert(child.id, body.trim())
                        alertPromptFor = null
                        scope.launch { snackbar.showSnackbar(alertSent) }
                    },
                ) { Text(stringResource(R.string.parent_alert_send_action)) }
            },
            dismissButton = { TextButton(onClick = { alertPromptFor = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        FamilyTopBar(vm, onSettings = onSettings)
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

            // Child switcher chips
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill(stringResource(R.string.all_children), null, selected = scopeId == null) { vm.setDashboardChild(null) }
                vm.children.forEach { c -> FilterPill(c.displayName, c, selected = scopeId == c.id) { vm.setDashboardChild(c.id) } }
                FilledTonalIconButton(
                    onClick = {
                        showAddLimit = vm.children.size >= 5
                        addOpen = true
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Filled.Add, stringResource(R.string.add_child), modifier = Modifier.size(20.dp))
                }
            }

            // Family Network hero
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PulsingDot(scopeStatusColor)
                            Text(monitoringText, style = MaterialTheme.typography.labelLarge, color = scopeStatusColor)
                        }
                        if (focused != null && focusedDevice != null && !focusedDevice.isUnpaired()) {
                            IconButton(
                                onClick = { permissionsFor = focused },
                                modifier = Modifier.size(40.dp),
                            ) {
                                PermissionStatusIcon(
                                    ready = focusedDevice.permissionStatus?.requiredPermissionsSatisfied() == true,
                                    contentDescription = stringResource(R.string.parent_permissions_action),
                                )
                            }
                        }
                    }
                    Text(focused?.displayName ?: stringResource(R.string.family_network),
                        style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(scopeStatusText, style = MaterialTheme.typography.bodyMedium, color = scopeStatusColor)
                    focusedDevice?.let { device ->
                        val child = focused
                        if (device.isUnpaired()) {
                            Text(stringResource(R.string.child_unpaired_notice, child?.displayName ?: stringResource(R.string.label_your_child)),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                        LastActivityText(device, focusedLocation)
                        if (device.shouldShowBackgroundAccessHelp()) {
                            OfflineBackgroundHelpText()
                        }
                        PermissionAttentionText(device)
                    }
                    if (focused != null && focusedLocation != null) {
                        DirectionsActionButton(
                            lat = focusedLocation.lat,
                            lng = focusedLocation.lng,
                            childName = focused.displayName,
                            lastKnown = !focusedOnline,
                            snackbar = snackbar,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (canPairFocusedChild) {
                        val child = focused ?: return@Column
                        OutlinedButton(
                            onClick = {
                                vm.select(child.id)
                                vm.generateCode()
                            },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.VpnKey, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.generate_pairing_code))
                        }
                        if (vm.selectedId == child.id) {
                            vm.pairingCode?.let { PairingCodePanel(it) }
                        }
                    }
                    if (markers.isNotEmpty()) {
                        Box(Modifier.fillMaxWidth().height(240.dp).clip(MaterialTheme.shapes.medium)) {
                            OsmFamilyMap(markers, Modifier.fillMaxSize())
                            Surface(color = Navy, shape = CircleShape, shadowElevation = 6.dp,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).clickable { bigMap = true }) {
                                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Map, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.full_view), color = Color.White, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    } else {
                        EmptyCard(Icons.Filled.MyLocation, stringResource(R.string.empty_locations_title),
                            stringResource(R.string.empty_locations_body))
                    }
                }
            }

            val activeSosCards = vm.children.mapNotNull { child ->
                val state = vm.sosByChild[child.id]?.takeIf { it.active } ?: return@mapNotNull null
                if (scopeId == null || scopeId == child.id) child to state else null
            }
            if (activeSosCards.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    activeSosCards.forEach { (child, state) ->
                        ActiveSosCard(
                            child = child,
                            state = state,
                            snackbar = snackbar,
                            onAcknowledge = { eventId -> vm.acknowledgeSos(child.id, eventId) },
                        )
                    }
                }
            }

            // Screen Time entry -> App Usage
            val usageChild = focused?.id ?: vm.children.firstOrNull()?.id
            if (usageChild != null) {
                Surface(onClick = { onAppUsage(usageChild) }, shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(SkyBright.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Schedule, null, tint = MaterialTheme.colorScheme.secondary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.appusage_card_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.appusage_card_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Top Places Today
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.top_places_today), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                val places = if (scopeId == null) vm.topPlaces else vm.topPlaces.filter { it.childId == scopeId }
                if (places.isEmpty()) {
                    EmptyCard(Icons.Filled.Place, stringResource(R.string.empty_places_title), stringResource(R.string.empty_places_body))
                } else {
                    places.forEach { TopPlaceRow(it) }
                }
            }

            // Recent Alerts
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recent_alerts), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    TextButton(onClick = onAlerts) { Text(stringResource(R.string.view_all)) }
                }
                val fa = if (scopeId == null) vm.familyAlerts else vm.familyAlerts.filter { it.childId == scopeId }
                if (fa.isEmpty()) {
                    EmptyCard(Icons.Filled.NotificationsNone, stringResource(R.string.empty_alerts_title), stringResource(R.string.empty_alerts_body))
                } else {
                    fa.take(5).forEach { FamilyAlertRow(it) }
                }
            }

            // Quick actions
            Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (focused != null) {
                    QuickAction(Modifier.weight(1f), Icons.Filled.PhoneAndroid, stringResource(R.string.action_ring_device),
                        MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.primary) {
                        val phone = focused.phoneNumber?.trim().orEmpty()
                        if (phone.isNotEmpty()) {
                            if (!openDialer(context, phone)) {
                                scope.launch { snackbar.showSnackbar(noDialerMsg) }
                            }
                        } else {
                            phonePromptFor = focused
                        }
                    }
                }
                QuickAction(Modifier.weight(1f), Icons.Filled.History, stringResource(R.string.action_timeline),
                    MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.primary, onClick = onTimeline)
                if (focused != null) {
                    QuickAction(Modifier.weight(1f), Icons.Filled.Warning, stringResource(R.string.action_alert),
                        MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error) {
                        alertPromptFor = focused
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSosCard(
    child: Child,
    state: SosState,
    snackbar: SnackbarHostState,
    onAcknowledge: (String) -> Unit,
) {
    val event = state.event ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val remainingMinutes = ((state.remainingSeconds + 59) / 60).coerceAtLeast(0)
    val noDialerMsg = stringResource(R.string.ring_no_phone_app)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.parent_sos_active_title, child.displayName),
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.parent_sos_started, ago(event.startedAt)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Text(
                stringResource(R.string.parent_sos_remaining, remainingMinutes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            event.lastLocationAt?.let {
                Text(
                    stringResource(R.string.parent_sos_location_age, ago(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            event.lastBatteryLevel?.let {
                Text(
                    stringResource(R.string.parent_sos_battery, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            event.lastLocation?.let { loc ->
                DirectionsActionButton(
                    lat = loc.lat,
                    lng = loc.lng,
                    childName = child.displayName,
                    lastKnown = false,
                    snackbar = snackbar,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                child.phoneNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { phone ->
                    OutlinedButton(
                        onClick = {
                            if (!openDialer(context, phone)) {
                                scope.launch { snackbar.showSnackbar(noDialerMsg) }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PhoneAndroid, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.parent_sos_call_action))
                    }
                }
                Button(
                    onClick = { onAcknowledge(event.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.parent_sos_ack_action), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FamilyTopBar(vm: ParentViewModel, onSettings: () -> Unit) {
    val child = vm.selected
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (child != null) Avatar(child.displayName, 40.dp, online = child.primaryDevice()?.isConnected() == true, avatar = child.avatar)
            else Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.cd_settings), tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun AllAlertsScreen(vm: ParentViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.loadAllFamilyAlerts() }
    val scopeId = vm.dashboardChildId
    val alerts = if (scopeId == null) vm.allFamilyAlerts else vm.allFamilyAlerts.filter { it.childId == scopeId }
    val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    stringResource(R.string.all_alerts_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + navigationBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.loadingAllAlerts) {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (alerts.isEmpty()) {
                EmptyCard(Icons.Filled.NotificationsNone, stringResource(R.string.empty_alerts_title), stringResource(R.string.empty_alerts_body))
            } else {
                alerts.forEach { FamilyAlertRow(it) }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, child: Child?, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            child?.let { Avatar(it.displayName, 24.dp, online = it.primaryDevice()?.isConnected() == true, avatar = it.avatar) }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TopPlaceRow(p: TopPlace) {
    val place = rememberPlaceName(p.lat, p.lng)
    var showMap by remember { mutableStateOf(false) }
    if (showMap) FullScreenMap(p.lat, p.lng, place) { showMap = false }
    Surface(onClick = { showMap = true }, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(SkyBright.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.secondary)
            }
            Column(Modifier.weight(1f)) {
                Text(place, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(stringResource(R.string.place_visited, p.childName, timeOf(p.arriveAt), timeOf(p.departAt)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FamilyAlertRow(fa: FamilyAlert) {
    val a = fa.alert
    val low = a.type == "low_battery"
    val (icon, tintBg, tintFg) = when (a.type) {
        "low_battery" -> Triple(Icons.Filled.BatteryFull, Orange.copy(alpha = 0.15f), Orange)
        "offline" -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.secondary)
        "safe_zone_enter", "safe_zone_exit" -> Triple(Icons.Filled.Place, Green.copy(alpha = 0.15f), Green)
        "kid_sos_started", "urgent_alert" -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
        "kid_sos_ended" -> Triple(Icons.Filled.CheckCircle, Green.copy(alpha = 0.15f), Green)
        else -> Triple(Icons.Filled.NotificationsActive, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary)
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(tintBg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tintFg)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.alert_named, fa.childName, alertTypeLabel(a.type)),
                    style = MaterialTheme.typography.titleMedium)
                Text(stringResource(when {
                    low -> R.string.alert_battery_body
                    a.type == "offline" -> R.string.alert_offline_body
                    a.type == "safe_zone_enter" -> R.string.alert_safe_zone_enter_body
                    a.type == "safe_zone_exit" -> R.string.alert_safe_zone_exit_body
                    a.type == "kid_sos_started" -> R.string.alert_kid_sos_started_body
                    a.type == "kid_sos_ended" -> R.string.alert_kid_sos_ended_body
                    a.type == "urgent_alert" -> R.string.alert_urgent_body
                    else -> R.string.alert_status_body
                }),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(dateTimeOf(a.createdAt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun alertTypeLabel(type: String): String = when (type) {
    "low_battery" -> stringResource(R.string.alert_low_battery)
    "offline" -> stringResource(R.string.alert_offline)
    "child_unpaired" -> stringResource(R.string.alert_child_unpaired)
    "safe_zone_enter" -> stringResource(R.string.alert_safe_zone_enter)
    "safe_zone_exit" -> stringResource(R.string.alert_safe_zone_exit)
    "kid_sos_started" -> stringResource(R.string.alert_kid_sos_started)
    "kid_sos_ended" -> stringResource(R.string.alert_kid_sos_ended)
    "urgent_alert" -> stringResource(R.string.alert_urgent)
    else -> stringResource(R.string.alert_generic)
}

@Composable
private fun QuickAction(modifier: Modifier, icon: ImageVector, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(80.dp), shape = MaterialTheme.shapes.large, color = bg) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = fg)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ----------------------------------- Chat ----------------------------------- */

@Composable
private fun ChatTab(vm: ParentViewModel, onSettings: () -> Unit, active: Boolean) {
    val onlyChild = vm.children.singleOrNull()
    LaunchedEffect(active, onlyChild?.id, vm.chatChildId) {
        if (!active) return@LaunchedEffect
        if (onlyChild != null && vm.chatChildId == null) vm.openChat(onlyChild.id)
    }
    when {
        onlyChild != null && vm.chatChildId == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        vm.chatChildId == null -> ConversationList(vm, onSettings, active)
        else -> ChatThread(vm, onSettings, showBack = onlyChild == null, active = active)
    }
}

@Composable
private fun ConversationList(vm: ParentViewModel, onSettings: () -> Unit, active: Boolean) {
    LaunchedEffect(active) {
        if (active) vm.loadConversations()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        ParentTopBar(vm, onSettings)
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            if (vm.children.isEmpty()) {
                EmptyCard(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.chat_empty_title), stringResource(R.string.chat_tap_to_start))
            } else {
                vm.children.forEach { c -> ConversationRow(vm, c) { vm.openChat(c.id) } }
            }
        }
    }
}

@Composable
private fun ConversationRow(vm: ParentViewModel, child: Child, onClick: () -> Unit) {
    val last = vm.lastMessageByChild[child.id]
    val unread = vm.unreadByChild[child.id] ?: 0
    val online = child.primaryDevice()?.isConnected() == true
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(child.displayName, 48.dp, online = online, avatar = child.avatar)
            Column(Modifier.weight(1f)) {
                Text(child.displayName, style = MaterialTheme.typography.titleMedium)
                Text(last?.body ?: stringResource(R.string.chat_tap_to_start),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                last?.let { Text(timeOf(it.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (unread > 0) Surface(color = MaterialTheme.colorScheme.secondary, shape = CircleShape) {
                    Text("$unread", Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ChatThread(vm: ParentViewModel, onSettings: () -> Unit, showBack: Boolean, active: Boolean) {
    val child = vm.chatChild
    val name = child?.displayName ?: stringResource(R.string.label_child)
    val device = child?.primaryDevice()
    val online = device?.isConnected() == true
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val loc = vm.location
    val zoneName = if (vm.chatChildId == vm.selectedId)
        vm.zones.firstOrNull { z -> loc != null && distanceM(loc.lat, loc.lng, z.lat, z.lng) <= z.radiusM }?.name else null
    if (showBack) BackHandler(enabled = active) { vm.closeChat() }
    // Only auto-scroll to the bottom when a NEWER message arrives (last id changes),
    // not when older history is prepended.
    LaunchedEffect(active, vm.chatMessages.lastOrNull()?.id) {
        if (!active) return@LaunchedEffect
        if (vm.chatMessages.isNotEmpty()) listState.animateScrollToItem(vm.chatMessages.size - 1)
    }
    Column(Modifier.fillMaxSize()) {
        // Header: avatar + online status + settings
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (showBack) {
                    IconButton(onClick = { vm.closeChat() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (child != null) Avatar(name, 40.dp, online = online, avatar = child.avatar)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (online) PulsingDot(Green, 6.dp)
                        Text(
                            when {
                                device?.isUnpaired() == true -> stringResource(R.string.child_unpaired)
                                online -> stringResource(R.string.chat_online)
                                else -> stringResource(R.string.child_offline)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (online) Green else if (device?.isUnpaired() == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.cd_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        // Messages
        if (vm.chatMessages.isEmpty() && zoneName == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.chat_empty_body, name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                if (vm.chatCursor != null) item {
                    Box(Modifier.fillMaxWidth().padding(4.dp), contentAlignment = Alignment.Center) {
                        if (vm.loadingOlder) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else TextButton(onClick = { vm.loadOlder() }) { Text(stringResource(R.string.chat_load_earlier)) }
                    }
                }
                itemsIndexed(vm.chatMessages, key = { _, m -> m.id }) { index, m ->
                    val currentDate = messageLocalDate(m.createdAt)
                    val previousDate = vm.chatMessages.getOrNull(index - 1)?.createdAt?.let { messageLocalDate(it) }
                    if (index == 0 || currentDate != previousDate) DateChip(chatDateLabel(m.createdAt))
                    MessageBubble(m.sender == "parent", m.body, m.createdAt, m.readAt != null)
                }
                zoneName?.let { z -> item { SafeZoneSystemCard(z, name) } }
            }
        }
        // Quick replies
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.qr_love, R.string.qr_stay_safe, R.string.qr_call_later).forEach { res ->
                val q = stringResource(res); QuickReplyChip(q) { vm.sendChat(q) }
            }
        }
        ChatInput(input, { input = it }, vm.sending, stringResource(R.string.chat_hint_name, name)) {
            if (input.isNotBlank()) { vm.sendChat(input); input = "" }
        }
    }
}

@Composable
private fun chatDateLabel(createdAt: String): String {
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
private fun DateChip(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = CircleShape) {
            Text(text, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessageBubble(mine: Boolean, body: String, createdAt: String, read: Boolean) {
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
private fun SafeZoneSystemCard(zoneName: String, childName: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 1.dp, modifier = Modifier.widthIn(max = 360.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Green.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Place, null, tint = Green, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(stringResource(R.string.chat_safe_zone_title, zoneName), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.chat_safe_zone_body, childName), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun QuickReplyChip(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChatInput(value: String, onChange: (String) -> Unit, sending: Boolean, placeholder: String, onSend: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value, onChange, modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) }, shape = CircleShape, maxLines = 4)
            FilledIconButton(onClick = onSend, enabled = value.isNotBlank() && !sending, modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.cd_send))
            }
        }
    }
}

/* ----------------------------------- Map ----------------------------------- */

private data class LocatedChild(val child: Child, val location: CurrentLocation)

@Composable
private fun MapTab(vm: ParentViewModel, snackbar: SnackbarHostState, active: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locatedChildren = remember(vm.children, vm.allLocations) {
        vm.children.mapNotNull { child -> vm.allLocations[child.id]?.let { LocatedChild(child, it) } }
    }
    val locatedIds = locatedChildren.joinToString(separator = "|") { it.child.id }
    val allZones = remember(vm.mapZonesByChild) { vm.mapZonesByChild.values.flatten().distinctBy { it.id } }
    var parentLocation by remember { mutableStateOf<MapPoint?>(null) }
    var cameraTarget by remember { mutableStateOf<MapPoint?>(null) }
    var cameraCommand by remember { mutableStateOf(0L) }
    val pagerState = rememberPagerState(pageCount = { locatedChildren.size })

    fun centerOn(point: MapPoint) {
        cameraTarget = point
        cameraCommand += 1
    }

    fun loadParentLocation() {
        scope.launch { parentLocation = ParentLocation.currentOrLastKnown(context) }
    }

    val parentLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) loadParentLocation()
    }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        vm.loadMapZones()
        if (ParentLocation.hasPermission(context)) {
            parentLocation = ParentLocation.currentOrLastKnown(context)
        } else {
            parentLocationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    LaunchedEffect(active, vm.children.map { it.id }.joinToString(separator = "|")) {
        if (!active) return@LaunchedEffect
        vm.loadMapZones()
    }
    LaunchedEffect(active, locatedIds) {
        if (!active) return@LaunchedEffect
        val targetId = vm.ensureMapTargetChild()
        val page = locatedChildren.indexOfFirst { it.child.id == targetId }
        if (page >= 0) {
            pagerState.scrollToPage(page)
            val loc = locatedChildren[page].location
            centerOn(MapPoint(loc.lat, loc.lng))
        }
    }
    LaunchedEffect(active, vm.selectedId, locatedIds) {
        if (!active) return@LaunchedEffect
        val page = locatedChildren.indexOfFirst { it.child.id == vm.selectedId }
        if (page >= 0 && page != pagerState.currentPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(page)
        }
    }
    LaunchedEffect(active, pagerState, locatedIds) {
        if (!active) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                locatedChildren.getOrNull(page)?.let { located ->
                    if (vm.selectedId != located.child.id) vm.selectMapChild(located.child.id)
                    centerOn(MapPoint(located.location.lat, located.location.lng))
                }
            }
    }

    val activePage = pagerState.currentPage.coerceIn(0, (locatedChildren.size - 1).coerceAtLeast(0))
    val activeChildId = locatedChildren.getOrNull(activePage)?.child?.id ?: vm.selectedId
    val childMarkers = remember(locatedChildren, activeChildId) {
        locatedChildren.map { located ->
            FamilyMapMarker(
                id = located.child.id,
                lat = located.location.lat,
                lng = located.location.lng,
                label = located.child.displayName,
                selected = located.child.id == activeChildId,
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (locatedChildren.isNotEmpty() || parentLocation != null || allZones.isNotEmpty()) {
            OsmLiveFamilyMap(
                childMarkers = childMarkers,
                zones = allZones,
                parentLocation = parentLocation,
                cameraTarget = cameraTarget,
                cameraCommand = cameraCommand,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                EmptyCard(Icons.Filled.MyLocation, stringResource(R.string.map_empty_title), stringResource(R.string.map_empty_body))
            }
        }
        if (locatedChildren.isEmpty()) {
            Box(Modifier.align(Alignment.Center).padding(16.dp).fillMaxWidth().widthIn(max = 560.dp)) {
                EmptyCard(Icons.Filled.MyLocation, stringResource(R.string.map_empty_title), stringResource(R.string.map_empty_body))
            }
        } else {
            Box(
                Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 16.dp).fillMaxWidth().widthIn(max = 592.dp),
            ) {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    pageSpacing = 12.dp,
                ) { page ->
                    val located = locatedChildren[page]
                    MapChildInfoCard(
                        located = located,
                        zones = vm.mapZonesByChild[located.child.id].orEmpty(),
                        snackbar = snackbar,
                        onRecenter = { centerOn(MapPoint(located.location.lat, located.location.lng)) },
                    )
                }
            }
        }
        parentLocation?.let { parent ->
            FloatingActionButton(
                onClick = { centerOn(parent) },
                containerColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) {
                Icon(Icons.Filled.MyLocation, stringResource(R.string.cd_recenter_parent), tint = Color.White)
            }
        }
    }
}

@Composable
private fun MapChildInfoCard(
    located: LocatedChild,
    zones: List<Zone>,
    snackbar: SnackbarHostState,
    onRecenter: () -> Unit,
) {
    val child = located.child
    val loc = located.location
    val device = child.primaryDevice()
    val unpaired = device?.isUnpaired() == true
    val online = device?.isConnected() == true
    val inZone = zones.any { it.active && distanceM(loc.lat, loc.lng, it.lat, it.lng) <= it.radiusM }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(child.displayName, 40.dp, online = online, avatar = child.avatar)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(
                        when {
                            unpaired -> R.string.map_status_unpaired
                            !online -> R.string.map_status_offline
                            inZone -> R.string.map_status_safe
                            else -> R.string.map_status_moving
                        },
                        child.displayName,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (unpaired) {
                    Text(stringResource(R.string.child_unpaired_last_location),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                LastActivityText(device, loc)
                if (device.shouldShowBackgroundAccessHelp()) {
                    OfflineBackgroundHelpText(compact = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (inZone) Chip(stringResource(R.string.chip_safe_zone), Green, dot = true)
                    device?.batteryLevel?.let { Chip("$it%", batteryColor(it)) }
                }
                DirectionsActionButton(
                    lat = loc.lat,
                    lng = loc.lng,
                    childName = child.displayName,
                    lastKnown = !online,
                    snackbar = snackbar,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(onClick = onRecenter) {
                Icon(Icons.Filled.MyLocation, stringResource(R.string.cd_recenter_child, child.displayName))
            }
        }
    }
}

/* --------------------------------- History --------------------------------- */

@Composable
private fun HistoryTab(vm: ParentViewModel, onSettings: () -> Unit) {
    val today = remember { LocalDate.now() }
    var selected by remember { mutableStateOf(today) }
    var selectedRoute by remember { mutableStateOf<FrequentRoute?>(null) }
    val switcherDays = remember(today, vm.historyDays) { historySwitcherDays(today, vm.historyDays) }
    val routeDays = remember(vm.trips, selectedRoute) { selectedRoute?.let { routeOccurrenceDays(vm.trips, it) }.orEmpty() }

    LaunchedEffect(vm.selectedId) {
        selectedRoute = null
    }
    LaunchedEffect(selectedRoute, routeDays) {
        if (selectedRoute != null) {
            routeDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
                .maxOrNull()
                ?.let { day ->
                    selected = day
                    vm.loadHistory(day.toString())
                }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        ParentTopBar(vm, onSettings)
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

            if (vm.children.size > 1) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        vm.children.forEach { child ->
                            ChildHistorySegment(child, selected = child.id == vm.selectedId) {
                                vm.select(child.id, selected.toString())
                            }
                        }
                    }
                }
            }

            // Frequent / recurring routes: departure -> return points.
            Text(stringResource(R.string.frequent_routes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (vm.frequentRoutes.isEmpty()) {
                EmptyCard(Icons.AutoMirrored.Filled.DirectionsWalk, stringResource(R.string.empty_routes_title),
                    stringResource(R.string.empty_routes_body, vm.selected?.displayName ?: stringResource(R.string.label_your_child)))
            } else {
                vm.frequentRoutes.forEach { r ->
                    val selectedThisRoute = selectedRoute == r
                    RouteCard(
                        r = r,
                        selected = selectedThisRoute,
                        onClick = { selectedRoute = if (selectedThisRoute) null else r },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Per-day timeline
            Text(stringResource(R.string.daily_timeline), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            HistoryDaySwitcher(
                days = switcherDays,
                selected = selected,
                routeDays = routeDays,
                routeFilterActive = selectedRoute != null,
                onSelect = { d ->
                    selected = d
                    vm.loadHistory(d.toString())
                },
            )
            val activities = remember(vm.history, vm.trips, vm.zones, selected, selectedRoute) {
                buildHistoryActivities(vm.history, vm.trips, vm.zones, selected.toString(), selectedRoute)
            }
            if (activities.isEmpty()) {
                EmptyCard(Icons.Filled.History, stringResource(R.string.empty_history_title), stringResource(R.string.empty_history_body))
            } else {
                activities.forEach { activity -> TimelineEntry(activity, vm.zones) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HistoryDaySwitcher(
    days: List<LocalDate>,
    selected: LocalDate,
    routeDays: Set<String>,
    routeFilterActive: Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val dayWidth = (maxWidth - 36.dp) / 7
        val visibleDays = if (layoutDirection == LayoutDirection.Rtl) days.asReversed() else days
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
        ) {
            visibleDays.forEach { d ->
                val isSelected = d == selected
                val enabled = !routeFilterActive || d.toString() in routeDays
                val contentColor = when {
                    isSelected -> Color.White
                    enabled -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                }
                Surface(
                    onClick = { onSelect(d) },
                    enabled = enabled,
                    shape = MaterialTheme.shapes.medium,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        enabled -> MaterialTheme.colorScheme.surfaceContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    modifier = Modifier.width(dayWidth),
                ) {
                    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) Color.White else contentColor)
                        Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = contentColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildHistorySegment(child: Child, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Avatar(child.displayName, 24.dp, online = child.primaryDevice()?.isConnected() == true, avatar = child.avatar)
            Text(child.displayName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RouteCard(r: FrequentRoute, selected: Boolean, onClick: () -> Unit) {
    val fromPlace = rememberPlaceName(r.from.lat, r.from.lng)
    val toPlace = rememberPlaceName(r.to.lat, r.to.lng)
    var showMap by remember { mutableStateOf(false) }
    if (showMap) FullScreenMap(r.to.lat, r.to.lng, stringResource(R.string.return_point)) { showMap = false }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$fromPlace ⇄ $toPlace", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                    Text(stringResource(R.string.route_count, r.count), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                }
            }
            com.familyshield.mobile.ui.OsmRoute(r.from, r.to, Modifier.fillMaxWidth().height(140.dp).clip(MaterialTheme.shapes.medium))
            Text(stringResource(R.string.route_summary, "%.1f".format(r.avgKm), r.avgMinutes.toInt(), timeOf(r.lastAt)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { showMap = true }, shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Map, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.open_return_point))
            }
        }
    }
}

@Composable
private fun RouteEndpoint(icon: ImageVector, tint: Color, place: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(place, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

@Composable
private fun TimelineEntry(activity: HistoryActivity, zones: List<Zone>) {
    when (activity) {
        is HistoryStay -> StayTimelineEntry(activity)
        is HistoryRouteActivity -> RouteTimelineEntry(activity, zones)
        is HistoryMovementActivity -> MovementTimelineEntry(activity, zones)
    }
}

@Composable
private fun StayTimelineEntry(stay: HistoryStay) {
    var showMap by remember { mutableStateOf(false) }
    val placeName = rememberBestGeocodedPlaceName(stay.lat, stay.lng, stay.nameCandidates, enabled = stay.zoneName == null)
    val title = stay.zoneName ?: placeName
    val timeRange = timeRangeOf(stay.startAt, stay.endAt)
    if (showMap) FullScreenMap(stay.lat, stay.lng, title ?: timeRange) { showMap = false }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(timeRange, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            Text("%.4f, %.4f".format(stay.lat, stay.lng), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { showMap = true }, shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Map, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.view_on_map))
            }
        }
    }
}

/* ---------------------------------- Zones ---------------------------------- */

@Composable
private fun ZonesTab(vm: ParentViewModel, onSettings: () -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Zone?>(null) }
    var pendingDelete by remember { mutableStateOf<Zone?>(null) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            ParentTopBar(vm, onSettings)
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.zones_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.zones_subtitle),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (vm.zones.isEmpty()) {
                    EmptyCard(Icons.Filled.Place, stringResource(R.string.empty_zones_title), stringResource(R.string.empty_zones_body))
                } else {
                    vm.zones.forEach { z ->
                        ZoneCard(
                            z = z,
                            onEdit = { editing = z },
                            onDelete = { pendingDelete = z },
                        )
                    }
                }

                Spacer(Modifier.height(72.dp))
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, containerColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(Icons.Filled.Add, stringResource(R.string.cd_add_zone), tint = Color.White)
        }
    }
    if (showAdd) AddZoneDialog(vm, onDismiss = { showAdd = false })
    editing?.let { zone ->
        EditZoneDialog(
            zone = zone,
            onDismiss = { editing = null },
            onSave = { name, radius, active ->
                vm.updateZone(zone.id, name, radius, active)
                editing = null
            },
        )
    }
    pendingDelete?.let { zone ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.zone_delete_confirm_title)) },
            text = { Text(stringResource(R.string.zone_delete_confirm_body, zone.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeZone(zone.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_yes)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ZoneCard(z: Zone, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center) { Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.secondary) }
                Column(Modifier.weight(1f)) {
                    Text(z.name, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(
                            stringResource(if (z.active) R.string.chip_active else R.string.chip_inactive),
                            if (z.active) Green else MaterialTheme.colorScheme.onSurfaceVariant,
                            dot = z.active,
                        )
                        Text(stringResource(R.string.zone_radius, z.radiusM), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.cd_edit_zone), tint = MaterialTheme.colorScheme.secondary) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.cd_delete_zone), tint = MaterialTheme.colorScheme.error) }
            }
            if (z.lat != 0.0 || z.lng != 0.0) {
                OsmMapZones(z.lat, z.lng, listOf(z), Modifier.fillMaxWidth().height(110.dp).clip(MaterialTheme.shapes.medium), zoom = 15.0)
            }
        }
    }
}

@Composable
private fun RouteTimelineEntry(route: HistoryRouteActivity, zones: List<Zone>) {
    PathTimelineEntry(
        from = route.from,
        to = route.to,
        startAt = route.startAt,
        endAt = route.endAt,
        points = route.points,
        zones = zones,
    )
}

@Composable
private fun MovementTimelineEntry(movement: HistoryMovementActivity, zones: List<Zone>) {
    PathTimelineEntry(
        from = movement.from,
        to = movement.to,
        startAt = movement.startAt,
        endAt = movement.endAt,
        points = movement.points,
        zones = zones,
    )
}

@Composable
private fun PathTimelineEntry(
    from: Geo,
    to: Geo,
    startAt: String,
    endAt: String,
    points: List<RoutePoint>,
    zones: List<Zone>,
) {
    var showMap by remember { mutableStateOf(false) }
    val fromName = rememberNamedLocation(from.lat, from.lng, zones)
    val toName = rememberNamedLocation(to.lat, to.lng, zones)
    val title = "$fromName -> $toName"
    val timeRange = timeRangeOf(startAt, endAt)
    val mapPoints = remember(points) { points.map { MapPoint(it.lat, it.lng) } }
    if (showMap) FullScreenRouteMap(mapPoints, title) { showMap = false }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(timeRange, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.history_route_start_coordinates, "%.4f, %.4f".format(from.lat, from.lng)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.history_route_end_coordinates, "%.4f, %.4f".format(to.lat, to.lng)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { showMap = true }, shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Map, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.view_on_map))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddZoneDialog(vm: ParentViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf(50) }
    val locatedChildren = remember(vm.children, vm.allLocations, vm.selectedId, vm.location) {
        vm.children.mapNotNull { child ->
            val loc = if (child.id == vm.selectedId) vm.location ?: vm.allLocations[child.id] else vm.allLocations[child.id]
            loc?.let { child to it }
        }
    }
    var expanded by remember { mutableStateOf(false) }
    var selectedChildId by remember(locatedChildren, vm.selectedId) {
        mutableStateOf(
            locatedChildren.firstOrNull { it.first.id == vm.selectedId }?.first?.id
                ?: locatedChildren.firstOrNull()?.first?.id,
        )
    }
    val selected = locatedChildren.firstOrNull { it.first.id == selectedChildId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_zone_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.zone_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ZoneRadiusPicker(radius, onRadiusChange = { radius = it })
                if (locatedChildren.isEmpty()) {
                    Text(stringResource(R.string.zone_center_no),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selected?.first?.displayName.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.zone_center_child_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            locatedChildren.forEach { (child, _) ->
                                DropdownMenuItem(
                                    text = { Text(child.displayName) },
                                    onClick = {
                                        selectedChildId = child.id
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && selected != null, onClick = {
                selected?.let { (child, loc) -> vm.addZone(name.trim(), child.id, loc.lat, loc.lng, radius) }
                onDismiss()
            }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun EditZoneDialog(zone: Zone, onDismiss: () -> Unit, onSave: (String, Int, Boolean) -> Unit) {
    var name by remember(zone.id) { mutableStateOf(zone.name) }
    var radius by remember(zone.id) { mutableStateOf(zone.radiusM.coerceIn(50, 2000).roundToZoneStep()) }
    var active by remember(zone.id) { mutableStateOf(zone.active) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_zone_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.zone_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ZoneRadiusPicker(radius, onRadiusChange = { radius = it })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.zone_active_label), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), radius, active) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ZoneRadiusPicker(radius: Int, onRadiusChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.zone_radius_label, radius), style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { onRadiusChange((radius - 10).coerceAtLeast(50)) }, enabled = radius > 50) {
                Icon(Icons.Filled.Remove, stringResource(R.string.cd_decrease_radius))
            }
            Slider(
                value = radius.toFloat(),
                onValueChange = { onRadiusChange(it.roundToInt().roundToZoneStep()) },
                valueRange = 50f..2000f,
                steps = 194,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onRadiusChange((radius + 10).coerceAtMost(2000)) }, enabled = radius < 2000) {
                Icon(Icons.Filled.Add, stringResource(R.string.cd_increase_radius))
            }
        }
    }
}

private fun Int.roundToZoneStep(): Int =
    ((this / 10.0).roundToInt() * 10).coerceIn(50, 2000)

/* ---------------------------------- Shared ---------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionsActionButton(
    lat: Double,
    lng: Double,
    childName: String,
    lastKnown: Boolean,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noAppMessage = stringResource(R.string.directions_no_app)
    val destinationLabel = stringResource(
        if (lastKnown) R.string.directions_sheet_last_known_title else R.string.directions_sheet_title,
        childName,
    )
    val destination = remember(lat, lng, destinationLabel) {
        DirectionDestination(lat, lng, destinationLabel)
    }
    var options by remember { mutableStateOf<List<DirectionOption>?>(null) }

    OutlinedButton(
        onClick = {
            val available = externalDirectionOptions(context, destination)
            if (available.isEmpty()) {
                scope.launch { snackbar.showSnackbar(noAppMessage) }
            } else {
                options = available
            }
        },
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Icon(Icons.Filled.Map, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (lastKnown) R.string.directions_last_known else R.string.directions))
    }

    options?.let { availableOptions ->
        ModalBottomSheet(onDismissRequest = { options = null }) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(destinationLabel, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                availableOptions.forEach { option ->
                    DirectionOptionRow(option) {
                        options = null
                        if (!openExternalDirections(context, option)) {
                            scope.launch { snackbar.showSnackbar(noAppMessage) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectionOptionRow(option: DirectionOption, onClick: () -> Unit) {
    val label = when (option.app) {
        DirectionApp.GoogleMaps -> stringResource(R.string.directions_google_maps)
        DirectionApp.Waze -> stringResource(R.string.directions_waze)
        DirectionApp.Other -> stringResource(R.string.directions_other_map)
    }
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Map, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, dot: Boolean = false) {
    Surface(color = color.copy(alpha = 0.14f), shape = CircleShape) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (dot) PulsingDot(color, 7.dp)
            Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyCard(icon: ImageVector, title: String, body: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun batteryColor(level: Int): Color = when {
    level <= 15 -> Danger
    level <= 35 -> Orange
    else -> Green
}

private fun Child.primaryDevice(): Device? =
    devices.maxByOrNull { it.lastSeenAt ?: it.revokedAt ?: "" }

private fun Device.isUnpaired(): Boolean = revokedAt != null

private fun Device.isConnected(): Boolean {
    if (revokedAt != null) return false
    isOnline?.let { return it }
    val last = lastSeenAt ?: return false
    return try {
        java.time.Duration.between(OffsetDateTime.parse(last).toInstant(), java.time.Instant.now()).toMinutes() < 30
    } catch (e: Exception) {
        false
    }
}

internal fun Device?.shouldShowBackgroundAccessHelp(): Boolean {
    val device = this ?: return false
    return device.revokedAt == null && device.lastSeenAt != null && !device.isConnected()
}

internal fun PermissionStatus?.hasMissingRequiredAccess(): Boolean =
    this != null && !requiredPermissionsSatisfied()

internal fun PermissionStatus?.hasMissingUrgentAccess(): Boolean =
    this != null && ((r and PERMISSION_URGENT_NOTIFICATIONS) != 0 && (m and PERMISSION_URGENT_NOTIFICATIONS) == 0 ||
        (r and PERMISSION_DND_BYPASS) != 0 && (m and PERMISSION_DND_BYPASS) == 0)

internal fun Device?.hasMissingPermissions(): Boolean =
    this?.permissionStatus.hasMissingRequiredAccess()

@Composable
private fun PermissionAttentionText(device: Device?) {
    if (!device.hasMissingPermissions()) return
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.parent_permissions_attention),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PermissionActionButton(device: Device?, onClick: () -> Unit) {
    if (device == null || device.isUnpaired()) return
    val missing = device.hasMissingPermissions()
    if (!missing) return
    val label = stringResource(R.string.parent_permissions_action)
    OutlinedButton(onClick = onClick, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Warning, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ChildPermissionsDialog(child: Child, onDismiss: () -> Unit) {
    val device = child.primaryDevice()
    val status = device?.permissionStatus
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.parent_permissions_title, child.displayName)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    device == null -> Text(stringResource(R.string.parent_permissions_no_device), style = MaterialTheme.typography.bodyMedium)
                    status == null -> Text(stringResource(R.string.parent_permissions_pending), style = MaterialTheme.typography.bodyMedium)
                    status.hasMissingRequiredAccess() -> Text(
                        stringResource(R.string.parent_permissions_attention),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Text(
                        stringResource(R.string.parent_permissions_all_enabled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Green,
                    )
                }
                if (status != null) {
                    MonitoredPermissionsContent(
                        ready = status.requiredPermissionsSatisfied(),
                        body = stringResource(R.string.parent_permissions_body),
                        items = status.toMonitoredPermissionItems(),
                        lastChecked = device.permissionStatusCheckedAt?.let {
                            stringResource(R.string.parent_permissions_last_checked, ago(it))
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

@Composable
private fun ParentPermissionRow(
    icon: ImageVector,
    label: String,
    granted: Boolean,
    required: Boolean,
    onClick: (() -> Unit)? = null,
) {
    if (!required) return
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            null,
            tint = if (granted) Green else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun OfflineBackgroundHelpText(compact: Boolean = false) {
    val text = stringResource(if (compact) R.string.parent_background_hint_short else R.string.parent_background_hint)
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        Text(
            text,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LastActivityText(device: Device?, location: CurrentLocation?) {
    val last = device?.lastSeenAt ?: location?.recordedAt ?: device?.revokedAt ?: return
    Text(
        stringResource(R.string.last_kid_activity, ago(last)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun rememberPlaceName(lat: Double?, lng: Double?): String {
    var name by remember(lat, lng) { mutableStateOf<String?>(null) }
    LaunchedEffect(lat, lng) {
        if (lat != null && lng != null) name = com.familyshield.mobile.net.Geocoding.reverse(lat, lng)
    }
    return name ?: if (lat != null && lng != null) "%.4f, %.4f".format(lat, lng) else "ג€”"
}

private fun timeOf(iso: String): String = try {
    formatMessageTime(iso)
} catch (e: Exception) { "" }

private fun timeRangeOf(startIso: String, endIso: String): String {
    val start = timeOf(startIso)
    val end = timeOf(endIso)
    return if (start == end || end.isBlank()) start else "$start - $end"
}

private fun dateTimeOf(iso: String): String = try {
    "${formatMessageDate(iso)}, ${timeOf(iso)}"
} catch (e: Exception) { timeOf(iso) }

@Composable
private fun rememberGeocodedPlaceName(lat: Double, lng: Double, enabled: Boolean): String? {
    return rememberBestGeocodedPlaceName(lat, lng, emptyList(), enabled)
}

@Composable
private fun rememberBestGeocodedPlaceName(lat: Double, lng: Double, candidates: List<Geo>, enabled: Boolean): String? {
    var name by remember(lat, lng, candidates, enabled) { mutableStateOf<String?>(null) }
    LaunchedEffect(lat, lng, candidates, enabled) {
        name = null
        if (!enabled) return@LaunchedEffect
        val points = (listOf(Geo(lat, lng)) + candidates)
            .distinctBy { "%.6f,%.6f".format(it.lat, it.lng) }
        for (point in points) {
            val resolved = com.familyshield.mobile.net.Geocoding.reverse(point.lat, point.lng)
            if (!resolved.isNullOrBlank()) {
                name = resolved
                break
            }
        }
    }
    return name
}

@Composable
private fun rememberNamedLocation(lat: Double, lng: Double, zones: List<Zone>): String {
    val zoneName = remember(lat, lng, zones) {
        zones.firstOrNull { z -> distanceM(lat, lng, z.lat, z.lng) <= z.radiusM }?.name
    }
    val geocoded = rememberGeocodedPlaceName(lat, lng, enabled = zoneName == null)
    return zoneName ?: geocoded ?: "%.4f, %.4f".format(lat, lng)
}

private fun millisUntilNextLocalMidnight(): Long {
    val now = ZonedDateTime.now()
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1000L)
}

private fun openDialer(context: Context, phoneNumber: String): Boolean {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phoneNumber)}"))
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

private fun ago(iso: String): String = try {
    val mins = java.time.Duration.between(OffsetDateTime.parse(iso).toInstant(), java.time.Instant.now()).toMinutes()
    when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 1440 -> "${mins / 60} h ago"
        else -> "${mins / 1440} d ago"
    }
} catch (e: Exception) { "recently" }

private fun distanceM(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val dLat = Math.toRadians(bLat - aLat); val dLng = Math.toRadians(bLng - aLng)
    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return 6_371_000.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}
