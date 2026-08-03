package com.familyshield.mobile.permissions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.familyshield.mobile.R
import com.familyshield.mobile.kid.BackgroundMonitoringStatus
import com.familyshield.mobile.kid.requiresManualConfirmation
import com.familyshield.mobile.net.PermissionStatus
import com.familyshield.mobile.ui.theme.Green

const val PERMISSION_FOREGROUND_LOCATION = 1
const val PERMISSION_BACKGROUND_LOCATION = 2
const val PERMISSION_NOTIFICATIONS = 4
const val PERMISSION_BATTERY_UNRESTRICTED = 8
const val PERMISSION_APP_USAGE = 16
const val PERMISSION_MANUFACTURER_BACKGROUND = 32
const val PERMISSION_URGENT_NOTIFICATIONS = 64
const val PERMISSION_DND_BYPASS = 128

enum class MonitoredPermission {
    Location,
    Notifications,
    UrgentNotifications,
    DndBypass,
    Battery,
    AppUsage,
    ManufacturerBackground,
}

data class MonitoredPermissionItem(
    val permission: MonitoredPermission,
    val granted: Boolean,
    val required: Boolean = true,
    val onClick: (() -> Unit)? = null,
    val checked: Boolean? = null,
    val onCheckedChange: ((Boolean) -> Unit)? = null,
)

fun PermissionStatus.requiredPermissionsSatisfied(): Boolean =
    (m and requiredMaskWithoutOptionalAppUsage()) == requiredMaskWithoutOptionalAppUsage()

fun PermissionStatus.requiredMaskWithoutOptionalAppUsage(): Int =
    r and PERMISSION_APP_USAGE.inv()

fun PermissionStatus.toMonitoredPermissionItems(): List<MonitoredPermissionItem> = buildList {
    add(MonitoredPermissionItem(
        permission = MonitoredPermission.Location,
        granted = (m and PERMISSION_FOREGROUND_LOCATION) != 0 && (m and PERMISSION_BACKGROUND_LOCATION) != 0,
        required = (r and PERMISSION_FOREGROUND_LOCATION) != 0 || (r and PERMISSION_BACKGROUND_LOCATION) != 0,
    ))
    add(MonitoredPermissionItem(MonitoredPermission.Notifications, (m and PERMISSION_NOTIFICATIONS) != 0, (r and PERMISSION_NOTIFICATIONS) != 0))
    add(MonitoredPermissionItem(MonitoredPermission.UrgentNotifications, (m and PERMISSION_URGENT_NOTIFICATIONS) != 0, (r and PERMISSION_URGENT_NOTIFICATIONS) != 0))
    add(MonitoredPermissionItem(MonitoredPermission.DndBypass, (m and PERMISSION_DND_BYPASS) != 0, (r and PERMISSION_DND_BYPASS) != 0))
    add(MonitoredPermissionItem(MonitoredPermission.Battery, (m and PERMISSION_BATTERY_UNRESTRICTED) != 0, (r and PERMISSION_BATTERY_UNRESTRICTED) != 0))
    add(MonitoredPermissionItem(MonitoredPermission.AppUsage, (m and PERMISSION_APP_USAGE) != 0, true))
    add(MonitoredPermissionItem(
        permission = MonitoredPermission.ManufacturerBackground,
        granted = (m and PERMISSION_MANUFACTURER_BACKGROUND) != 0,
        required = (r and PERMISSION_MANUFACTURER_BACKGROUND) != 0,
    ))
}.filter { it.required }

@Composable
fun BackgroundMonitoringStatus.toMonitoredPermissionItems(
    onLocationClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onUrgentNotificationsClick: () -> Unit,
    onDndBypassClick: () -> Unit,
    onBatteryClick: () -> Unit,
    onAppUsageClick: () -> Unit,
    onManufacturerBackgroundClick: () -> Unit,
    onManualConfirmationChange: (Boolean) -> Unit,
): List<MonitoredPermissionItem> = buildList {
    add(MonitoredPermissionItem(
        permission = MonitoredPermission.Location,
        granted = foregroundLocationGranted && backgroundLocationGranted,
        onClick = onLocationClick,
    ))
    add(MonitoredPermissionItem(MonitoredPermission.Notifications, notificationsEnabled, onClick = onNotificationsClick))
    add(MonitoredPermissionItem(MonitoredPermission.UrgentNotifications, urgentNotificationsEnabled, onClick = onUrgentNotificationsClick))
    add(MonitoredPermissionItem(MonitoredPermission.DndBypass, urgentDndBypassAllowed, onClick = onDndBypassClick))
    add(MonitoredPermissionItem(MonitoredPermission.Battery, batteryUnrestricted, onClick = onBatteryClick))
    add(MonitoredPermissionItem(MonitoredPermission.AppUsage, appUsageGranted, onClick = onAppUsageClick))
    if (manufacturerGuide.requiresManualConfirmation) {
        add(MonitoredPermissionItem(
            permission = MonitoredPermission.ManufacturerBackground,
            granted = manufacturerAccessConfirmed,
            onClick = onManufacturerBackgroundClick,
            checked = manufacturerAccessConfirmed,
            onCheckedChange = onManualConfirmationChange,
        ))
    }
}

