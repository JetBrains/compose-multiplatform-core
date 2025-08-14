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

package androidx.xr.projected

import java.util.Objects

/**
 * Represents the audio configurations for a projected device.
 *
 * @property sourceType The audio source type. Values correspond to constants in
 *   [android.media.MediaRecorder.AudioSource].
 * @property sampleRatesHz The array of supported audio sample rates in Hz.
 * @property channelCounts The array of supported audio channel counts.
 */
public class ProjectedAudioConfig
internal constructor(
    public val sourceType: Int,
    public val sampleRatesHz: IntArray,
    public val channelCounts: IntArray,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProjectedAudioConfig

        if (sourceType != other.sourceType) return false
        if (!sampleRatesHz.contentEquals(other.sampleRatesHz)) return false
        if (!channelCounts.contentEquals(other.channelCounts)) return false

        return true
    }

    override fun hashCode(): Int =
        Objects.hash(sourceType.hashCode(), sampleRatesHz.hashCode(), channelCounts.hashCode())

    override fun toString(): String =
        "ProjectedAudioConfig(sourceType=$sourceType, sampleRatesHz=${sampleRatesHz.joinToString(", ")}, channelCounts=${channelCounts.joinToString(", ")})"
}
