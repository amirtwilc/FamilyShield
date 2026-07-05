package com.familyshield.app.kid

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.familyshield.app.net.AppUsageReportItem
import java.time.LocalDate
import java.time.ZoneId

object AppUsageTelemetry {
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
        val stats = usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end).orEmpty()
        val pm = app.packageManager

        return stats.asSequence()
            .filter { it.totalTimeInForeground > 0 }
            .filter { it.packageName != app.packageName }
            .mapNotNull { stat ->
                val info = runCatching { pm.getApplicationInfo(stat.packageName, 0) }.getOrNull()
                    ?: return@mapNotNull null
                if (pm.getLaunchIntentForPackage(stat.packageName) == null) return@mapNotNull null
                val minutes = (stat.totalTimeInForeground / 60_000L).toInt().coerceAtLeast(1)
                AppUsageReportItem(
                    app = appLabel(pm, info).take(64),
                    category = appCategory(info).take(32),
                    minutes = minutes.coerceIn(0, 1440),
                    day = day.toString(),
                )
            }
            .sortedByDescending { it.minutes }
            .take(maxItems)
            .toList()
    }

    private fun appLabel(pm: PackageManager, info: ApplicationInfo): String {
        val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrNull()
        return label?.takeIf { it.isNotBlank() } ?: info.packageName
    }

    private fun appCategory(info: ApplicationInfo): String {
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
}
