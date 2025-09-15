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

package androidx.xr.arcore.internal

import androidx.annotation.RestrictTo
import androidx.xr.arcore.EyeState
import androidx.xr.runtime.math.Pose

/** Describes a user's eye information with coarse and fine precision. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface Eye {
    /** The state of the eye with coarse accuracy */
    public val coarseState: EyeState?

    /** The eye's pose with coarse accuracy */
    public val coarsePose: Pose?

    /** The state of the eye with fine accuracy */
    public val fineState: EyeState?

    /** the eye's pose with fine accuracy */
    public val finePose: Pose?
}
