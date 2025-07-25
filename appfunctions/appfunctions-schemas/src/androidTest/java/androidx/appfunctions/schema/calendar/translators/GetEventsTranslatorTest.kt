/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.appfunctions.schema.calendars.translators

import androidx.appfunctions.EventAttendeeStatus
import androidx.appfunctions.EventStatus
import androidx.appfunctions.LegacyEvent
import androidx.appfunctions.schema.TranslatorTestUtils
import androidx.appfunctions.schema.calendars.AppFunctionCalendarEvent
import androidx.appfunctions.toLegacyDateTime
import androidx.test.filters.SdkSuppress
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

@SdkSuppress(minSdkVersion = 33)
class GetEventsTranslatorTest {
    private val translatorTestUtils = TranslatorTestUtils(GetEventsTranslator())

    @Test
    fun upgradeRequest_allFields() {
        val eventIds = listOf("1", "2")

        val expectedJetpackParams = GetCalendarEventsAppFunctionParams(calendarEventIds = eventIds)

        translatorTestUtils.assertUpgradeRequestTranslation(
            "eventIds",
            eventIds,
            expectedJetpackParams
        )
    }

    @Test
    fun downgradeRequest_allFields() {
        val jetpackEventIds = listOf("1", "2")
        val jetpackParams =
            GetCalendarEventsAppFunctionParams( // Use concrete class
                calendarEventIds = jetpackEventIds
            )

        val expectedLegacyEventIds = jetpackEventIds

        translatorTestUtils.assertDowngradeRequestTranslation(
            "eventIds",
            jetpackParams,
            expectedLegacyEventIds
        )
    }

    @Test
    fun upgradeResponse_allFields() {
        val zoneId = ZoneId.of("America/Los_Angeles")
        val startZdt = ZonedDateTime.of(2025, 10, 21, 14, 10, 20, 123000000, zoneId)
        val endZdt = ZonedDateTime.of(2025, 10, 21, 15, 30, 40, 456000000, zoneId)

        val legacyEvent =
            LegacyEvent(
                id = "event-all",
                title = "Team Sync (All Fields)",
                description = "Detailed discussion about Q3 goals.",
                startDate = startZdt.toLegacyDateTime(),
                endDate = endZdt.toLegacyDateTime(),
                attendeeIds = listOf("user1@example.com"),
                allDay = true,
                location = "Board Room 1",
                recurrenceSchedule = "RRULE:FREQ=DAILY;COUNT=5",
                eventStatus = EventStatus.EVENT_TENTATIVE,
                isReadOnly = false,
                isInPublicCalendar = true,
                isOrganizer = false,
                selfAttendeeStatus = EventAttendeeStatus.STATUS_DECLINED,
            )
        val legacyEventList = listOf(legacyEvent)

        val expectedUpgradedEvent =
            AppFunctionCalendarEventImpl(
                id = "event-all",
                title = "Team Sync (All Fields)",
                description = "Detailed discussion about Q3 goals.",
                startsAt = startZdt.toInstant(),
                endsAt = endZdt.toInstant(),
                startTimeZone = zoneId,
                endTimeZone = zoneId,
                isAllDay = true,
                location = "Board Room 1",
                recurrenceSchedule = "RRULE:FREQ=DAILY;COUNT=5",
                status = AppFunctionCalendarEvent.EVENT_TENTATIVE,
                isReadOnly = false,
                isInPublicCalendar = true,
                isOrganizer = false,
                selfAttendeeStatus = AppFunctionCalendarEvent.ATTENDEE_DECLINED,
            )
        val expectedUpgradedResponse =
            GetCalendarEventsAppFunctionResponse(calendarEvents = listOf(expectedUpgradedEvent))

        translatorTestUtils.assertUpgradeResponseTranslation(
            legacyEventList,
            expectedUpgradedResponse
        )
    }

