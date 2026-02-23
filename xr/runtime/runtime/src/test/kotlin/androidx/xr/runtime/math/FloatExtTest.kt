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

package androidx.xr.runtime.math

import com.google.common.truth.Truth.assertThat
import kotlin.math.PI
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FloatExtTest {
    @Test
    fun rsqrt_returnsReciprocalSqrt() {
        assertThat(rsqrt(0.02f)).isWithin(1.0e-5f).of(7.0710683f)
        assertThat(rsqrt(1f)).isWithin(1.0e-5f).of(1f)
        assertThat(rsqrt(2f)).isWithin(1.0e-5f).of(0.70710677f)
        assertThat(rsqrt(3f)).isWithin(1.0e-5f).of(0.57735026f)
    }

    @Test
    fun clamp_returnsValueBetweenMinAndMax() {
        assertThat(clamp(0f, 1f, 2f)).isEqualTo(1f)
        assertThat(clamp(3f, 1f, 2f)).isEqualTo(2f)
        assertThat(clamp(2f, 1f, 3f)).isEqualTo(2f)
    }

    @Test
    fun lerpFloat_returnsInterpolatedValueBetweenStartAndEnd() {
        assertThat(lerp(1f, 2f, 0f)).isEqualTo(1f)
        assertThat(lerp(1f, 2f, 0.5f)).isEqualTo(1.5f)
        assertThat(lerp(1f, 2f, 1f)).isEqualTo(2f)
    }

    @Test
    fun toDegrees_returnsDegreesFromRadians() {
        assertThat(toDegrees(PI.toFloat() / 1f)).isEqualTo(180f)
        assertThat(toDegrees(PI.toFloat() / 2f)).isEqualTo(90f)
        assertThat(toDegrees(PI.toFloat() / 3f)).isEqualTo(60f)
        assertThat(toDegrees(PI.toFloat() / 4f)).isEqualTo(45f)
        assertThat(toDegrees(PI.toFloat() / 6f)).isEqualTo(30f)
    }

    @Test
    fun toRadians_returnsRadiansFromDegrees() {
        assertThat(toRadians(180f)).isEqualTo(PI.toFloat() / 1f)
        assertThat(toRadians(90f)).isEqualTo(PI.toFloat() / 2f)
        assertThat(toRadians(60f)).isEqualTo(PI.toFloat() / 3f)
        assertThat(toRadians(45f)).isEqualTo(PI.toFloat() / 4f)
        assertThat(toRadians(30f)).isEqualTo(PI.toFloat() / 6f)
    }
}
