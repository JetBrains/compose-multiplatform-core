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

package androidx.appfunctions.schema.calendars

import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP
import androidx.annotation.StringDef
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSchemaDefinition
import java.time.Instant
import java.time.ZoneId

/**
 * The category name of Calendar related app functions.
 *
 * Example of apps that can support this category of schema include calendar apps.
 *
 * The category is used to search app functions related to events, using
 * [androidx.appfunctions.AppFunctionSearchSpec.schemaCategory].
 */
public const val APP_FUNCTION_SCHEMA_CATEGORY_CALENDAR: String = "calendar"

/** Gets [AppFunctionCalendarEvent]s with the given IDs. */
@AppFunctionSchemaDefinition(
    name = "getEvents",
    version = GetCalendarEventsAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_CALENDAR
)
public interface GetCalendarEventsAppFunction<
    Parameters : GetCalendarEventsAppFunction.Parameters,
    Response : GetCalendarEventsAppFunction.Response
> {
    /**
     * Gets the events with the given IDs. Returns only the events found for the provided IDs. Does
     * not throw if some IDs are not found.
     *
     * The implementing app should throw an appropriate subclass of
     * [androidx.appfunctions.AppFunctionException] in exceptional cases (other than IDs not being
     * found).
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param parameters The parameters defining which events to get.
     * @return The response including the list of events that match the given IDs.
     */
    public suspend fun getCalendarEvents(
        appFunctionContext: AppFunctionContext,
        parameters: Parameters,
    ): Response

    /** The parameters defining the IDs of the events to get. */
    public interface Parameters {
        /** The IDs of the events to get. Can be application-generated IDs. */
        public val calendarEventIds: List<String>
    }

    /** The response including the list of events that match the given IDs. */
    public interface Response {
        /**
         * The list of events that match the given IDs. Will be empty if no matching events are
         * found.
         */
        public val calendarEvents: List<AppFunctionCalendarEvent>
    }

    public companion object {
        /** Current schema version. */
        @RestrictTo(LIBRARY_GROUP) internal const val SCHEMA_VERSION: Int = 2
    }
}

/** Represents a calendar event entity. */
public interface AppFunctionCalendarEvent {
    /** The unique ID of the event. */
    public val id: String

    /** The title of the event. */
    public val title: String

    /** A more detailed description of the event. */
    public val description: String?
        get() = null

    /**
     * The start time of the event. If [isAllDay] is true, this representation is converted to date
     * in the [timeZone], and the time-of-day component is disregarded.
     *
     * @see isAllDay
     */
    public val startsAt: Instant

    /**
     * The end time of the event. If [isAllDay] is true, this representation is converted to date in
     * the [timeZone], and the time-of-day component is disregarded.
     *
     * @see isAllDay
     */
    public val endsAt: Instant

    /** The time zone of the event. */
    public val timeZone: ZoneId

    // TODO: Add attendeeIds

    /** Indicates whether this event lasts for the entire day(s). */
    public val isAllDay: Boolean
        get() = false

    /** The geographical location associated with the event. */
    public val location: String?
        get() = null

    /**
     * Defines the rules for the recurrence schedule of the event, following the Internet
     * Calendaring and Scheduling Core Object Specification (RFC5545).
     *
     * The string should include the RRULE, and potentially EXRULE, RDATE, and EXDATE lines,
     * separated by newline characters (`\n`). These lines specify the recurrence pattern,
     * exceptions, and additional dates for the event.
     *
     * Example: For a weekly event occurring every Tuesday:
     * ```
     * RRULE:FREQ=WEEKLY;BYDAY=TU
     * ```
     *
     * @see <a href="https://tools.ietf.org/html/rfc5545">RFC 5545</a>
     */
    public val recurrenceSchedule: String?
        get() = null

    /** The status of the event. */
    @EventStatus
    public val status: String?
        get() = null

    /** Indicates whether the event details can be modified by the current user. */
    public val isReadOnly: Boolean
        get() = false

    /** Indicates whether the event belongs to a calendar that is publicly accessible. */
    public val isInPublicCalendar: Boolean
        get() = false

    /** Indicates whether the current user is the organizer of the event. */
    public val isOrganizer: Boolean
        get() = false

    /** The attendance status of the current user for this event (e.g., accepted, declined). */
    @AttendeeStatus
    public val selfAttendeeStatus: String?
        get() = null

    public companion object {
        /** The event is confirmed. */
        public const val EVENT_CONFIRMED: String = "CONFIRMED"
        /** The event is tentatively confirmed. */
        public const val EVENT_TENTATIVE: String = "TENTATIVE"
        /** The event is cancelled. */
        public const val EVENT_CANCELLED: String = "CANCELLED"

        /** The attendee accepted the event invitation. */
        public const val ATTENDEE_ACCEPTED: String = "ACCEPTED"
        /** The attendee declined the event invitation. */
        public const val ATTENDEE_DECLINED: String = "DECLINED"
        /** The attendee is uncertain about the event invitation. */
        public const val ATTENDEE_TENTATIVE: String = "TENTATIVE"

        /** Defines the allowed string constants for the status of an event. */
        @Retention(AnnotationRetention.SOURCE)
        @StringDef(EVENT_CONFIRMED, EVENT_TENTATIVE, EVENT_CANCELLED)
        internal annotation class EventStatus

        /**
         * Defines the allowed string constants for the attendance status of an attendee for an
         * event.
         */
        @Retention(AnnotationRetention.SOURCE)
        @StringDef(ATTENDEE_ACCEPTED, ATTENDEE_DECLINED, ATTENDEE_TENTATIVE)
        internal annotation class AttendeeStatus
    }
}
