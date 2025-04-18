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

import androidx.annotation.RestrictTo

/**
 * Defines a configuration state of all available features to be set at runtime.
 *
 * An instance of this class should be passed to [Session.configure()] to modify the current
 * configuration.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class Config(
    public val planeTracking: PlaneTrackingMode = PlaneTrackingMode.Disabled,
    public val handTracking: HandTrackingMode = HandTrackingMode.Disabled,
    public val depthEstimation: DepthEstimationMode = DepthEstimationMode.Disabled,
    public val anchorPersistence: AnchorPersistenceMode = AnchorPersistenceMode.Disabled,
    public val headTracking: HeadTrackingMode = HeadTrackingMode.Disabled,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Config) return false

        if (planeTracking != other.planeTracking) return false
        if (handTracking != other.handTracking) return false
        if (depthEstimation != other.depthEstimation) return false
        if (anchorPersistence != other.anchorPersistence) return false
        if (headTracking != other.headTracking) return false

        return true
    }

    override fun hashCode(): Int {
        var result = planeTracking.hashCode()
        result = 31 * result + handTracking.hashCode()
        result = 31 * result + depthEstimation.hashCode()
        result = 31 * result + anchorPersistence.hashCode()
        result = 31 * result + headTracking.hashCode()
        return result
    }

    @JvmOverloads
    public fun copy(
        planeTracking: PlaneTrackingMode = this.planeTracking,
        handTracking: HandTrackingMode = this.handTracking,
        depthEstimation: DepthEstimationMode = this.depthEstimation,
        anchorPersistence: AnchorPersistenceMode = this.anchorPersistence,
        headTracking: HeadTrackingMode = this.headTracking,
    ): Config {
        return Config(
            planeTracking = planeTracking,
            handTracking = handTracking,
            depthEstimation = depthEstimation,
            anchorPersistence = anchorPersistence,
            headTracking = headTracking,
        )
    }

    /**
     * Feature that allows tracking of and provides information about scene planes.
     *
     * Setting this feature to [PlaneTrackingMode.Enabled] requires that the
     * `SCENE_UNDERSTANDING_COARSE` Android permission is granted.
     */
    public class PlaneTrackingMode private constructor(public val mode: Int) {
        public companion object {
            /** Planes will not be tracked. */
            @JvmField public val Disabled: PlaneTrackingMode = PlaneTrackingMode(0)
            /**
             * Horizontal and vertical planes will be tracked. Note that setting this mode will
             * consume additional runtime resources.
             */
            @JvmField public val HorizontalAndVertical: PlaneTrackingMode = PlaneTrackingMode(1)
        }
    }

    /**
     * Feature that allows tracking of the user's hands and hand joints.
     *
     * Setting this feature to [HandTrackingMode.Enabled] requires that the `HAND_TRACKING` Android
     * permission is granted by the calling application.
     */
    public class HandTrackingMode private constructor(public val mode: Int) {
        public companion object {
            /** Hands will not be tracked. */
            @JvmField public val Disabled: HandTrackingMode = HandTrackingMode(0)
            /**
             * Hands will be tracked. Note that setting this mode will consume additional runtime
             * resources.
             */
            @JvmField public val Enabled: HandTrackingMode = HandTrackingMode(1)
        }
    }

    /**
     * Feature that allows more accurate information about scene depth and meshes.
     *
     * Setting this feature to [DepthEstimationMode.Enabled] requires that the
     * `SCENE_UNDERSTANDING_FINE` Android permission is granted by the calling application.
     */
    public class DepthEstimationMode private constructor(public val mode: Int) {
        public companion object {
            /** No information about scene depth will be provided. */
            @JvmField public val Disabled: DepthEstimationMode = DepthEstimationMode(0)
            /**
             * Depth estimation will be enabled. Note that setting this mode will consume additional
             * runtime resources.
             */
            @JvmField public val Enabled: DepthEstimationMode = DepthEstimationMode(1)
        }
    }

    /**
     * Feature that allows [Anchor]'s to be peristed through sessions.
     *
     * This feature does not require any additional application permissions.
     */
    public class AnchorPersistenceMode private constructor(public val mode: Int) {
        public companion object {
            /** Anchors cannot be persisted. */
            @JvmField public val Disabled: AnchorPersistenceMode = AnchorPersistenceMode(0)
            /** Anchors may be persisted. */
            @JvmField public val Enabled: AnchorPersistenceMode = AnchorPersistenceMode(1)
        }
    }

    /**
     * Feature that allows tracking of the user's head pose.
     *
     * Setting this feature to [HeadTracking.Enabled] requires that the `HEAD_TRACKING` Android
     * permission is granted by the calling application.
     */
    public class HeadTrackingMode private constructor(public val mode: Int) {
        public companion object {
            /** Head pose will not be tracked. */
            @JvmField public val Disabled: HeadTrackingMode = HeadTrackingMode(0)
            /** Head pose will be tracked. */
            @JvmField public val Enabled: HeadTrackingMode = HeadTrackingMode(1)
        }
    }
}
