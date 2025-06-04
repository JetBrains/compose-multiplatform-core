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

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.xr.runtime.internal.ActivityPose.HitTestFilter as RtHitTestFilter
import androidx.xr.runtime.internal.AnchorEntity as RtAnchorEntity
import androidx.xr.runtime.internal.AnchorPlacement as RtAnchorPlacement
import androidx.xr.runtime.internal.Dimensions as RtDimensions
import androidx.xr.runtime.internal.HitTestResult as RtHitTestResult
import androidx.xr.runtime.internal.HitTestResult.HitTestSurfaceType as RtHitTestSurfaceType
import androidx.xr.runtime.internal.InputEvent as RtInputEvent
import androidx.xr.runtime.internal.InputEvent.Companion.HitInfo as RtHitInfo
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.internal.MoveEvent as RtMoveEvent
import androidx.xr.runtime.internal.PerceivedResolutionResult as RtPerceivedResolutionResult
import androidx.xr.runtime.internal.PixelDimensions as RtPixelDimensions
import androidx.xr.runtime.internal.PlaneSemantic as RtPlaneSemantic
import androidx.xr.runtime.internal.PlaneType as RtPlaneType
import androidx.xr.runtime.internal.ResizeEvent as RtResizeEvent
import androidx.xr.runtime.internal.Space as RtSpace
import androidx.xr.runtime.internal.SpatialCapabilities as RtSpatialCapabilities
import androidx.xr.runtime.internal.SpatialPointerIcon as RtSpatialPointerIcon
import androidx.xr.runtime.internal.SpatialPointerIconType as RtSpatialPointerIconType
import androidx.xr.runtime.internal.SpatialVisibility as RtSpatialVisibility
import androidx.xr.runtime.internal.TextureSampler as RtTextureSampler
import androidx.xr.runtime.math.FloatSize3d
import androidx.xr.runtime.math.IntSize2d
import androidx.xr.runtime.math.Ray
import androidx.xr.scenecore.HitTestResult.SurfaceType
import androidx.xr.scenecore.InputEvent.HitInfo
import androidx.xr.scenecore.ScenePose.HitTestFilter
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job

internal class HandlerExecutor(val handler: Handler) : Executor {
    override fun execute(command: Runnable) {
        handler.post(command)
    }

    companion object {
        val mainThreadExecutor: Executor = HandlerExecutor(Handler(Looper.getMainLooper()))
    }
}

/** Extension function that converts a [androidx.xr.runtime.math.FloatSize3d] to [RtDimensions]. */
internal fun FloatSize3d.toRtDimensions(): RtDimensions {
    return RtDimensions(width, height, depth)
}

/** Extension function that converts a [RtDimensions] to [FloatSize3d]. */
internal fun RtDimensions.toFloatSize3d(): FloatSize3d {
    return FloatSize3d(width, height, depth)
}

/**
 * Extension function that converts a [androidx.xr.runtime.math.IntSize2d] to [RtPixelDimensions].
 */
internal fun IntSize2d.toRtPixelDimensions(): RtPixelDimensions {
    return RtPixelDimensions(width, height)
}

/** Extension function that converts a [RtPixelDimensions] to [IntSize2d]. */
internal fun RtPixelDimensions.toIntSize2d(): IntSize2d {
    return IntSize2d(width, height)
}

/** Extension function that converts [Int] to [JxrPlatformAdapter.PlaneType]. */
internal fun Int.toRtPlaneType(): RtPlaneType {
    return when (this) {
        PlaneOrientation.HORIZONTAL -> RtPlaneType.HORIZONTAL
        PlaneOrientation.VERTICAL -> RtPlaneType.VERTICAL
        PlaneOrientation.ANY -> RtPlaneType.ANY
        else -> error("Unknown Plane Type: $PlaneOrientation")
    }
}

/** Extension function that converts [Int] to [JxrPlatformAdapter.PlaneSemantic]. */
internal fun Int.toRtPlaneSemantic(): RtPlaneSemantic {
    return when (this) {
        PlaneSemanticType.WALL -> RtPlaneSemantic.WALL
        PlaneSemanticType.FLOOR -> RtPlaneSemantic.FLOOR
        PlaneSemanticType.CEILING -> RtPlaneSemantic.CEILING
        PlaneSemanticType.TABLE -> RtPlaneSemantic.TABLE
        PlaneSemanticType.ANY -> RtPlaneSemantic.ANY
        else -> error("Unknown Plane Semantic: $PlaneSemanticType")
    }
}

