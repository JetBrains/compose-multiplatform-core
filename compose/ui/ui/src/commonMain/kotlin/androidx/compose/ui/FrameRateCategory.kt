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

package androidx.compose.ui

import kotlin.jvm.JvmInline

/**
 * A type-safe representation of a frame rate category for a display or application.
 *
 * Using this class avoids primitive obsession by providing a clear and constrained set of possible
 * frame rate preferences instead of relying on raw float values.
 * - Default: Default value.
 * - NoPreference: It will explicitly not influence the frame rate.
 * - Normal: Indicates a middle frame rate suitable for animations that do not require higher frame
 *   rates. This is normally 60 Hz or close to it.
 * - High: Indicates a frame rate suitable for animations that require a high frame rate.
 */
@JvmInline
value class FrameRateCategory private constructor(internal val value: Float) {
    companion object {
        val Default = FrameRateCategory(Float.NaN)
        val NoPreference = FrameRateCategory(-1f)
        val Normal = FrameRateCategory(-3f)
        val High = FrameRateCategory(-4f)
    }

    override fun toString(): String {
        val text =
            when (value) {
                -1f -> "NoPreference"
                -3f -> "Normal"
                -4f -> "High"
                else -> "Default"
            }
        return text
    }
}
