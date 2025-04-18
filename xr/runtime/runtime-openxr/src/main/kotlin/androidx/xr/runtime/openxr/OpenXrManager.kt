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

package androidx.xr.runtime.openxr

import android.app.Activity
import androidx.annotation.RestrictTo
import androidx.xr.runtime.internal.Config
import androidx.xr.runtime.internal.LifecycleManager
import androidx.xr.runtime.internal.PermissionNotGrantedException
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** Manages the lifecycle of an OpenXR session. */
@Suppress("NotCloseable")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class OpenXrManager
internal constructor(
    private val activity: Activity,
    private val perceptionManager: OpenXrPerceptionManager,
    internal val timeSource: OpenXrTimeSource,
) : LifecycleManager {

    /**
     * A pointer to the native OpenXrManager. Only valid after [create] and before [stop] have been
     * called.
     */
    internal var nativePointer: Long = 0L
        private set

    override fun create() {
        nativePointer = nativeGetPointer()
    }

    /** The current state of the runtime configuration for the session. */
    // TODO(b/392660855): Disable all features by default once this API is fully implemented.
    override var config: Config =
        Config(
            Config.PlaneTrackingMode.Disabled,
            Config.HandTrackingMode.Disabled,
            Config.DepthEstimationMode.Disabled,
            Config.AnchorPersistenceMode.Enabled,
        )
        private set

    override fun configure(config: Config) {
        when (
            nativeConfigureSession(
                config.planeTracking.mode,
                config.handTracking.mode,
                config.depthEstimation.mode,
                config.anchorPersistence.mode,
                config.headTracking.mode,
            )
        ) {
            -2L ->
                throw RuntimeException(
                    "There was an unknown runtime error configuring the session."
                ) // XR_ERROR_RUNTIME_FAILURE
            -1000710000L ->
                throw PermissionNotGrantedException() // XR_ERROR_PERMISSION_INSUFFICIENT
        }
        this.config = config
    }

    override fun resume() {
        check(nativeInit(activity))
    }

    override suspend fun update(): ComparableTimeMark {
        // TODO: b/345314364 - Implement this method properly once the native manager supports it.
        // Currently the native manager handles this via an internal looping mechanism.
        val now = timeSource.markNow()
        perceptionManager.update(timeSource.getXrTime(now))
        // Block the call for a time that is appropriate for OpenXR devices.
        // TODO: b/359871229 - Implement dynamic delay. We start with a fixed 20ms delay as it is
        // a nice round number that produces a reasonable frame rate @50 Hz, but this value may need
        // to
        // be adjusted in the future.
        delay(20.milliseconds)
        return now
    }

    override fun pause() {
        check(nativePause())
    }

    override fun stop() {
        nativeDeInit()
        nativePointer = 0L
        perceptionManager.clear()
    }

    private external fun nativeGetPointer(): Long

    private external fun nativeInit(activity: Activity): Boolean

    private external fun nativeDeInit(): Boolean

    private external fun nativePause(): Boolean

    private external fun nativeConfigureSession(
        planeTracking: Int,
        handTracking: Int,
        depthEstimation: Int,
        anchorPersistence: Int,
        headTracking: Int,
    ): Long
}
