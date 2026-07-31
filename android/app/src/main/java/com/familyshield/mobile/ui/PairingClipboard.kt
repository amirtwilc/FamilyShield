package com.familyshield.mobile.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle

private val pairingCodePattern = Regex("[0-9]{6}")

internal fun pairingCodeFromClipboard(text: CharSequence?): String? =
    text?.toString()?.trim()?.takeIf(pairingCodePattern::matches)

internal fun pairingCodeEditOrNull(text: String): String? =
    text.takeIf { candidate ->
        candidate.length <= 6 && candidate.all { it in '0'..'9' }
    }

internal fun copySensitiveText(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text).apply {
        description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}
