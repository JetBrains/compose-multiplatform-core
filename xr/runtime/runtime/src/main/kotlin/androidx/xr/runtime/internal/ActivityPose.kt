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

package androidx.xr.runtime.internal

import androidx.annotation.IntDef
import androidx.annotation.RestrictTo
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import com.google.common.util.concurrent.ListenableFuture

/** Interface for an XR Runtime ActivityPose. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface ActivityPose {
    /** Returns the pose for this entity, relative to the activity space root. */
    public val activitySpacePose: Pose

    // TODO: b/364303733 - Consider deprecating this method.
    /**
     * Returns the scale of this ActivityPose. For base ActivityPoses, the scale is (1,1,1). For
     * entities this returns the accumulated scale. This value includes the parent's scale, and is
     * similar to a ActivitySpace scale.
     *
     * @return Total [Vector3] scale applied to self and children.
     */
    public val worldSpaceScale: Vector3

    /**
     * Returns the scale of this WorldPose relative to the activity space. This returns the
     * accumulated scale which includes the parent's scale, but does not include the scale of the
     * activity space itself.
     *
     * @return Total [Vector3] scale applied to self and children relative to the activity space.
     */
    public val activitySpaceScale: Vector3

    /**
     * Returns a pose relative to this entity transformed into a pose relative to the destination.
     *
     * @param pose A pose in this entity's local coordinate space.
     * @param destination The entity which the returned pose will be relative to.
     * @return The pose relative to the destination entity.
     */
    public fun transformPoseTo(pose: Pose, destination: ActivityPose): Pose

    @IntDef(
        HitTestRange.HIT_TEST_RANGE_ALL_SCENES,
        HitTestRange.HIT_TEST_RANGE_OTHER_SCENES,
        HitTestRange.HIT_TEST_RANGE_SELF_SCENES,
    )
    @Retention(AnnotationRetention.SOURCE)
    @Suppress("PublicTypedef")
    public annotation class HitTestRangeValue

    /** Specifies Range of entities to hit test with ActivityPose.hitTest */
    public object HitTestRange {
        /**
         * Register hit tests for all Scenes. If the app does not have the
         * android.permission.ACCESS_OVERLAY_SPACE perminssion a hit test will only register hit
         * tests for your own scene.
         */
        public const val HIT_TEST_RANGE_ALL_SCENES: Int = 0
        /**
         * Register hit tests only for other scenes. If the app does not have the
         * android.permission.ACCESS_OVERLAY_SPACE perminssion, a hit test will throw an
         * IllegalStateException.
         */
        public const val HIT_TEST_RANGE_OTHER_SCENES: Int = 1
        /** Register hit tests for the scene which this Activity pose belongs to. */
        public const val HIT_TEST_RANGE_SELF_SCENES: Int = 2
    }

    /**
     * Creates a hit test at the from the specified origin in the specified direction into the
     * scene.
     *
     * @param origin The translation of the origin of the hit test relative to this ActivityPose.
     * @param direction The direction for the hit test ray from the ActivityPose.
     * @param hitTestRange The scenes that will be in range for the hit test.
     * @return a {@code ListenableFuture<HitResult>}. The HitResult describes if it hit something
     *   and where relative to this [ActivityPose]. Listeners will be called on the main thread if
     *   Runnable::run is supplied.
     */
    public fun hitTest(
        origin: Vector3,
        direction: Vector3,
        @HitTestRangeValue hitTestRange: Int,
    ): ListenableFuture<HitTestResult>
}
