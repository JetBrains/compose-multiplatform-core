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
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.ExecuteAppFunctionResponse.Success.Companion.PROPERTY_RETURN_VALUE
import androidx.appfunctions.internal.Translator
import androidx.appfunctions.schema.calendars.AppFunctionCalendarEvent
import androidx.appfunctions.schema.calendars.AppFunctionCalendarEvent.Companion.AttendeeStatus
import androidx.appfunctions.schema.calendars.AppFunctionCalendarEvent.Companion.EventStatus
import androidx.appfunctions.schema.calendars.GetCalendarEventsAppFunction
import java.time.Instant
import java.time.ZoneId

@RequiresApi(33)
internal class GetEventsTranslator : Translator {
    override fun upgradeRequest(request: AppFunctionData): AppFunctionData {
        val eventIds = request.getStringList("eventIds")
        val parameters =
            GetCalendarEventsAppFunctionParams(calendarEventIds = eventIds ?: emptyList())
        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData(
                "parameters",
                AppFunctionData.serialize(
                    parameters,
                    GetCalendarEventsAppFunctionParams::class.java
                )
            )
            .build()
    }

    override fun upgradeResponse(response: AppFunctionData): AppFunctionData {
        val legacyEvents = checkNotNull(response.getAppFunctionDataList(PROPERTY_RETURN_VALUE))
        val upgradedEvents = legacyEvents.map { EventTranslator.upgrade(it) }

        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData(
                PROPERTY_RETURN_VALUE,
                AppFunctionData.Companion.serialize(
                    GetCalendarEventsAppFunctionResponse(upgradedEvents),
                    GetCalendarEventsAppFunctionResponse::class.java
                )
            )
            .build()
    }

    override fun downgradeRequest(request: AppFunctionData): AppFunctionData {
        val parametersData = checkNotNull(request.getAppFunctionData("parameters"))
        val getCalendarEventsAppFunctionParams =
            parametersData.deserialize(GetCalendarEventsAppFunctionParams::class.java)
        return AppFunctionData.Builder(qualifiedName = "")
            .setStringList("eventIds", getCalendarEventsAppFunctionParams.calendarEventIds)
            .build()
    }

    override fun downgradeResponse(response: AppFunctionData): AppFunctionData {
        val responseData = checkNotNull(response.getAppFunctionData(PROPERTY_RETURN_VALUE))
        val getEventsResponse =
            responseData.deserialize(GetCalendarEventsAppFunctionResponse::class.java)
        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionDataList(
                PROPERTY_RETURN_VALUE,
                getEventsResponse.calendarEvents.map { EventTranslator.downgrade(it) }
            )
            .build()
    }
}

@AppFunctionSerializable
internal data class GetCalendarEventsAppFunctionParams(
    override val calendarEventIds: List<String>
) : GetCalendarEventsAppFunction.Parameters

@AppFunctionSerializable
internal data class GetCalendarEventsAppFunctionResponse(
    override val calendarEvents: List<AppFunctionCalendarEventImpl>
) : GetCalendarEventsAppFunction.Response

@AppFunctionSerializable
internal data class AppFunctionCalendarEventImpl(
    override val id: String,
    override val title: String,
    override val description: String? = null,
    override val startsAt: Instant,
    override val endsAt: Instant,
    override val startTimeZone: ZoneId,
    override val endTimeZone: ZoneId,
    override val isAllDay: Boolean = false,
    override val location: String? = null,
    override val recurrenceSchedule: String? = null,
    @EventStatus override val status: String? = null,
    override val isReadOnly: Boolean = false,
    override val isInPublicCalendar: Boolean = false,
    override val isOrganizer: Boolean = false,
    @AttendeeStatus override val selfAttendeeStatus: String? = null
) : AppFunctionCalendarEvent
