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

package androidx.xr.compose.subspace.layout

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.compose.unit.IntVolumeSize
import androidx.xr.runtime.math.Vector3
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [SpatialAlignment]. */
@RunWith(AndroidJUnit4::class)
class SpatialAlignmentTest {
    @Test
    fun spatialBiasAlignment_horizontalOffset() {
        assertThat(SpatialAlignment.CenterLeft.horizontalOffset(width = 100, space = 200))
            .isEqualTo(-50)
        assertThat(SpatialAlignment.Center.horizontalOffset(width = 100, space = 200)).isEqualTo(0)
        assertThat(SpatialAlignment.CenterRight.horizontalOffset(width = 100, space = 200))
            .isEqualTo(50)

        assertThat(SpatialAlignment.Left.offset(width = 100, space = 200)).isEqualTo(-50)
        assertThat(SpatialAlignment.CenterHorizontally.offset(width = 100, space = 200))
            .isEqualTo(0)
        assertThat(SpatialAlignment.Right.offset(width = 100, space = 200)).isEqualTo(50)
    }

    @Test
    fun spatialBiasAlignment_verticalOffset() {
        assertThat(SpatialAlignment.BottomCenter.verticalOffset(height = 100, space = 200))
            .isEqualTo(-50)
        assertThat(SpatialAlignment.Center.verticalOffset(height = 100, space = 200)).isEqualTo(0)
        assertThat(SpatialAlignment.TopCenter.verticalOffset(height = 100, space = 200))
            .isEqualTo(50)

        assertThat(SpatialAlignment.Bottom.offset(height = 100, space = 200)).isEqualTo(-50)
        assertThat(SpatialAlignment.CenterVertically.offset(height = 100, space = 200)).isEqualTo(0)
        assertThat(SpatialAlignment.Top.offset(height = 100, space = 200)).isEqualTo(50)
    }

    @Test
    fun spatialBiasAlignment_depthOffset() {
        assertThat(SpatialBiasAlignment(0f, 0f, -1f).depthOffset(depth = 100, space = 200))
            .isEqualTo(-50)
        assertThat(SpatialBiasAlignment(0f, 0f, 0f).depthOffset(depth = 100, space = 200))
            .isEqualTo(0)
        assertThat(SpatialBiasAlignment(0f, 0f, 1f).depthOffset(depth = 100, space = 200))
            .isEqualTo(50)

        assertThat(SpatialAlignment.Back.offset(depth = 100, space = 200)).isEqualTo(-50)
        assertThat(SpatialAlignment.CenterDepthwise.offset(depth = 100, space = 200)).isEqualTo(0)
        assertThat(SpatialAlignment.Front.offset(depth = 100, space = 200)).isEqualTo(50)
    }

    @Test
    fun spatialBiasAlignment_position() {
        val size = IntVolumeSize(100, 100, 100)
        val space = IntVolumeSize(200, 200, 200)
        assertThat(SpatialBiasAlignment(-1f, -1f, -1f).position(size, space))
            .isEqualTo(Vector3(-50f, -50f, -50f))
        assertThat(SpatialBiasAlignment(0f, 0f, 0f).position(size, space)).isEqualTo(Vector3.Zero)
        assertThat(SpatialBiasAlignment(1f, 1f, 1f).position(size, space))
            .isEqualTo(Vector3(50f, 50f, 50f))
    }
}
