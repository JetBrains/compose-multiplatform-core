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
 * distributed under the License is distributed on an "AS IS" BASIS * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.appfunctions.schema.tasks

import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP
import androidx.annotation.StringDef
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionOpenable
import androidx.appfunctions.AppFunctionSchemaDefinition
import androidx.appfunctions.AppFunctionSerializableInterface
import androidx.appfunctions.schema.tasks.AppFunctionTask.Schedule
import java.time.Instant
import java.time.ZoneId

/**
 * The category name of Tasks related app functions.
 *
 * Example of apps that can support this category of schema include task taking apps.
 *
 * The category is used to search app functions related to tasks, using
 * [androidx.appfunctions.AppFunctionSearchSpec.schemaCategory].
 */
public const val APP_FUNCTION_SCHEMA_CATEGORY_TASKS: String = "tasks"

/** Finds tasks matching the given parameters. */
@AppFunctionSchemaDefinition(
    name = "findTasks",
    version = FindTasksAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_TASKS
)
public interface FindTasksAppFunction<
    Parameters : FindTasksAppFunction.Parameters,
    Response : FindTasksAppFunction.Response
> {
    /**
     * Finds tasks matching the given parameters.
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param parameters The parameters for finding tasks.
     * @return The response including a list of tasks that match the parameters.
     */
    public suspend fun findTasks(
        appFunctionContext: AppFunctionContext,
        parameters: Parameters,
    ): Response

    /** The parameters for finding tasks. */
    @AppFunctionSerializableInterface
    public interface Parameters {
        /**
         * The search query to be processed. A null value means to query all tasks with the
         * remaining parameters.
         *
         * This parameter is analogous to the caller typing a query into a search box. The app
         * providing the app function has full control over how the query is interpreted and
         * processed; for example by name matching or semantic analysis.
         */
        public val query: String?
            get() = null

        /** The status of the task. */
        @TaskStatus
        public val status: String?
            get() = null

        /** The lower bound (inclusive) of the [AppFunctionTask.schedule] time. */
        public val scheduledAfter: Instant?
            get() = null

        /** The upper bound (exclusive) of the [AppFunctionTask.schedule] time. */
        public val scheduledBefore: Instant?
            get() = null
    }

    /** The response including the list of tasks that match the parameters. */
    @AppFunctionSerializableInterface
    public interface Response {
        public val tasks: List<AppFunctionTask>
    }

    public companion object {
        /** Current schema version. */
        @RestrictTo(LIBRARY_GROUP) internal const val SCHEMA_VERSION: Int = 2
    }
}

/** Creates an [AppFunctionTask]. */
@AppFunctionSchemaDefinition(
    name = "createTask",
    version = CreateTaskAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_TASKS
)
public interface CreateTaskAppFunction<
    Parameters : CreateTaskAppFunction.Parameters,
    Response : CreateTaskAppFunction.Response
> {

    /**
     * Creates an [AppFunctionTask].
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param parameters The parameters for creating the task.
     * @return The response including the created task.
     */
    public suspend fun createTask(
        appFunctionContext: AppFunctionContext,
        parameters: Parameters,
    ): Response

    /** The parameters for creating a task. */
    @AppFunctionSerializableInterface
    public interface Parameters {
        /** The title of the task. */
        public val title: String

        /** The description of the task. */
        public val description: String?
            get() = null

        /** The status of the task. */
        @TaskStatus public val status: String

        /** The scheduling configuration of the task. */
        public val schedule: Schedule?

        /**
         * An optional UUID for this task provided by the caller. If provided, the caller can use
         * this UUID as well as the returned [AppFunctionTask.id] to reference this specific task in
         * subsequent requests, such as a request to update the task that was just created.
         *
         * To support [externalUuid], the application should maintain a mapping between the
         * [externalUuid] and the internal id of this task. This allows the application to retrieve
         * the correct task when the caller references it using the provided `externalUuid` in
         * subsequent requests.
         *
         * If the `externalUuid` is not provided by the caller in the creation request, the
         * application should expect subsequent requests from the caller to reference the task using
         * the application generated [AppFunctionTask.id].
         */
        public val externalUuid: String?
            get() = null
    }

    /** The response including the created task. */
    @AppFunctionSerializableInterface
    public interface Response {
        /** The created task. */
        public val createdTask: AppFunctionTask
    }

    public companion object {
        /** Current schema version. */
        @RestrictTo(LIBRARY_GROUP) internal const val SCHEMA_VERSION: Int = 2
    }
}

