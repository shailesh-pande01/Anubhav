package com.example.anubhav.core.utils

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object RelativeTimeFormatter {

    fun format(timestampString: String?): String {
        if (timestampString.isNullOrBlank()) return ""

        val postInstant = parseInstant(timestampString) ?: return ""
        val now = Instant.now()
        val duration = Duration.between(postInstant, now)

        val seconds = duration.seconds
        if (seconds < 60) {
            return "just now"
        }

        val minutes = duration.toMinutes()
        if (minutes < 60) {
            return "${minutes}m"
        }

        val hours = duration.toHours()
        if (hours < 24) {
            return "${hours}h"
        }

        val days = duration.toDays()
        if (days == 1L) {
            return "Yesterday"
        }
        if (days < 7) {
            return "${days}d"
        }
        if (days < 30) {
            val weeks = days / 7
            return "${weeks}w"
        }

        // Older than a month: format as "MMM d" or "MMM d, yyyy"
        val zonedDateTime = postInstant.atZone(ZoneId.systemDefault())
        val currentYear = ZonedDateTime.now().year
        val pattern = if (zonedDateTime.year == currentYear) "MMM d" else "MMM d, yyyy"
        return zonedDateTime.format(DateTimeFormatter.ofPattern(pattern))
    }

    private fun parseInstant(timestamp: String): Instant? {
        return try {
            Instant.parse(timestamp)
        } catch (_: DateTimeParseException) {
            try {
                // Try parsing offset date time format e.g. "2026-09-05T01:34:00+00:00"
                ZonedDateTime.parse(timestamp).toInstant()
            } catch (_: Exception) {
                null
            }
        }
    }
}
