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

package androidx.xr.runtime.testing

import androidx.xr.runtime.internal.SpatialPointerComponent
import androidx.xr.runtime.internal.SpatialPointerIcon
import androidx.xr.runtime.internal.SpatialPointerIconType

/** Test-only implementation of [SpatialPointerComponent] */
public class FakeSpatialPointerComponent : SpatialPointerComponent, FakeComponent() {

    @SpatialPointerIconType private var spatialPointerIcon: Int = SpatialPointerIcon.TYPE_DEFAULT

    override public fun setSpatialPointerIcon(@SpatialPointerIconType iconType: Int) {
        spatialPointerIcon = iconType
    }

    @SpatialPointerIconType override public fun getSpatialPointerIcon(): Int = spatialPointerIcon
}
