package com.familyshield.mobile.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.familyshield.mobile.BuildConfig
import com.familyshield.mobile.R
import com.familyshield.mobile.Locales
import com.familyshield.mobile.net.Alert
import com.familyshield.mobile.net.Child
import com.familyshield.mobile.net.CurrentLocation
import com.familyshield.mobile.net.Device
import com.familyshield.mobile.net.HistoryPoint
import com.familyshield.mobile.net.Zone
import com.familyshield.mobile.ui.Avatar
import com.familyshield.mobile.ui.GradientButton
import com.familyshield.mobile.ui.MapMarker
import com.familyshield.mobile.ui.OsmFamilyMap
import com.familyshield.mobile.ui.OsmMap
import com.familyshield.mobile.ui.OsmMapZones
import com.familyshield.mobile.ui.PulsingDot
import com.familyshield.mobile.ui.childAvatarOptions
import com.familyshield.mobile.ui.FullScreenFamilyMap
import com.familyshield.mobile.ui.FullScreenMap
import com.familyshield.mobile.ui.theme.BrandGradient
import com.familyshield.mobile.ui.theme.Danger
import com.familyshield.mobile.ui.theme.Green
import com.familyshield.mobile.ui.theme.Navy
import com.familyshield.mobile.ui.theme.Orange
import com.familyshield.mobile.ui.theme.SkyBright
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos

