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

package androidx.xr.arcore.testing

import androidx.annotation.RestrictTo
import androidx.xr.arcore.internal.Face as RuntimeFace
import androidx.xr.runtime.TrackingState

/**
 * Fake implementation of [androidx.xr.arcore.internal.Face] for testing purposes.
 *
 * @property trackingState The current tracking state of the face.
 * @property isValid Indicates whether the face is valid.
 * @property blendShapeValues The array of blend shape values.
 * @property confidenceValues The array of confidence values.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class FakeRuntimeFace(
    override var trackingState: TrackingState = TrackingState.PAUSED,
    override var isValid: Boolean = true,
    override var blendShapeValues: FloatArray = FloatArray(0),
    override var confidenceValues: FloatArray = FloatArray(0),
) : RuntimeFace {}
