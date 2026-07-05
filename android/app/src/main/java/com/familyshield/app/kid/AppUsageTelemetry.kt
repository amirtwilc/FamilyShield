package com.familyshield.app.kid

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.familyshield.app.net.AppUsageReportItem
import java.time.LocalDate
import java.time.ZoneId

object AppUsageTelemetry {
    private const val MIN_REPORTED_MINUTES = 5

    private val infrastructurePackages = setOf(
        "android",
        "com.android.providers.downloads",
        "com.android.settings",
        "com.android.systemui",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.google.android.providers.media.module",
        "com.google.android.webview",
    )

    private val infrastructurePrefixes = listOf(
        "com.android.",
        "com.google.android.adservices",
        "com.google.android.apps.restore",
        "com.google.android.ext.",
        "com.google.android.inputdevices",
        "com.google.android.modulemetadata",
        "com.google.android.networkstack",
        "com.google.android.overlay.",
        "com.miui.",
        "com.qualcomm.",
        "com.xiaomi.",
    )

    fun hasUsageAccess(context: Context): Boolean {
        val app = context.applicationContext
        val ops = app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), app.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), app.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun todayUsage(context: Context, maxItems: Int = 100): List<AppUsageReportItem> {
        val app = context.applicationContext
        if (!hasUsageAccess(app)) return emptyList()

        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        val usage = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val totals = foregroundTotalsFromEvents(usage, start, end).toMutableMap()
        usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end).orEmpty().forEach { stat ->
            val aggregateMs = stat.totalTimeInForeground
            if (aggregateMs > 0) {
                totals[stat.packageName] = maxOf(totals[stat.packageName] ?: 0L, aggregateMs)
            }
        }

        val pm = app.packageManager
        val catalog = launchableCatalog(pm)
        val homePackages = homePackages(pm)
        val keyboardPackages = keyboardPackages(app)

        return totals.asSequence()
            .filter { (_, foregroundMs) -> foregroundMs > 0 }
            .mapNotNull { (packageName, foregroundMs) ->
                val info = catalog[packageName]?.info
                    ?: runCatching { pm.getApplicationInfoCompat(packageName) }.getOrNull()
                val minutes = (foregroundMs / 60_000L).toInt().coerceAtLeast(1)
                val classification = classifyPackage(
                    appPackage = app.packageName,
                    packageName = packageName,
                    info = info,
                    launchable = catalog.containsKey(packageName),
                    homePackages = homePackages,
                    keyboardPackages = keyboardPackages,
                )
                if (!classification.isRelevant || minutes < MIN_REPORTED_MINUTES) return@mapNotNull null
                val label = catalog[packageName]?.label
                    ?: appLabel(pm, info, packageName)
                AppUsageReportItem(
                    app = label.take(64),
                    packageName = packageName.take(128),
                    category = appCategory(info).take(32),
                    minutes = minutes.coerceIn(0, 1440),
                    day = day.toString(),
                )
            }
            .sortedByDescending { it.minutes }
            .take(maxItems)
            .toList()
    }

    private data class CatalogEntry(val label: String, val info: ApplicationInfo?)
    private data class Classification(val isRelevant: Boolean)

    private fun launchableCatalog(pm: PackageManager): Map<String, CatalogEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return activities.mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            val label = runCatching { resolveInfo.loadLabel(pm).toString() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: packageName
            packageName to CatalogEntry(label, resolveInfo.activityInfo?.applicationInfo)
        }.toMap()
    }

    private fun homePackages(pm: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return activities.mapNotNullTo(mutableSetOf()) { it.activityInfo?.packageName }
    }

    private fun keyboardPackages(context: Context): Set<String> {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.mapTo(mutableSetOf()) { it.packageName }
    }

    private fun classifyPackage(
        appPackage: String,
        packageName: String,
        info: ApplicationInfo?,
        launchable: Boolean,
        homePackages: Set<String>,
        keyboardPackages: Set<String>,
    ): Classification {
        if (packageName == appPackage) return Classification(false)
        if (packageName in homePackages) return Classification(false)
        if (packageName in keyboardPackages) return Classification(false)
        if (packageName in infrastructurePackages) return Classification(false)
        if (infrastructurePrefixes.any { packageName.startsWith(it) } && !launchable) {
            return Classification(false)
        }
        if (!launchable && info?.isSystemApp() == true) return Classification(false)
        if (!launchable) return Classification(false)
        return Classification(true)
    }

    private fun foregroundTotalsFromEvents(
        usage: UsageStatsManager,
        start: Long,
        end: Long,
    ): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        val activeSince = mutableMapOf<String, Long>()
        val events = usage.queryEvents(start, end) ?: return emptyMap()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName?.takeIf { it.isNotBlank() } ?: continue
            when {
                isForegroundEvent(event.eventType) -> activeSince.putIfAbsent(packageName, event.timeStamp)
                isBackgroundEvent(event.eventType) -> {
                    val since = activeSince.remove(packageName) ?: continue
                    if (event.timeStamp > since) {
                        totals[packageName] = (totals[packageName] ?: 0L) + (event.timeStamp - since)
                    }
                }
            }
        }
        activeSince.forEach { (packageName, since) ->
            if (end > since) totals[packageName] = (totals[packageName] ?: 0L) + (end - since)
        }
        return totals
    }

    private fun isForegroundEvent(type: Int): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type == UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            type == UsageEvents.Event.MOVE_TO_FOREGROUND
        }

    private fun isBackgroundEvent(type: Int): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type == UsageEvents.Event.ACTIVITY_PAUSED
        } else {
            @Suppress("DEPRECATION")
            type == UsageEvents.Event.MOVE_TO_BACKGROUND
        }

    private fun appLabel(pm: PackageManager, info: ApplicationInfo?, packageName: String): String {
        if (info == null) return packageName
        val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrNull()
        return label?.takeIf { it.isNotBlank() } ?: packageName
    }

    private fun appCategory(info: ApplicationInfo?): String {
        if (info == null) return "Other"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "Other"
        return when (info.category) {
            ApplicationInfo.CATEGORY_AUDIO -> "Audio"
            ApplicationInfo.CATEGORY_GAME -> "Game"
            ApplicationInfo.CATEGORY_IMAGE -> "Image"
            ApplicationInfo.CATEGORY_MAPS -> "Maps"
            ApplicationInfo.CATEGORY_NEWS -> "News"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            else -> "Other"
        }
    }

    private fun PackageManager.getApplicationInfoCompat(packageName: String): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            getApplicationInfo(packageName, 0)
        }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}
