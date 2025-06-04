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

package androidx.xr.scenecore

import androidx.xr.runtime.math.Pose

/**
 * The SpatialModeChangeListener is used to handle scenegraph updates when the spatial mode for the
 * scene changes.
 */
public fun interface SpatialModeChangeListener {
    /**
     * Called when the activity encounters spatial mode change.
     *
     * @param recommendedPose the recommended pose for the center of attention entity. The pose is
     *   relative to [ActivitySpace] origin, not relative to the center of attention's parent.
     * @param recommendedScale the recommended scale for the center of attention entity. The scale
     *   is the accumulated scale for this entity i.e. accumulated scale in [ActivitySpace], not
     *   scale relative to the center of attention's parent.
     */
    public fun onSpatialModeChanged(recommendedPose: Pose, recommendedScale: Float)
}