/**
 * Shows the task.
 *
 * @param Parameters The parameters of the task to show.
 * @param Response The response including the [AppFunctionOpenable] to show the task.
 */
@AppFunctionSchemaDefinition(
    name = "showTask",
    version = ShowTaskAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_TASKS
)
public interface ShowTaskAppFunction<
    Parameters : ShowTaskAppFunction.Parameters,
    Response : ShowTaskAppFunction.Response
> {
    /**
     * Shows the task.
     *
     * The implementing app should throw an appropriate subclass of
     * [androidx.appfunctions.AppFunctionException] in exceptional cases.
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param parameters The params of the task to show.
     * @return The response including the intent to show the task.
     */
    public suspend fun showTask(
        appFunctionContext: AppFunctionContext,
        parameters: Parameters
    ): Response

    /** The parameters for [showTask]. */
    @AppFunctionSerializableInterface
    public interface Parameters {
        /**
         * The [AppFunctionTask.id] of the task to show.
         *
         * [androidx.appfunctions.AppFunctionElementNotFoundException] should be thrown when a task
         * with the specified taskId doesn't exist.
         */
        public val taskId: String
    }

    /** The [AppFunctionOpenable] response for [showTask]. */
    @AppFunctionSerializableInterface public interface Response : AppFunctionOpenable

    public companion object {
        /** Current schema version. */
        @RestrictTo(LIBRARY_GROUP) internal const val SCHEMA_VERSION: Int = 2
    }
}

/** A task entity. */
@AppFunctionSerializableInterface
public interface AppFunctionTask {
    /** The ID of the task. */
    public val id: String

    /** The title of the task. */
    public val title: String

    /** The description of the task. */
    public val description: String?
        get() = null

    /** The status of the task. */
    @TaskStatus public val status: String

    /** The scheduling configuration of the task. */
    public val schedule: Schedule?

    /**
     * Describes the scheduling configuration of the task, including date, time and recurrence
     * details.
     */
    // TODO: refactor this along with Calendar's schedule fields after alignment.
    @AppFunctionSerializableInterface
    public interface Schedule {
        /**
         * The date and time that the task is scheduled for, if any. If [isAllDay] is true, this
         * representation is converted to date in the [timeZone], and the time-of-day component is
         * disregarded.
         */
        public val instant: Instant

        /** The time zone of the task. This applies to all events in [recurrenceSchedule]. */
        public val timeZone: ZoneId

        /** Whether the task is an all-day task. */
        // TODO: refactor this along with Calendar
        public val isAllDay: Boolean
            get() = false

        /**
         * Defines the rules for the recurrence schedule of the task, following the Internet
         * Calendaring and Scheduling Core Object Specification (RFC5545).
         *
         * The string should include the RRULE, and potentially EXRULE, RDATE, and EXDATE lines,
         * separated by newline characters (`\n`). These lines specify the recurrence pattern,
         * exceptions, and additional dates for the task.
         *
         * Example: For a weekly task occurring every Tuesday:
         * ```
         * RRULE:FREQ=WEEKLY;BYDAY=TU
         * ```
         *
         * @see <a href="https://tools.ietf.org/html/rfc5545">RFC 5545</a>
         */
        public val recurrenceSchedule: String?
            get() = null
    }

    public companion object {
        /** The task is pending. */
        public const val STATUS_PENDING: String = "PENDING"
        /** The task is completed. */
        public const val STATUS_COMPLETED: String = "COMPLETED"
    }
}

/** The status of the task. */
@StringDef(AppFunctionTask.STATUS_PENDING, AppFunctionTask.STATUS_COMPLETED)
@Retention(AnnotationRetention.SOURCE)
private annotation class TaskStatus {}
