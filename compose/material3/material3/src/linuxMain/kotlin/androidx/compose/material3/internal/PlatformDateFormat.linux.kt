/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalTime::class)

package androidx.compose.material3.internal

import androidx.compose.material3.CalendarLocale
import kotlin.time.ExperimentalTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern

internal actual class PlatformDateFormat actual constructor(private val locale: CalendarLocale) {
    actual val firstDayOfWeek: Int = 1 // Monday by default on Linux

    actual val weekdayNames: List<Pair<String, String>> = listOf(
        "Monday" to "M",
        "Tuesday" to "T",
        "Wednesday" to "W",
        "Thursday" to "T",
        "Friday" to "F",
        "Saturday" to "S",
        "Sunday" to "S"
    )

    @OptIn(FormatStringsInDatetimeFormats::class)
    actual fun formatWithPattern(
        utcTimeMillis: Long,
        pattern: String,
        cache: MutableMap<String, Any>
    ): String {
        val date = Instant
            .fromEpochMilliseconds(utcTimeMillis)
            .toLocalDateTime(TimeZone.UTC)
        // Clean up common patterns that byUnicodePattern doesn't support or needs adjustments for
        val normalizedPattern = pattern
            .replace("MMMM", "MM")
            .replace("MMM", "MM")
            .replace("EEEE", "EE")
            .replace("EEE", "EE")
        return date.format(LocalDateTime.Format { byUnicodePattern(normalizedPattern) })
    }

    actual fun formatWithSkeleton(
        utcTimeMillis: Long,
        skeleton: String,
        cache: MutableMap<String, Any>
    ): String {
        return formatWithPattern(utcTimeMillis, "yyyy-MM-dd", cache)
    }

    @OptIn(FormatStringsInDatetimeFormats::class)
    actual fun parse(
        date: String,
        pattern: String,
        locale: CalendarLocale,
        cache: MutableMap<String, Any>
    ): CalendarDate? {
        return try {
            val normalizedPattern = pattern
                .replace("MMMM", "MM")
                .replace("MMM", "MM")
                .replace("EEEE", "EE")
                .replace("EEE", "EE")
            LocalDate.parse(
                input = date,
                format = LocalDate.Format {
                    byUnicodePattern(normalizedPattern)
                }
            ).atTime(Midnight)
                .toInstant(TimeZone.UTC)
                .toCalendarDate(TimeZone.UTC)
        } catch (e: Throwable) {
            null
        }
    }

    actual fun getDateInputFormat(): DateInputFormat {
        return datePatternAsInputFormat("yyyy-MM-dd")
    }

    actual fun is24HourFormat(): Boolean = true
}