/** Extension function that converts [Space] value to [JxrPlatformAdapter.Space] value. */
internal fun Int.toRtSpace(): Int {
    return when (this) {
        Space.PARENT -> RtSpace.PARENT
        Space.ACTIVITY -> RtSpace.ACTIVITY
        Space.REAL_WORLD -> RtSpace.REAL_WORLD
        else -> error("Unknown Space Value: $this")
    }
}

/** Extension function that converts a [RtMoveEvent] to a [MoveEvent]. */
internal fun RtMoveEvent.toMoveEvent(entityManager: EntityManager): MoveEvent {

    disposedEntity?.let { entityManager.removeEntity(it) }
    return MoveEvent(
        moveState.toMoveState(),
        Ray(initialInputRay.origin, initialInputRay.direction),
        Ray(currentInputRay.origin, currentInputRay.direction),
        previousPose,
        currentPose,
        previousScale.x,
        currentScale.x,
        entityManager.getEntityForRtEntity(initialParent)!!,
        updatedParent?.let {
            entityManager.getEntityForRtEntity(it)
                ?: AnchorEntity.create(it as RtAnchorEntity, entityManager)
        },
    )
}

/** Extension function that converts a [RtHitInfo] to a [HitInfo]. */
internal fun RtHitInfo.toHitInfo(entityManager: EntityManager): HitInfo? {
    // TODO: b/377541143 - Replace instance equality check in EntityManager.
    val hitEntity = entityManager.getEntityForRtEntity(inputEntity)
    return if (hitEntity == null) {
        null
    } else {
        HitInfo(inputEntity = hitEntity, hitPosition = hitPosition, transform = transform)
    }
}

/** Extension function that converts a [RtInputEvent] to a [InputEvent]. */
internal fun RtInputEvent.toInputEvent(entityManager: EntityManager): InputEvent {
    return InputEvent(
        source.toInputEventSource(),
        pointerType.toInputEventPointerType(),
        timestamp,
        origin,
        direction,
        action.toInputEventAction(),
        hitInfo?.toHitInfo(entityManager),
        secondaryHitInfo?.toHitInfo(entityManager),
    )
}

/** Extension function that converts a [RtSpatialCapabilities] to a [SpatialCapabilities]. */
internal fun RtSpatialCapabilities.toSpatialCapabilities(): SpatialCapabilities {
    return SpatialCapabilities(capabilities.toSpatialCapability())
}

/** Extension function that converts a [RtSpatialVisibility] to a [SpatialVisibility]. */
internal fun RtSpatialVisibility.toSpatialVisibility(): SpatialVisibility {
    return SpatialVisibility(visibility.toSpatialVisibilityValue())
}

/** Extension function that converts a [RtResizeEvent] to a [ResizeEvent]. */
internal fun RtResizeEvent.toResizeEvent(): ResizeEvent {
    return ResizeEvent(resizeState.toResizeState(), newSize.toFloatSize3d())
}

/**
 * Extension function that converts a [Set] of [AnchorPlacement] to a [Set] of
 * [JxrPlatformAdapter.AnchorPlacement].
 */
internal fun Set<AnchorPlacement>.toRtAnchorPlacement(
    runtime: JxrPlatformAdapter
): Set<RtAnchorPlacement> {
    val rtAnchorPlacementSet = HashSet<RtAnchorPlacement>()
    for (placement in this) {
        val planeTypeFilter = placement.planeTypeFilter.map { it.toRtPlaneType() }.toMutableSet()
        val planeSemanticFilter =
            placement.planeSemanticFilter.map { it.toRtPlaneSemantic() }.toMutableSet()

        val rtAnchorPlacement =
            runtime.createAnchorPlacementForPlanes(planeTypeFilter, planeSemanticFilter)
        rtAnchorPlacementSet.add(rtAnchorPlacement)
    }
    return rtAnchorPlacementSet
}

