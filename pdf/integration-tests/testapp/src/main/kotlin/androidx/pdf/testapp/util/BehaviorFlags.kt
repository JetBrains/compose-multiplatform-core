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

package androidx.pdf.testapp.util

import android.os.Bundle

/**
 * A class for behavior flags used across the test app to toggle feature behavior. This class
 * provides a structured way to encapsulate feature flags.
 */
class BehaviorFlags private constructor(private val bundle: Bundle) {

    fun isCustomLinkHandlingEnabled(): Boolean {
        return bundle.getBoolean(KEY_CUSTOM_LINK_HANDLING_ENABLED, false)
    }

    fun toBundle(): Bundle = bundle

    /** A builder for constructing immutable [BehaviorFlags] instances. */
    class Builder {
        private val flags = Bundle()

        fun setCustomLinkHandlingEnabled(enabled: Boolean): Builder {
            flags.putBoolean(KEY_CUSTOM_LINK_HANDLING_ENABLED, enabled)
            return this
        }

        fun build(): BehaviorFlags {
            return BehaviorFlags(flags)
        }
    }

    companion object {
        private const val KEY_CUSTOM_LINK_HANDLING_ENABLED = "custom_link_handling_enabled"

        /** Create [BehaviorFlags] from a [Bundle], to deserialize flags in a fragment. */
        fun fromBundle(bundle: Bundle?): BehaviorFlags {
            return BehaviorFlags(bundle ?: Bundle())
        }
    }
}
