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

import android.media.MediaRecorder.AudioSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectedAudioConfigTest {

    @Test
    fun createInstance_fieldsAreInitializedCorrectly() {
        val config = ProjectedAudioConfig(SOURCE_TYPE, SAMPLE_RATES, CHANNEL_COUNTS)

        assertThat(config.sourceType).isEqualTo(SOURCE_TYPE)
        assertThat(config.sampleRatesHz).isEqualTo(SAMPLE_RATES)
        assertThat(config.channelCounts).isEqualTo(CHANNEL_COUNTS)
    }

    @Test
    fun equals_instancesWithSameValuesAreEqual() {
        val config1 = ProjectedAudioConfig(SOURCE_TYPE, SAMPLE_RATES, CHANNEL_COUNTS)

        val config2 =
            ProjectedAudioConfig(SOURCE_TYPE, SAMPLE_RATES.clone(), CHANNEL_COUNTS.clone())

        val configDifferentSource =
            ProjectedAudioConfig(AudioSource.CAMCORDER, SAMPLE_RATES, CHANNEL_COUNTS)

        val configDifferentRates =
            ProjectedAudioConfig(SOURCE_TYPE, intArrayOf(16000), CHANNEL_COUNTS)

        val configDifferentChannels = ProjectedAudioConfig(SOURCE_TYPE, SAMPLE_RATES, intArrayOf(1))

        // Reflexive
        assertThat(config1).isEqualTo(config1)
        // Symmetric and equal
        assertThat(config1).isEqualTo(config2)
        assertThat(config2).isEqualTo(config1)
        // Unequal
        assertThat(config1).isNotEqualTo(configDifferentSource)
        assertThat(config1).isNotEqualTo(configDifferentRates)
        assertThat(config1).isNotEqualTo(configDifferentChannels)
        // Different type and null
        assertThat(config1).isNotEqualTo(Any())
        assertThat(config1).isNotEqualTo(null)
    }

    @Test
    fun hashCode_isConsistent() {
        val config1 = ProjectedAudioConfig(SOURCE_TYPE, SAMPLE_RATES, CHANNEL_COUNTS)

        val config2 = ProjectedAudioConfig(SOURCE_TYPE, SAMPLE_RATES, CHANNEL_COUNTS)

        assertThat(config1.hashCode()).isEqualTo(config2.hashCode())
    }

    @Test
    fun toString_returnsExpectedString() {
        val config = ProjectedAudioConfig(SOURCE_TYPE, SAMPLE_RATES, CHANNEL_COUNTS)

        val expectedString =
            "ProjectedAudioConfig(sourceType=$SOURCE_TYPE, " +
                "sampleRatesHz=${SAMPLE_RATES.joinToString(", ")}, " +
                "channelCounts=${CHANNEL_COUNTS.joinToString(", ")})"

        assertThat(config.toString()).isEqualTo(expectedString)
    }

    companion object {
        private const val SOURCE_TYPE = AudioSource.MIC
        private val SAMPLE_RATES = intArrayOf(44100, 48000)
        private val CHANNEL_COUNTS = intArrayOf(1, 2)
    }
}
