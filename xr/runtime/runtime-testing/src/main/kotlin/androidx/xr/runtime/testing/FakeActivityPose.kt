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

import androidx.annotation.RestrictTo
import androidx.xr.runtime.internal.ActivityPose
import androidx.xr.runtime.internal.HitTestResult
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import com.google.common.util.concurrent.Futures.immediateFailedFuture
import com.google.common.util.concurrent.ListenableFuture

// TODO: b/405218432 - Implement this correctly instead of stubbing it out.
/** Test-only implementation of [ActivityPose] */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public open class FakeActivityPose : ActivityPose {
    override val activitySpacePose: Pose = Pose()

    override val worldSpaceScale: Vector3 = Vector3()

    override val activitySpaceScale: Vector3 = Vector3()

    override fun transformPoseTo(pose: Pose, destination: ActivityPose): Pose = pose

    override fun hitTest(
        origin: Vector3,
        direction: Vector3,
        @ActivityPose.HitTestFilterValue hitTestFilter: Int,
    ): ListenableFuture<HitTestResult> = immediateFailedFuture(NotImplementedError())
}