@Composable
fun MonitoredPermissionsCard(
    ready: Boolean,
    body: String,
    items: List<MonitoredPermissionItem>,
    modifier: Modifier = Modifier,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp, modifier = modifier) {
        MonitoredPermissionsContent(
            ready = ready,
            body = body,
            items = items,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
fun MonitoredPermissionsContent(
    ready: Boolean,
    body: String,
    items: List<MonitoredPermissionItem>,
    modifier: Modifier = Modifier,
    lastChecked: String? = null,
) {
    var info by remember { mutableStateOf<MonitoredPermission?>(null) }
    info?.let { selected ->
        AlertDialog(
            onDismissRequest = { info = null },
            title = { Text(monitoredPermissionTitle(selected)) },
            text = { Text(monitoredPermissionBody(selected)) },
            confirmButton = {
                TextButton(onClick = { info = null }) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (ready) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                null,
                tint = if (ready) Green else MaterialTheme.colorScheme.error,
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.kid_permissions_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (ready) R.string.kid_permissions_ready else R.string.kid_permissions_needs_attention),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ready) Green else MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        items.forEach { item ->
            MonitoredPermissionRow(item = item, onInfo = { info = item.permission })
        }
        lastChecked?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PermissionStatusIcon(
    ready: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val color = if (ready) Green else MaterialTheme.colorScheme.error
    Box(modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.VerifiedUser, contentDescription, tint = color, modifier = Modifier.size(27.dp))
        if (ready) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).size(12.dp)
                    .background(Green, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(8.dp))
            }
        } else {
            Icon(
                Icons.Filled.Warning,
                null,
                tint = color,
                modifier = Modifier.align(Alignment.BottomEnd).size(12.dp),
            )
        }
    }
}

@Composable
private fun MonitoredPermissionRow(
    item: MonitoredPermissionItem,
    onInfo: () -> Unit,
) {
    val label = monitoredPermissionTitle(item.permission)
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (item.onClick != null) Modifier.clickable(onClick = item.onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(monitoredPermissionIcon(item.permission), null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (item.checked != null && item.onCheckedChange != null) {
            Checkbox(checked = item.checked, onCheckedChange = item.onCheckedChange)
        }
        IconButton(onClick = onInfo, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Info, stringResource(R.string.kid_permission_info, label), modifier = Modifier.size(20.dp))
        }
        Icon(
            if (item.granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            null,
            tint = if (item.granted) Green else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun monitoredPermissionIcon(permission: MonitoredPermission): ImageVector = when (permission) {
    MonitoredPermission.Location -> Icons.Filled.LocationOn
    MonitoredPermission.Notifications -> Icons.Filled.Notifications
    MonitoredPermission.UrgentNotifications -> Icons.Filled.NotificationsActive
    MonitoredPermission.DndBypass -> Icons.Filled.Warning
    MonitoredPermission.Battery -> Icons.Filled.BatteryFull
    MonitoredPermission.AppUsage -> Icons.Filled.Apps
    MonitoredPermission.ManufacturerBackground -> Icons.Filled.PhoneAndroid
}

@Composable
private fun monitoredPermissionTitle(permission: MonitoredPermission): String = stringResource(
    when (permission) {
        MonitoredPermission.Location -> R.string.kid_permission_location
        MonitoredPermission.Notifications -> R.string.kid_permission_notifications
        MonitoredPermission.Battery -> R.string.kid_permission_battery
        MonitoredPermission.AppUsage -> R.string.kid_permission_app_usage
        MonitoredPermission.UrgentNotifications -> R.string.kid_permission_urgent_notifications
        MonitoredPermission.DndBypass -> R.string.kid_permission_dnd_bypass
        MonitoredPermission.ManufacturerBackground -> R.string.kid_permission_manufacturer_background
    },
)

@Composable
private fun monitoredPermissionBody(permission: MonitoredPermission): String = stringResource(
    when (permission) {
        MonitoredPermission.Location -> R.string.kid_permission_location_info
        MonitoredPermission.Notifications -> R.string.kid_permission_notifications_info
        MonitoredPermission.Battery -> R.string.kid_permission_battery_info
        MonitoredPermission.AppUsage -> R.string.kid_permission_app_usage_info
        MonitoredPermission.UrgentNotifications -> R.string.kid_permission_urgent_notifications_info
        MonitoredPermission.DndBypass -> R.string.kid_permission_dnd_bypass_info
        MonitoredPermission.ManufacturerBackground -> R.string.kid_permission_manufacturer_background_info
    },
)
