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

package androidx.xr.scenecore

import androidx.annotation.RestrictTo
import androidx.xr.runtime.HeadTrackingMode
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.internal.LifecycleManager

/**
 * The User object is used to retrieve information about the user. This includes the Head and The
 * CameraViews.
 *
 * @param lifecycleManager The [LifecycleManager] for the Session.
 * @param runtime The JxrPlatformAdapter for the Session.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class SpatialUser(
    private val lifecycleManager: LifecycleManager,
    private val runtime: JxrPlatformAdapter,
) {
    private var cachedLeftCamera: CameraView? = null
    private var cachedRightCamera: CameraView? = null
    private var cachedHead: Head? = null

    private var leftCamera: CameraView? = null
        get() {
            if (cachedLeftCamera == null) {
                cachedLeftCamera = CameraView.createLeft(runtime)
            }
            return cachedLeftCamera
        }

    private var rightCamera: CameraView? = null
        get() {
            if (cachedRightCamera == null) {
                cachedRightCamera = CameraView.createRight(runtime)
            }
            return cachedRightCamera
        }

    internal companion object {
        /** Factory function for creating [SpatialUser] instance. */
        internal fun create(
            lifecycleManager: LifecycleManager,
            runtime: JxrPlatformAdapter,
        ): SpatialUser {
            return SpatialUser(lifecycleManager, runtime)
        }
    }

    /**
     * Returns a Head for the SpatialUser or null if it is not yet available.
     *
     * @throws [IllegalStateException] if [session.config.headTracking] is set to
     *   [HeadTrackingMode.Disabled].
     */
    public var head: Head? = null
        get() {
            check(lifecycleManager.config.headTracking != HeadTrackingMode.Disabled) {
                "Config.HeadTrackingMode is set to Disabled."
            }
            if (cachedHead == null) {
                cachedHead = Head.create(runtime)
            }
            return cachedHead
        }

    /**
     * Returns a list of CameraViews that the user is using. The length of the list is dependent on
     * the device type. The list will be empty if the cameras are not yet available.
     *
     * @throws [IllegalStateException] if [session.config.headTracking] is set to
     *   [HeadTrackingMode.Disabled].
     */
    public fun getCameraViews(): List<CameraView> {
        check(lifecycleManager.config.headTracking != HeadTrackingMode.Disabled) {
            "Config.HeadTrackingMode is set to Disabled."
        }
        return listOfNotNull<CameraView>(leftCamera, rightCamera)
    }

    /**
     * Returns a CameraView for the specified CameraType or null if it is not available.
     *
     * @throws [IllegalStateException] if [session.config.headTracking] is set to
     *   [HeadTrackingMode.Disabled].
     */
    public fun getCameraView(cameraType: CameraView.CameraType): CameraView? {
        check(lifecycleManager.config.headTracking != HeadTrackingMode.Disabled) {
            "Config.HeadTrackingMode is set to Disabled."
        }
        if (cameraType == CameraView.CameraType.LEFT_EYE) {
            return leftCamera
        } else if (cameraType == CameraView.CameraType.RIGHT_EYE) {
            return rightCamera
        }
        return null
    }
}
