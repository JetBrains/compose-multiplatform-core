/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.appfunctions.schema.notes.translators

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionData
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RequiresApi(33)
internal object DateTimeTranslator {

    fun upgradeToInstant(legacyDateTime: AppFunctionData): Instant {
        val timeZone = checkNotNull(legacyDateTime.getString("timeZone"))
        val zoneId = ZoneId.of(timeZone)

        val date = checkNotNull(legacyDateTime.getAppFunctionData("date"))
        val year = checkNotNull(date.getInt("year"))
        val month = checkNotNull(date.getInt("month"))
        val day = checkNotNull(date.getInt("day"))
        val localDate = LocalDate.of(year, month, day)

        val timeOfDay = checkNotNull(legacyDateTime.getAppFunctionData("timeOfDay"))
        val legacyHours = checkNotNull(timeOfDay.getInt("hours"))
        val legacyMinutes = checkNotNull(timeOfDay.getInt("minutes"))
        val legacySeconds = checkNotNull(timeOfDay.getInt("seconds"))
        val legacyNanos = checkNotNull(timeOfDay.getInt("nanos"))
        val localTime = LocalTime.of(legacyHours, legacyMinutes, legacySeconds, legacyNanos)

        val localDateTime = LocalDateTime.of(localDate, localTime)
        val zonedDateTime = ZonedDateTime.of(localDateTime, zoneId)
        return zonedDateTime.toInstant()
    }

    fun downgradeToDateTime(instant: Instant, zoneId: ZoneId): AppFunctionData {
        val zonedDateTime = instant.atZone(zoneId)

        val localDateTime = zonedDateTime.toLocalDateTime()
        val localDate = localDateTime.toLocalDate()
        val localTime = localDateTime.toLocalTime()

        val dateData =
            AppFunctionData.Builder(qualifiedName = "")
                .setInt("year", localDate.year)
                .setInt("month", localDate.monthValue)
                .setInt("day", localDate.dayOfMonth)
                .build()

        val timeOfDayData =
            AppFunctionData.Builder(qualifiedName = "")
                .setInt("hours", localTime.hour)
                .setInt("minutes", localTime.minute)
                .setInt("seconds", localTime.second)
                .setInt("nanos", localTime.nano)
                .build()

        return AppFunctionData.Builder(qualifiedName = "", id = "")
            .setString("timeZone", zoneId.id)
            .setAppFunctionData("date", dateData)
            .setAppFunctionData("timeOfDay", timeOfDayData)
            .build()
    }
}