/** Extension function that converts a [Int] to [MoveEvent.MoveState]. */
@MoveEvent.MoveState
internal fun Int.toMoveState(): Int {
    return when (this) {
        RtMoveEvent.MOVE_STATE_START -> MoveEvent.MOVE_STATE_START
        RtMoveEvent.MOVE_STATE_ONGOING -> MoveEvent.MOVE_STATE_ONGOING
        RtMoveEvent.MOVE_STATE_END -> MoveEvent.MOVE_STATE_END
        else -> error("Unknown Move State: $this")
    }
}

/** Extension function that converts a [Int] to [ResizeEvent.ResizeState]. */
@ResizeEvent.ResizeState
internal fun Int.toResizeState(): Int {
    return when (this) {
        RtResizeEvent.RESIZE_STATE_UNKNOWN -> ResizeEvent.RESIZE_STATE_UNKNOWN
        RtResizeEvent.RESIZE_STATE_START -> ResizeEvent.RESIZE_STATE_START
        RtResizeEvent.RESIZE_STATE_ONGOING -> ResizeEvent.RESIZE_STATE_ONGOING
        RtResizeEvent.RESIZE_STATE_END -> ResizeEvent.RESIZE_STATE_END
        else -> error("Unknown Resize State: $this")
    }
}

/** Extension function that converts a [Int] to [InputEvent.Source]. */
@InputEvent.Source
internal fun Int.toInputEventSource(): Int {
    return when (this) {
        RtInputEvent.SOURCE_UNKNOWN -> InputEvent.SOURCE_UNKNOWN
        RtInputEvent.SOURCE_HEAD -> InputEvent.SOURCE_HEAD
        RtInputEvent.SOURCE_CONTROLLER -> InputEvent.SOURCE_CONTROLLER
        RtInputEvent.SOURCE_HANDS -> InputEvent.SOURCE_HANDS
        RtInputEvent.SOURCE_MOUSE -> InputEvent.SOURCE_MOUSE
        RtInputEvent.SOURCE_GAZE_AND_GESTURE -> InputEvent.SOURCE_GAZE_AND_GESTURE
        else -> error("Unknown Input Event Source: $this")
    }
}

/** Extension function that converts a [Int] to [InputEvent.PointerType]. */
@InputEvent.PointerType
internal fun Int.toInputEventPointerType(): Int {
    return when (this) {
        RtInputEvent.POINTER_TYPE_DEFAULT -> InputEvent.POINTER_TYPE_DEFAULT
        RtInputEvent.POINTER_TYPE_LEFT -> InputEvent.POINTER_TYPE_LEFT
        RtInputEvent.POINTER_TYPE_RIGHT -> InputEvent.POINTER_TYPE_RIGHT
        else -> error("Unknown Input Event Pointer Type: $this")
    }
}

/** Extension function that converts a [Int] to [SpatialCapabilities.SpatialCapability]. */
@SpatialCapabilities.SpatialCapability
internal fun Int.toSpatialCapability(): Int {
    return this
}

/** Extension function that converts a [Int] to [SpatialVisibilityValue]. */
@SpatialVisibility.SpatialVisibilityValue
internal fun Int.toSpatialVisibilityValue(): Int {
    return when (this) {
        RtSpatialVisibility.UNKNOWN -> SpatialVisibility.UNKNOWN
        RtSpatialVisibility.OUTSIDE_FOV -> SpatialVisibility.OUTSIDE_FOV
        RtSpatialVisibility.PARTIALLY_WITHIN_FOV -> SpatialVisibility.PARTIALLY_WITHIN_FOV
        RtSpatialVisibility.WITHIN_FOV -> SpatialVisibility.WITHIN_FOV
        else -> error("Unknown Spatial Visibility Value: $this")
    }
}

/** Extension function that converts a [Int] to [InputEvent.Action]. */
@InputEvent.Action
internal fun Int.toInputEventAction(): Int {
    return when (this) {
        RtInputEvent.ACTION_DOWN -> InputEvent.ACTION_DOWN
        RtInputEvent.ACTION_UP -> InputEvent.ACTION_UP
        RtInputEvent.ACTION_MOVE -> InputEvent.ACTION_MOVE
        RtInputEvent.ACTION_CANCEL -> InputEvent.ACTION_CANCEL
        RtInputEvent.ACTION_HOVER_MOVE -> InputEvent.ACTION_HOVER_MOVE
        RtInputEvent.ACTION_HOVER_ENTER -> InputEvent.ACTION_HOVER_ENTER
        RtInputEvent.ACTION_HOVER_EXIT -> InputEvent.ACTION_HOVER_EXIT
        else -> error("Unknown Input Event Action: $this")
    }
}

