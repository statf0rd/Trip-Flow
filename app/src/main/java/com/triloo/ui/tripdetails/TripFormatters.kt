package com.triloo.ui.tripdetails

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun formatDurationLabel(minutes: Int): String {
    if (minutes < 60) return "$minutes мин"
    val hoursValue = minutes / 60.0
    val hoursInt = minutes / 60
    val hoursText = if (hoursValue % 1.0 == 0.0) {
        hoursInt.toString()
    } else {
        String.format(Locale.US, "%.1f", hoursValue).trimEnd('0').trimEnd('.')
    }
    val hoursLabel = pluralizeHours(hoursInt)
    return "$hoursText $hoursLabel"
}

fun formatTimeDisplay(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    val format = detectTimeFormat(trimmed)
    val parsed = parseTime(trimmed.uppercase(Locale.US), format) ?: return trimmed
    return formatMinutesToTime(parsed.hour * 60 + parsed.minute, format)
}

fun parseTimeToMinutes(value: String): Int? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    val format = detectTimeFormat(trimmed)
    val parsed = parseTime(trimmed.uppercase(Locale.US), format) ?: return null
    return parsed.hour * 60 + parsed.minute
}

fun formatMinutesToTime(minutes: Int, format: TimeFormat): String {
    val normalized = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60)
    val hours = normalized / 60
    val mins = normalized % 60
    val time = LocalTime.of(hours, mins)
    val pattern = if (format == TimeFormat.HOURS_24) "HH:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}

fun detectTimeFormat(value: String): TimeFormat {
    val upper = value.uppercase(Locale.US)
    return if (upper.contains("AM") || upper.contains("PM")) {
        TimeFormat.HOURS_12
    } else {
        TimeFormat.HOURS_24
    }
}

private fun parseTime(value: String, format: TimeFormat): LocalTime? {
    val patterns = if (format == TimeFormat.HOURS_24) {
        listOf("H:mm", "HH:mm")
    } else {
        listOf("h:mm a", "hh:mm a", "h:mma", "hh:mma")
    }
    return patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            LocalTime.parse(value, DateTimeFormatter.ofPattern(pattern, Locale.US))
        }.getOrNull()
    }
}

private fun pluralizeHours(count: Int): String {
    return when {
        count % 100 in 11..19 -> "часов"
        count % 10 == 1 -> "час"
        count % 10 in 2..4 -> "часа"
        else -> "часов"
    }
}
