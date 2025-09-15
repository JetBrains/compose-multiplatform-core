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
import androidx.xr.arcore.internal.Anchor
import androidx.xr.arcore.internal.AnchorInvalidUuidException
import androidx.xr.arcore.internal.DepthMap
import androidx.xr.arcore.internal.Earth
import androidx.xr.arcore.internal.Eye
import androidx.xr.arcore.internal.Face
import androidx.xr.arcore.internal.Hand
import androidx.xr.arcore.internal.HitResult
import androidx.xr.arcore.internal.PerceptionManager
import androidx.xr.arcore.internal.Trackable
import androidx.xr.runtime.VpsAvailabilityAvailable
import androidx.xr.runtime.VpsAvailabilityResult
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Ray
import androidx.xr.runtime.math.Vector3
import java.util.UUID

/**
 * Test-only implementation of [androidx.xr.arcore.internal.PerceptionManager] used to validate
 * state transitions.
 */
@SuppressWarnings("HiddenSuperclass")
public class FakePerceptionManager : PerceptionManager, AnchorHolder {

    /** List of anchors created by this [FakePerceptionManager]. */
    public val anchors: MutableList<Anchor> = mutableListOf<Anchor>()
    override val trackables: MutableList<Trackable> = mutableListOf<Trackable>()

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override val leftEye: Eye? = FakeRuntimeEye()

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override val rightEye: Eye? = FakeRuntimeEye()

    override val leftHand: Hand? = FakeRuntimeHand()
    override val rightHand: Hand? = FakeRuntimeHand()

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override val arDevice: FakeRuntimeArDevice = FakeRuntimeArDevice()

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override val leftRenderViewpoint: FakeRuntimeRenderViewpoint? =
        FakeRuntimeRenderViewpoint(Pose(Vector3(1f, 0f, 0f), Quaternion.Companion.Identity))

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override val rightRenderViewpoint: FakeRuntimeRenderViewpoint? =
        FakeRuntimeRenderViewpoint(Pose(Vector3(0f, 1f, 0f), Quaternion.Companion.Identity))

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override val monoRenderViewpoint: FakeRuntimeRenderViewpoint? =
        FakeRuntimeRenderViewpoint(Pose(Vector3(0f, 0f, 1f), Quaternion.Companion.Identity))

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override val userFace: Face? = FakeRuntimeFace()

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override val earth: Earth = FakeRuntimeEarth()

    override val leftDepthMap: DepthMap? = FakeRuntimeDepthMap()

    override val rightDepthMap: DepthMap? = FakeRuntimeDepthMap()

    override val monoDepthMap: DepthMap? = FakeRuntimeDepthMap()

    private val hitResults = mutableListOf<HitResult>()
    private val anchorUuids = mutableListOf<UUID>()

    /** Flag to represent available tracking state of the camera. */
    public var isTrackingAvailable: Boolean = true

    override fun createAnchor(pose: Pose): Anchor {
        // TODO: b/349862231 - Modify it once detach is implemented.
        val anchor = FakeRuntimeAnchor(pose, this, isTrackingAvailable)
        anchors.add(anchor)
        return anchor
    }

    override fun hitTest(ray: Ray): MutableList<HitResult> = hitResults

    override fun getPersistedAnchorUuids(): List<UUID> = anchorUuids

    override fun loadAnchor(uuid: UUID): Anchor {
        if (!anchorUuids.contains(uuid)) {
            throw AnchorInvalidUuidException()
        }
        return FakeRuntimeAnchor(Pose(), this)
    }

    override fun unpersistAnchor(uuid: UUID) {
        if (!anchorUuids.contains(uuid)) {
            throw AnchorInvalidUuidException()
        }
        anchorUuids.remove(uuid)
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    override fun onAnchorPersisted(anchor: Anchor) {
        anchorUuids.add(anchor.uuid!!)
    }

    override fun loadAnchorFromNativePointer(nativePointer: Long): Anchor {
        return FakeRuntimeAnchor(Pose(), this)
    }

    override fun detachAnchor(anchor: Anchor) {
        anchors.remove(anchor)
        anchor.uuid?.let { anchorUuids.remove(it) }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override suspend fun checkVpsAvailability(
        latitude: Double,
        longitude: Double,
    ): VpsAvailabilityResult {
        return VpsAvailabilityAvailable()
    }

    /**
     * Adds a [androidx.xr.arcore.internal.HitResult] to the list that is returned when calling
     * [hitTest] with any pose.
     */
    public fun addHitResult(hitResult: HitResult) {
        hitResults.add(hitResult)
    }

    /** Removes all [androidx.xr.arcore.internal.HitResult] instances passed to [addHitResult]. */
    public fun clearHitResults() {
        hitResults.clear()
    }

    /**
     * Adds a [androidx.xr.arcore.internal.Trackable] to the list that is returned when calling
     * [trackables].
     */
    public fun addTrackable(trackable: Trackable) {
        trackables.add(trackable)
    }

    /** Removes all [androidx.xr.arcore.internal.Trackable] instances passed to [addTrackable]. */
    public fun clearTrackables() {
        trackables.clear()
    }
}