/** Extension function that converts a [TextureSampler] to [RtTextureSampler]. */
internal fun TextureSampler.toRtTextureSampler(): RtTextureSampler {
    return RtTextureSampler(
        wrapModeS,
        wrapModeT,
        wrapModeR,
        minFilter,
        magFilter,
        compareMode,
        compareFunc,
        anisotropyLog2,
    )
}

/** Extension function that converts a [HitTestFilter] to a [RtHitTestFilter]. */
internal fun Int.toRtHitTestFilter(): Int {
    var filters: Int = 0
    if (this and HitTestFilter.OTHER_SCENES != 0) {
        filters = filters or RtHitTestFilter.OTHER_SCENES
    }
    if (this and HitTestFilter.SELF_SCENE != 0) {
        filters = filters or RtHitTestFilter.SELF_SCENE
    }
    return filters
}

/** Extension function that converts a [RtHitTestSurfaceType] to a [HitTestResult.SurfaceType]. */
internal fun Int.toHitTestSurfaceType(): Int {
    return when (this) {
        RtHitTestSurfaceType.HIT_TEST_RESULT_SURFACE_TYPE_UNKNOWN -> SurfaceType.UNKNOWN
        RtHitTestSurfaceType.HIT_TEST_RESULT_SURFACE_TYPE_PLANE -> SurfaceType.PLANE
        RtHitTestSurfaceType.HIT_TEST_RESULT_SURFACE_TYPE_OBJECT -> SurfaceType.OBJECT
        else -> SurfaceType.UNKNOWN
    }
}

/** Extension function that converts a [RtHitTestResult] to a [HitTestResult]. */
internal fun RtHitTestResult.toHitTestResult(): HitTestResult {
    return HitTestResult(hitPosition, surfaceNormal, surfaceType.toHitTestSurfaceType(), distance)
}

@RtSpatialPointerIconType
internal fun SpatialPointerIcon.toRtSpatialPointerIcon(): Int {
    return when (this) {
        SpatialPointerIconNone -> RtSpatialPointerIcon.TYPE_NONE
        SpatialPointerIconCircle -> RtSpatialPointerIcon.TYPE_CIRCLE
    }
}

internal fun Int.toSpatialPointerIcon(): SpatialPointerIcon? {
    return when (this) {
        RtSpatialPointerIcon.TYPE_NONE -> SpatialPointerIconNone
        RtSpatialPointerIcon.TYPE_CIRCLE -> SpatialPointerIconCircle
        RtSpatialPointerIcon.TYPE_DEFAULT -> null
        else -> error("Unknown spatial pointer icon type: $this")
    }
}

/**
 * Extension function that converts a [RtPerceivedResolutionResult] to [PerceivedResolutionResult].
 */
internal fun RtPerceivedResolutionResult.toPerceivedResolutionResult(): PerceivedResolutionResult {
    return when (this) {
        is RtPerceivedResolutionResult.Success ->
            PerceivedResolutionResult.Success(this.perceivedResolution.toIntSize2d())
        is RtPerceivedResolutionResult.EntityTooClose -> PerceivedResolutionResult.EntityTooClose()
        is RtPerceivedResolutionResult.InvalidCameraView ->
            PerceivedResolutionResult.InvalidCameraView()
    }
}

internal suspend fun <T> ListenableFuture<T>.awaitSuspending(): T {
    val deferred = CompletableDeferred<T>(coroutineContext[Job])
    val futureBeingAwaited = this

    this.addListener(
        Runnable {
            try {
                deferred.complete(this.get())
            } catch (e: Throwable) {
                Log.e("AwaitSuspending", "ListenableFuture failed: $futureBeingAwaited", e)
                deferred.completeExceptionally(e)
            }
        },
        DirectExecutor,
    )

    return deferred.await()
}

private object DirectExecutor : Executor {
    override fun execute(command: Runnable) {
        command.run()
    }
}
