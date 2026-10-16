package com.sonyphototransfer.app.ui

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

/** "2026-10-01T10:00:05" → "2026-10-01 10:00:05"; the camera gives local time without zone. */
fun formatCameraDate(date: String?): String = date?.take(19)?.replace('T', ' ') ?: "—"
