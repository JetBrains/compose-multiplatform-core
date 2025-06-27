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

package androidx.xr.compose.spatial

import androidx.annotation.RestrictTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Represents the resting elevation level for spatial elements.
 *
 * Elevation levels range from `Level0` (no elevation) to `Level5` (highest recommended elevation).
 *
 * NOTE: Level0 is not visually distinguishable from base-level content but is present to support
 * smooth transitioning between elevation levels.
 *
 * @property level The elevation level in Dp.
 */
@JvmInline
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public value class SpatialElevationLevel(public val level: Dp) {
    public companion object {
        internal val ActivityDefault = SpatialElevationLevel(0.dp)
        public val Level0: SpatialElevationLevel = SpatialElevationLevel(0.1.dp)
        public val Level1: SpatialElevationLevel = SpatialElevationLevel(16.dp)
        public val Level2: SpatialElevationLevel = SpatialElevationLevel(24.dp)
        public val Level3: SpatialElevationLevel = SpatialElevationLevel(32.dp)
        public val Level4: SpatialElevationLevel = SpatialElevationLevel(40.dp)
        public val Level5: SpatialElevationLevel = SpatialElevationLevel(56.dp)
        internal val DialogDefault = SpatialElevationLevel(56.dp)
    }
}
