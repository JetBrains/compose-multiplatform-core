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

package androidx.appfunctions.schema.calendars.translators

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.schema.calendars.AppFunctionCalendarEvent
import androidx.appfunctions.schema.calendars.AppFunctionCalendarEvent.Companion.AttendeeStatus
import androidx.appfunctions.schema.notes.translators.DateTimeTranslator
import java.time.ZoneId

@RequiresApi(33)
internal object EventTranslator {
    fun upgrade(legacyEvent: AppFunctionData): AppFunctionCalendarEventImpl {
        val legacyStateDate = checkNotNull(legacyEvent.getAppFunctionData("startDate"))
        val legacyEndDate = checkNotNull(legacyEvent.getAppFunctionData("endDate"))
        // Assuming the timezone in startDate is the same as the one in endDate.
        val timeZone = checkNotNull(legacyStateDate.getString("timeZone"))
        return AppFunctionCalendarEventImpl(
            id = legacyEvent.id,
            title = checkNotNull(legacyEvent.getString("title")),
            description = legacyEvent.getString("description"),
            startsAt = DateTimeTranslator.upgradeToInstant(legacyStateDate),
            endsAt = DateTimeTranslator.upgradeToInstant(legacyEndDate),
            startTimeZone = ZoneId.of(timeZone),
            endTimeZone = ZoneId.of(timeZone),
            isAllDay = legacyEvent.getBoolean("allDay"),
            location = legacyEvent.getString("location"),
            recurrenceSchedule = legacyEvent.getString("recurrenceSchedule"),
            status = legacyEvent.getString("eventStatus")?.let { upgradeEventStatus(it) },
            isReadOnly =
                legacyEvent.getBoolean(
                    "isReadOnly",
                ),
            isInPublicCalendar = legacyEvent.getBoolean("isInPublicCalendar"),
            isOrganizer = legacyEvent.getBoolean("isOrganizer"),
            selfAttendeeStatus =
                legacyEvent.getString("selfAttendeeStatus")?.let { upgradeAttendeeStatus(it) }
        )
    }

    fun downgrade(event: AppFunctionCalendarEventImpl): AppFunctionData {
        return AppFunctionData.Builder(qualifiedName = "", id = event.id)
            .setString("title", event.title)
            .setAppFunctionData(
                "startDate",
                DateTimeTranslator.downgradeToDateTime(event.startsAt, event.startTimeZone)
            )
            .setAppFunctionData(
                "endDate",
                DateTimeTranslator.downgradeToDateTime(event.endsAt, event.endTimeZone)
            )
            .setString("timeZone", event.startTimeZone.id)
            .setBoolean("allDay", event.isAllDay)
            .setBoolean("isReadOnly", event.isReadOnly)
            .setBoolean("isInPublicCalendar", event.isInPublicCalendar)
            .setBoolean("isOrganizer", event.isOrganizer)
            .apply {
                event.description?.let { setString("description", it) }
                event.location?.let { setString("location", it) }
                event.recurrenceSchedule?.let { setString("recurrenceSchedule", it) }
                event.selfAttendeeStatus?.let {
                    setString("selfAttendeeStatus", downgradeAttendeeStatus(it))
                }
                event.status?.let { setString("eventStatus", downgradeEventStatus(it)) }
            }
            .build()
    }

    @AttendeeStatus
    private fun upgradeAttendeeStatus(attendeeStatus: String): String {
        return when (attendeeStatus) {
            "STATUS_ACCEPTED" -> AppFunctionCalendarEvent.ATTENDEE_ACCEPTED
            "STATUS_DECLINED" -> AppFunctionCalendarEvent.ATTENDEE_DECLINED
            "STATUS_TENTATIVE" -> AppFunctionCalendarEvent.ATTENDEE_TENTATIVE
            else -> throw IllegalArgumentException("Unexpected attendee status: $attendeeStatus")
        }
    }

    private fun downgradeAttendeeStatus(@AttendeeStatus attendeeStatus: String): String {
        return when (attendeeStatus) {
            AppFunctionCalendarEvent.ATTENDEE_ACCEPTED -> "STATUS_ACCEPTED"
            AppFunctionCalendarEvent.ATTENDEE_DECLINED -> "STATUS_DECLINED"
            AppFunctionCalendarEvent.ATTENDEE_TENTATIVE -> "STATUS_TENTATIVE"
            else -> throw IllegalArgumentException("Unexpected attendee status: $attendeeStatus")
        }
    }

    private fun upgradeEventStatus(legacyStatus: String): String? {
        return when (legacyStatus) {
            "EVENT_CONFIRMED" -> AppFunctionCalendarEvent.EVENT_CONFIRMED
            "EVENT_TENTATIVE" -> AppFunctionCalendarEvent.EVENT_TENTATIVE
            "EVENT_CANCELLED" -> AppFunctionCalendarEvent.EVENT_CANCELLED
            "EVENT_UNSPECIFIED" -> null
            else -> throw IllegalArgumentException("Unexpected event status: $legacyStatus")
        }
    }

    private fun downgradeEventStatus(newStatus: String): String {
        return when (newStatus) {
            AppFunctionCalendarEvent.EVENT_CONFIRMED -> "EVENT_CONFIRMED"
            AppFunctionCalendarEvent.EVENT_TENTATIVE -> "EVENT_TENTATIVE"
            AppFunctionCalendarEvent.EVENT_CANCELLED -> "EVENT_CANCELLED"
            else -> throw IllegalArgumentException("Unexpected event status: $newStatus")
        }
    }
}
