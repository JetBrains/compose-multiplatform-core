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

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpatialShapeTest {
    @Test
    fun roundedCorner_zero() {
        assertThat(
                SpatialRoundedCornerShape(CornerSize(0))
                    .computeCornerRadius(maxWidth = 100f, maxHeight = 100f, density = Density(1.0f))
            )
            .isEqualTo(0f)
    }

    @Test
    fun roundedCorner_dp() {
        assertThat(
                SpatialRoundedCornerShape(CornerSize(25.dp))
                    .computeCornerRadius(maxWidth = 100f, maxHeight = 200f, density = Density(1.0f))
            )
            .isEqualTo(25f)
    }

    @Test
    fun roundedCorner_increasedDensity() {
        // Returns a larger radius scaled with Density.
        assertThat(
                SpatialRoundedCornerShape(CornerSize(25.dp))
                    .computeCornerRadius(maxWidth = 100f, maxHeight = 200f, density = Density(2.0f))
            )
            .isEqualTo(50f)
    }

    @Test
    fun roundedCorner_extraDp() {
        // Corner radius is capped at 50% of the smallest side (60). Since the set corner size of
        // 150
        // is above this, corner radius becomes equal to the cap.
        assertThat(
                SpatialRoundedCornerShape(CornerSize(150.dp))
                    .computeCornerRadius(maxWidth = 120f, maxHeight = 200f, density = Density(1.0f))
            )
            .isEqualTo(60f)
    }

    @Test
    fun roundedCorner_percent() {
        // Takes 25% of the smallest side.
        assertThat(
                SpatialRoundedCornerShape(CornerSize(25))
                    .computeCornerRadius(maxWidth = 100f, maxHeight = 200f, density = Density(1.0f))
            )
            .isEqualTo(25f)
    }

    @Test
    fun roundedCorner_extraPercent() {
        // Anything above 50% has no effect, so this computes 50% of the smallest side.
        assertThat(
                SpatialRoundedCornerShape(CornerSize(75))
                    .computeCornerRadius(maxWidth = 100f, maxHeight = 200f, density = Density(1.0f))
            )
            .isEqualTo(50f)
    }
}
