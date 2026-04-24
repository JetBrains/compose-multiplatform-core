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

package androidx.compose.ui.window.v2

import androidx.compose.runtime.Immutable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.DpSize


/**
 * Defines limits on the window size.
 *
 * Note: this class may be moved to `androidx.compose.ui.window` before stabilization.
 */
@ExperimentalComposeUiApi
@Immutable
class WindowSizeLimits(
    /**
     * The minimum size of the window; [DpSize.Unspecified] means no minimum size.
     */
    val min: DpSize = DpSize.Unspecified,

    /**
     * The maximum size of the window; [DpSize.Unspecified] means no maximum size.
     */
    val max: DpSize = DpSize.Unspecified
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WindowSizeLimits) return false

        if (min != other.min) return false
        if (max != other.max) return false

        return true
    }

    override fun hashCode(): Int {
        var result = min.hashCode()
        result = 31 * result + max.hashCode()
        return result
    }

    override fun toString(): String {
        return "WindowSizeLimits(min=$min, max=$max)"
    }

    @ExperimentalComposeUiApi
    companion object {
        /**
         * A [WindowSizeLimits] that has no limits.
         */
        val Unlimited = WindowSizeLimits(DpSize.Unspecified, DpSize.Unspecified)
    }
}