@Composable
fun ParentApp(
    onKidDevice: () -> Unit,
    vm: ParentViewModel = viewModel(factory = ParentViewModel.factory(LocalContext.current)),
) {
    if (vm.token == null) LoginScreen(vm, onKidDevice) else ParentShell(vm)
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
private fun ParentShell(vm: ParentViewModel) {
    LaunchedEffect(Unit) { vm.refreshChildren() }
    // Real-time location tracking while the parent app is open.
    DisposableEffect(Unit) {
        vm.startLive()
        onDispose { vm.stopLive() }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it); vm.clearError() } }
    var tab by remember { mutableStateOf(Tab.Dashboard) }
    var showSettings by remember { mutableStateOf(false) }
    var appUsageFor by remember { mutableStateOf<String?>(null) }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
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
    // Stop chat polling whenever the Chat tab isn't the one on screen.
    LaunchedEffect(tab, showSettings, appUsageFor) { if (tab != Tab.Chat || showSettings || appUsageFor != null) vm.closeChat() }

    if (showSettings) {
        SettingsScreen(vm, onBack = { showSettings = false }, onOpenZones = { showSettings = false; tab = Tab.Zones })
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
                Tab.entries.forEach { t ->
                    val label = stringResource(t.labelRes)
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
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
        Box(Modifier.padding(pad)) {
            when (tab) {
                Tab.Dashboard -> DashboardTab(vm, onTimeline = { tab = Tab.History }, onSettings = openSettings,
                    onAppUsage = { id -> appUsageFor = id }, snackbar = snackbar)
                Tab.Chat -> ChatTab(vm, onSettings = openSettings)
                Tab.Map -> MapTab(vm)
                Tab.History -> HistoryTab(vm, onSettings = openSettings)
                Tab.Zones -> ZonesTab(vm, onSettings = openSettings)
            }
        }
    }
}

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

    var addOpen by remember { mutableStateOf(false) }
    var showAddLimit by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Child?>(null) }
    var pendingDelete by remember { mutableStateOf<Child?>(null) }
    val activeCount = vm.children.count { it.primaryDevice()?.isConnected() == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                        Switch(checked = vm.biometricLock, onCheckedChange = { vm.updateBiometricLock(it) })
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingMini(Modifier.weight(1f), Icons.Filled.Place, stringResource(R.string.settings_zones), stringResource(R.string.settings_zones_body, vm.zones.size), MaterialTheme.colorScheme.secondary, onOpenZones)
                    SettingMini(Modifier.weight(1f), Icons.Filled.NotificationsActive, stringResource(R.string.settings_alerts),
                        stringResource(if (vm.alertsEnabled) R.string.alerts_on else R.string.alerts_off), MaterialTheme.colorScheme.tertiary) { vm.updateAlertsEnabled(!vm.alertsEnabled) }
                }

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
            onAdd = { name ->
                vm.addChild(name)
                addOpen = false
                showAddLimit = false
            },
        )
    }
    rename?.let { c ->
        var name by remember { mutableStateOf(c.displayName) }
        var avatar by remember { mutableStateOf(c.avatar) }
        AlertDialog(onDismissRequest = { rename = null }, title = { Text(stringResource(R.string.edit_profile_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.select(c.id); vm.updateChild(name.trim(), avatar); rename = null }) { Text(stringResource(R.string.action_save)) } },
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
}

@Composable
private fun AddChildDialog(
    childrenCount: Int,
    showInitialLimit: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
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
                        onAdd(name.trim())
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
                }
                FilledTonalIconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.cd_edit_child, child.displayName)) }
            }
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
    val current = remember { Locales.saved(context) }   // "" = system, "en", "he"
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
private fun DashboardTab(vm: ParentViewModel, onTimeline: () -> Unit, onSettings: () -> Unit, onAppUsage: (String) -> Unit, snackbar: SnackbarHostState) {
    LaunchedEffect(Unit) { vm.loadFamilyOverview() }
    val scope = rememberCoroutineScope()
    var addOpen by remember { mutableStateOf(false) }
    var showAddLimit by remember { mutableStateOf(false) }
    val scopeId = vm.dashboardChildId
    val focused = vm.children.find { it.id == scopeId }
    val focusedDevice = focused?.primaryDevice()
    val focusedLocation = focused?.let { vm.allLocations[it.id] ?: vm.location }
    val onlineCount = vm.children.count { it.primaryDevice()?.isConnected() == true }
    val focusedOnline = focusedDevice?.isConnected() == true
    val focusedUnpaired = focusedDevice?.isUnpaired() == true
    val canPairFocusedChild = focused != null && (focusedDevice == null || focusedUnpaired)
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
    val ringMsg = stringResource(R.string.snack_ring, focused?.displayName ?: stringResource(R.string.label_device))
    val emergencyMsg = stringResource(R.string.snack_emergency)

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
            onAdd = { name ->
                vm.addChild(name)
                addOpen = false
                showAddLimit = false
            },
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PulsingDot(scopeStatusColor)
                        Text(monitoringText, style = MaterialTheme.typography.labelLarge, color = scopeStatusColor)
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
                    }
                    if (canPairFocusedChild && focused != null) {
                        val child = focused
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
                    TextButton(onClick = onTimeline) { Text(stringResource(R.string.view_all)) }
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
                QuickAction(Modifier.weight(1f), Icons.Filled.NotificationsActive, stringResource(R.string.action_ring_device),
                    MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.primary) {
                    scope.launch { snackbar.showSnackbar(ringMsg) }
                }
                QuickAction(Modifier.weight(1f), Icons.Filled.History, stringResource(R.string.action_timeline),
                    MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.primary, onClick = onTimeline)
                QuickAction(Modifier.weight(1f), Icons.Filled.Warning, stringResource(R.string.action_emergency),
                    MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error) {
                    scope.launch { snackbar.showSnackbar(emergencyMsg) }
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
                Text(stringResource(if (low) R.string.alert_battery_body else R.string.alert_status_body),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(timeOf(a.createdAt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun alertTypeLabel(type: String): String = when (type) {
    "low_battery" -> stringResource(R.string.alert_low_battery)
    "offline" -> stringResource(R.string.alert_offline)
    "child_unpaired" -> stringResource(R.string.alert_child_unpaired)
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
private fun ChatTab(vm: ParentViewModel, onSettings: () -> Unit) {
    if (vm.chatChildId == null) ConversationList(vm, onSettings) else ChatThread(vm, onSettings)
}

@Composable
private fun ConversationList(vm: ParentViewModel, onSettings: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadConversations() }
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
private fun ChatThread(vm: ParentViewModel, onSettings: () -> Unit) {
    val child = vm.chatChild
    val name = child?.displayName ?: stringResource(R.string.label_child)
    val device = child?.primaryDevice()
    val online = device?.isConnected() == true
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val loc = vm.location
    val zoneName = if (vm.chatChildId == vm.selectedId)
        vm.zones.firstOrNull { z -> loc != null && distanceM(loc.lat, loc.lng, z.lat, z.lng) <= z.radiusM }?.name else null
    val requestMsg = stringResource(R.string.chat_request_location_msg)
    // Only auto-scroll to the bottom when a NEWER message arrives (last id changes),
    // not when older history is prepended.
    LaunchedEffect(vm.chatMessages.lastOrNull()?.id) {
        if (vm.chatMessages.isNotEmpty()) listState.animateScrollToItem(vm.chatMessages.size - 1)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        // Header: avatar + online status + settings
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.closeChat() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.primary) }
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
                item { DateChip(stringResource(R.string.chat_today)) }
                items(vm.chatMessages, key = { it.id }) { m -> MessageBubble(m.sender == "parent", m.body, m.createdAt, m.readAt != null) }
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
        ChatInput(input, { input = it }, vm.sending, stringResource(R.string.chat_hint_name, name),
            onRequestLocation = { vm.sendChat(requestMsg) }) {
            if (input.isNotBlank()) { vm.sendChat(input); input = "" }
        }
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
    val shape = RoundedCornerShape(16.dp, 16.dp, if (mine) 4.dp else 16.dp, if (mine) 16.dp else 4.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Surface(color = bg, shape = shape, shadowElevation = if (mine) 3.dp else 1.dp, modifier = Modifier.widthIn(max = 300.dp)) {
            Text(body, color = fg, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        Row(Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(timeOf(createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            if (mine) Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(14.dp),
                tint = if (read) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
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
private fun ChatInput(value: String, onChange: (String) -> Unit, sending: Boolean, placeholder: String, onRequestLocation: () -> Unit, onSend: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledIconButton(onClick = onRequestLocation, modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                Icon(Icons.Filled.MyLocation, stringResource(R.string.cd_request_location))
            }
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

@Composable
private fun MapTab(vm: ParentViewModel) {
    val child = vm.selected
    val loc = vm.location
    val device = child?.primaryDevice()
    val unpaired = device?.isUnpaired() == true
    val inZone = loc != null && vm.zones.any { distanceM(loc.lat, loc.lng, it.lat, it.lng) <= it.radiusM }

    Box(Modifier.fillMaxSize()) {
        if (loc != null) {
            OsmMapZones(loc.lat, loc.lng, vm.zones, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                EmptyCard(Icons.Filled.MyLocation, stringResource(R.string.map_empty_title), stringResource(R.string.map_empty_body))
            }
        }
        // Floating status card
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(16.dp).fillMaxWidth().widthIn(max = 560.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (child != null) Avatar(child.displayName, 40.dp, online = device?.isConnected() == true, avatar = child.avatar)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(
                        when {
                            unpaired -> R.string.map_status_unpaired
                            inZone -> R.string.map_status_safe
                            else -> R.string.map_status_moving
                        },
                        child?.displayName ?: stringResource(R.string.label_child)),
                        style = MaterialTheme.typography.titleMedium)
                    if (unpaired && loc != null) {
                        Text(stringResource(R.string.child_unpaired_last_location),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    LastActivityText(device, loc)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (inZone) Chip(stringResource(R.string.chip_safe_zone), Green, dot = true)
                        device?.batteryLevel?.let { Chip("$it%", batteryColor(it)) }
                    }
                }
                FilledTonalIconButton(onClick = { vm.refreshDetail() }) { Icon(Icons.Filled.Refresh, stringResource(R.string.cd_refresh)) }
            }
        }
        FloatingActionButton(onClick = { vm.refreshDetail() }, containerColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(Icons.Filled.MyLocation, stringResource(R.string.cd_locate), tint = Color.White)
        }
    }
}

/* --------------------------------- History --------------------------------- */

@Composable
private fun HistoryTab(vm: ParentViewModel, onSettings: () -> Unit) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val days = remember { (0..6).map { today.minusDays(it.toLong()) } }
    var selected by remember { mutableStateOf(today) }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        ParentTopBar(vm, onSettings)
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

            // Frequent / recurring routes: departure -> return points.
            Text(stringResource(R.string.frequent_routes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (vm.frequentRoutes.isEmpty()) {
                EmptyCard(Icons.AutoMirrored.Filled.DirectionsWalk, stringResource(R.string.empty_routes_title),
                    stringResource(R.string.empty_routes_body, vm.selected?.displayName ?: stringResource(R.string.label_your_child)))
            } else {
                vm.frequentRoutes.forEach { r -> RouteCard(r) }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Per-day timeline
            Text(stringResource(R.string.daily_timeline), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                days.reversed().forEach { d ->
                    val sel = d == selected
                    Surface(onClick = { selected = d; vm.loadHistory(d.toString()) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(d.dayOfWeek.name.take(3), style = MaterialTheme.typography.labelSmall,
                                color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium,
                                color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (vm.history.isEmpty()) {
                EmptyCard(Icons.Filled.History, stringResource(R.string.empty_history_title), stringResource(R.string.empty_history_body))
            } else {
                vm.history.forEach { p -> TimelineEntry(p) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RouteCard(r: com.familyshield.mobile.net.FrequentRoute) {
    val fromPlace = rememberPlaceName(r.from.lat, r.from.lng)
    val toPlace = rememberPlaceName(r.to.lat, r.to.lng)
    var showMap by remember { mutableStateOf(false) }
    if (showMap) FullScreenMap(r.to.lat, r.to.lng, stringResource(R.string.return_point)) { showMap = false }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RouteEndpoint(Icons.Filled.TripOrigin, MaterialTheme.colorScheme.tertiary, fromPlace)
                    RouteEndpoint(Icons.Filled.Place, MaterialTheme.colorScheme.primary, toPlace)
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
private fun TimelineEntry(p: HistoryPoint) {
    var showMap by remember { mutableStateOf(false) }
    if (showMap) FullScreenMap(p.lat, p.lng, timeOf(p.recordedAt)) { showMap = false }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(timeOf(p.recordedAt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.location_update), style = MaterialTheme.typography.titleMedium)
            Text("%.4f, %.4f".format(p.lat, p.lng), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            ParentTopBar(vm, onSettings)
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.zones_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.zones_subtitle, vm.selected?.displayName ?: stringResource(R.string.label_your_child)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (vm.zones.isEmpty()) {
                    EmptyCard(Icons.Filled.Place, stringResource(R.string.empty_zones_title), stringResource(R.string.empty_zones_body))
                } else {
                    vm.zones.forEach { z -> ZoneCard(z) { vm.removeZone(z.id) } }
                }

                // Notification strategy
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.notif_strategy_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(stringResource(R.string.notif_strategy_body),
                            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
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
}

@Composable
private fun ZoneCard(z: Zone, onDelete: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center) { Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.secondary) }
                Column(Modifier.weight(1f)) {
                    Text(z.name, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(stringResource(R.string.chip_active), Green, dot = true)
                        Text(stringResource(R.string.zone_radius, z.radiusM), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.cd_delete_zone), tint = MaterialTheme.colorScheme.error) }
            }
            if (z.lat != 0.0 || z.lng != 0.0) {
                OsmMapZones(z.lat, z.lng, listOf(z), Modifier.fillMaxWidth().height(110.dp).clip(MaterialTheme.shapes.medium), zoom = 15.0)
            }
        }
    }
}

@Composable
private fun AddZoneDialog(vm: ParentViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf(300f) }
    val loc = vm.location
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_zone_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.zone_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.zone_radius_label, radius.toInt()), style = MaterialTheme.typography.bodyMedium)
                Slider(value = radius, onValueChange = { radius = it }, valueRange = 50f..2000f)
                Text(
                    stringResource(if (loc != null) R.string.zone_center_yes else R.string.zone_center_no),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && loc != null, onClick = {
                loc?.let { vm.addZone(name.trim(), it.lat, it.lng, radius.toInt()) }; onDismiss()
            }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/* ---------------------------------- Shared ---------------------------------- */

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

private fun Device.isConnected(): Boolean = revokedAt == null && lastSeenAt != null

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
    OffsetDateTime.parse(iso).toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a"))
} catch (e: Exception) { "" }

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
