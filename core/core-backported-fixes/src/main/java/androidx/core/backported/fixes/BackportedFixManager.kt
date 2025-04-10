/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.core.backported.fixes

import android.os.Build

/**
 * Reports the status of a known issue on this device.
 *
 * @param resolver a function that takes a [KnownIssue] and returns its [Status] on this device.
 *   This parameter is only used for testing. In normal flows, the empty constructor should be used.
 */
internal class BackportedFixManager(private val resolver: StatusResolver) {
    /** Creates a BackportedFixManager object using the default lookup strategy. */
    public constructor() :
        this(
            // TODO b/381267367 - Use Build.getBackportedFixStatus in when available.
            resolver = SystemPropertyResolver()
        )

    /**
     * Is the known issue fixed on this device.
     *
     * @param ki The known issue to check.
     */
    internal fun isFixed(ki: KnownIssue): Boolean {
        return when (getStatus(ki)) {
            Status.Unknown -> false
            Status.Fixed -> true
            Status.NotApplicable -> true
            Status.NotFixed -> false
        }
    }

    /**
     * The status of a known issue on this device.
     *
     * @param ki The known issue to check.
     */
    internal fun getStatus(ki: KnownIssue): Status {
        return when (ki) {
            // If the known issue needs special handling,
            // like when an issue only applies
            // to certain devices then call a method named after the issue id or using
            // a precondition names after the issue id.
            //
            // Issues are individually listed and use inline functions to make it easy for
            // the compiler to remove unused code.

            // keep-sorted start

            KnownIssue.KI_372917199 -> preconditionResolution(::pre372917199, ki)

            // keep-sorted end
            else -> defaultResolution(ki)
        }
    }

    private inline fun preconditionResolution(precondition: () -> Boolean, ki: KnownIssue): Status {
        if (precondition.invoke()) {
            return defaultResolution(ki)
        }
        return Status.NotApplicable
    }

    private fun defaultResolution(ki: KnownIssue): Status {
        return resolver.invoke(ki)
    }

    private fun pre372917199(): Boolean {
        return (Build.BRAND.equals("robolectric"))
    }
}

/** Function that takes a [KnownIssue] and returns its [Status] on this device. */
internal typealias StatusResolver = (KnownIssue) -> Status
