package com.familyshield.mobile.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val messageTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val clockTimePattern = Regex("""\b(\d{1,2}):(\d{2})(?::\d{2}(?:\.\d+)?)?""")
private val shortOffsetPattern = Regex("""([+-]\d{2})$""")

fun formatMessageTime(value: String, zoneId: ZoneId = ZoneId.systemDefault()): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return ""

    parseOffsetDateTime(trimmed)?.let { return it.toInstant().atZone(zoneId).toLocalTime().format(messageTimeFormatter) }
    runCatching { Instant.parse(trimmed).atZone(zoneId).toLocalTime().format(messageTimeFormatter) }.getOrNull()?.let { return it }
    parseLocalDateTime(trimmed)?.let { return it.toLocalTime().format(messageTimeFormatter) }
    parseClockTime(trimmed)?.let { return it.format(messageTimeFormatter) }

    return trimmed.take(8)
}

fun messageLocalDate(value: String, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null

    parseOffsetDateTime(trimmed)?.let { return it.toInstant().atZone(zoneId).toLocalDate() }
    runCatching { Instant.parse(trimmed).atZone(zoneId).toLocalDate() }.getOrNull()?.let { return it }
    parseLocalDateTime(trimmed)?.let { return it.toLocalDate() }

    return null
}

fun formatMessageDate(value: String, zoneId: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    val date = messageLocalDate(value, zoneId) ?: return value.trim().take(10)
    return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
}

private fun parseOffsetDateTime(value: String): OffsetDateTime? {
    val normalized = value.replace(' ', 'T').replace(shortOffsetPattern, "$1:00")
    return runCatching { OffsetDateTime.parse(normalized) }.getOrNull()
}

private fun parseLocalDateTime(value: String): LocalDateTime? {
    val normalized = value.replace(' ', 'T')
    return runCatching { LocalDateTime.parse(normalized) }.getOrNull()
}

private fun parseClockTime(value: String): LocalTime? {
    val match = clockTimePattern.find(value) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    return runCatching { LocalTime.of(hour, minute) }.getOrNull()
}