    @Test
    fun upgradeResponse_optionalFieldsNotSet() {
        val zoneId = ZoneId.of("Europe/London")
        val startZdt = ZonedDateTime.of(2025, 11, 1, 9, 1, 2, 3000000, zoneId)
        val endZdt = ZonedDateTime.of(2025, 11, 1, 9, 16, 7, 8000000, zoneId)

        val legacyEvent =
            LegacyEvent(
                id = "event-opt",
                title = "Quick Check-in",
                startDate = startZdt.toLegacyDateTime(),
                endDate = endZdt.toLegacyDateTime(),
            )
        val legacyEventList = listOf(legacyEvent)

        val expectedUpgradedEvent =
            AppFunctionCalendarEventImpl(
                id = "event-opt",
                title = "Quick Check-in",
                description = null,
                startsAt = startZdt.toInstant(),
                endsAt = endZdt.toInstant(),
                startTimeZone = zoneId,
                endTimeZone = zoneId,
                isAllDay = false,
                location = null,
                recurrenceSchedule = null,
                status = null,
                isReadOnly = false,
                isInPublicCalendar = false,
                isOrganizer = false,
                selfAttendeeStatus = null,
            )
        val expectedUpgradedResponse =
            GetCalendarEventsAppFunctionResponse(calendarEvents = listOf(expectedUpgradedEvent))

        translatorTestUtils.assertUpgradeResponseTranslation(
            legacyEventList,
            expectedUpgradedResponse
        )
    }

    @Test
    fun downgradeResponse_allFields() {
        val zoneId = ZoneId.of("America/Los_Angeles")
        val startZdt = ZonedDateTime.of(2025, 10, 21, 14, 10, 20, 123000000, zoneId)
        val endZdt = ZonedDateTime.of(2025, 10, 21, 15, 30, 40, 456000000, zoneId)

        val jetpackEvent =
            AppFunctionCalendarEventImpl(
                id = "event-all",
                title = "Team Sync (All Fields)",
                description = "Detailed discussion about Q3 goals.",
                startsAt = startZdt.toInstant(),
                endsAt = endZdt.toInstant(),
                startTimeZone = zoneId,
                endTimeZone = zoneId,
                isAllDay = true,
                location = "Board Room 1",
                recurrenceSchedule = "RRULE:FREQ=DAILY;COUNT=5",
                status = AppFunctionCalendarEvent.EVENT_CONFIRMED,
                isReadOnly = true,
                isInPublicCalendar = true,
                isOrganizer = true,
                selfAttendeeStatus = AppFunctionCalendarEvent.ATTENDEE_ACCEPTED,
            )
        val jetpackResponse =
            GetCalendarEventsAppFunctionResponse(calendarEvents = listOf(jetpackEvent))

        val expectedDowngradedEvent =
            LegacyEvent(
                id = "event-all",
                title = "Team Sync (All Fields)",
                description = "Detailed discussion about Q3 goals.",
                startDate = startZdt.toLegacyDateTime(),
                endDate = endZdt.toLegacyDateTime(),
                attendeeIds = emptyList(),
                allDay = true,
                location = "Board Room 1",
                recurrenceSchedule = "RRULE:FREQ=DAILY;COUNT=5",
                eventStatus = EventStatus.EVENT_CONFIRMED,
                isReadOnly = true,
                isInPublicCalendar = true,
                isOrganizer = true,
                selfAttendeeStatus = EventAttendeeStatus.STATUS_ACCEPTED,
            )
        val expectedDowngradedEventList = listOf(expectedDowngradedEvent)

        translatorTestUtils.assertDowngradeResponseTranslation(
            jetpackResponse,
            expectedDowngradedEventList
        )
    }

    @Test
    fun downgradeResponse_optionalFieldsNotSet() {
        val zoneId = ZoneId.of("Europe/London")
        val startZdt = ZonedDateTime.of(2025, 11, 1, 9, 1, 2, 3000000, zoneId)
        val endZdt = ZonedDateTime.of(2025, 11, 1, 9, 16, 7, 8000000, zoneId)

        val jetpackEvent =
            AppFunctionCalendarEventImpl(
                id = "event-opt",
                title = "Quick Check-in",
                startsAt = startZdt.toInstant(),
                endsAt = endZdt.toInstant(),
                startTimeZone = zoneId,
                endTimeZone = zoneId,
            )
        val jetpackResponse =
            GetCalendarEventsAppFunctionResponse(calendarEvents = listOf(jetpackEvent))

        val expectedDowngradedEvent =
            LegacyEvent(
                id = "event-opt",
                title = "Quick Check-in",
                description = null,
                startDate = startZdt.toLegacyDateTime(),
                endDate = endZdt.toLegacyDateTime(),
                attendeeIds = emptyList(),
                allDay = false,
                location = null,
                recurrenceSchedule = null,
                eventStatus = null,
                isReadOnly = false,
                isInPublicCalendar = false,
                isOrganizer = false,
                selfAttendeeStatus = null,
            )
        val expectedDowngradedEventList = listOf(expectedDowngradedEvent)

        translatorTestUtils.assertDowngradeResponseTranslation(
            jetpackResponse,
            expectedDowngradedEventList
        )
    }
}
