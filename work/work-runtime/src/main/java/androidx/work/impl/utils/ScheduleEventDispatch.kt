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

package androidx.work.impl.utils

import androidx.work.Operation
import androidx.work.ScheduleEventListener
import androidx.work.WorkInfo
import androidx.work.WorkInfo.State.CANCELLED
import androidx.work.WorkInfo.State.ENQUEUED
import androidx.work.WorkInfo.State.FAILED

internal suspend fun ScheduleEventListener.dispatchScheduleEvents(work: List<WorkInfo>) {
    dispatchScheduleEvents(work, false)
}

/**
 * Dispatch the appropriate schedule event for a list of work infos that had their [WorkInfo.State]
 * modified due to an [Operation] or worker finishing. e.g. a cancel would cancel its dependents, a
 * worker finishing may unblock its dependents.
 *
 * This should not include work that was not modified. For example a worker failing will not modify
 * the state of a dependents that are [CANCELLED] and so we shouldn't send a
 * [ScheduleEventListener.onPrerequisiteFailed] event.
 *
 * @param work work infos that had its work state modified.
 * @param isEnqueue true if this is after the works are enqueued
 */
internal suspend fun ScheduleEventListener.dispatchScheduleEvents(
    work: List<WorkInfo>,
    isEnqueue: Boolean,
) {
    if (work.isEmpty()) {
        return
    }
    for (work in work) {
        if (isEnqueue) {
            onEnqueued(work)
        }
        when (work.state) {
            FAILED -> onPrerequisiteFailed(work)
            CANCELLED -> onCancelled(work)
            ENQUEUED -> onUnblocked(work)
            else -> {}
        }
    }
}
