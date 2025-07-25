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

package androidx.xr.compose.spatial

import androidx.activity.ComponentActivity
import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposableOpenTarget
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalWithComputedDefaultOf
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.xr.compose.platform.LocalCoreEntity
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.platform.SpatialComposeScene
import androidx.xr.compose.platform.disposableValueOf
import androidx.xr.compose.platform.getActivity
import androidx.xr.compose.platform.getValue
import androidx.xr.compose.subspace.SpatialBox
import androidx.xr.compose.subspace.SpatialBoxScope
import androidx.xr.compose.subspace.SubspaceComposable
import androidx.xr.compose.subspace.layout.CoreContentlessEntity
import androidx.xr.compose.subspace.layout.SubspaceLayout
import androidx.xr.compose.subspace.node.SubspaceNodeApplier
import androidx.xr.compose.unit.IntVolumeSize
import androidx.xr.compose.unit.Meter
import androidx.xr.compose.unit.VolumeConstraints
import androidx.xr.runtime.HeadTrackingMode
import androidx.xr.runtime.math.Pose
import androidx.xr.scenecore.CameraView
import androidx.xr.scenecore.CameraView.CameraType
import androidx.xr.scenecore.ContentlessEntity
import androidx.xr.scenecore.Fov
import androidx.xr.scenecore.Head
import androidx.xr.scenecore.Space
import androidx.xr.scenecore.scene
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay

private val LocalIsInApplicationSubspace: ProvidableCompositionLocal<Boolean> =
    compositionLocalWithComputedDefaultOf {
        LocalCoreEntity.currentValue != null
    }

/** Defines default values used by the Subspace composables, primarily [ApplicationSubspace]. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public object SubspaceDefaults {
    /**
     * Default [VolumeConstraints] used as a fallback value.
     *
     * This value is primarily used as the default `constraints` parameter when
     * [ConstraintsBehavior.FieldOfView] is used.
     */
    public val fallbackFieldOfViewConstraints: VolumeConstraints =
        VolumeConstraints(minWidth = 0, maxWidth = 2775, minHeight = 0, maxHeight = 2576)
}

/**
 * Create a 3D area that the app can render spatial content into.
 *
 * If this is the topmost [Subspace] in the compose hierarchy then this will expand to fill all of
 * the available space bounded by the SpatialUser's field of view in width and height and will not
 * be bound by its containing window. In case the field of view width and height cannot be
 * determined, the default field of view width and height values will be used.
 *
 * If this is nested within another [Subspace] then it will lay out its content in the X and Y
 * directions according to the layout logic of its parent in 2D space. It will be constrained in the
 * Z direction according to the constraints imposed by its containing [Subspace].
 *
 * This is a no-op and does not render anything in non-XR environments (i.e. Phone and Tablet).
 *
 * [Subspace] attempts to use the SpatialUser's field of view as width/height constraints for the
 * subspace being created. If the calculation fails or if the `HEAD_TRACKING` Android permission is
 * not granted, the default field of view width/height values will be used.
 *
 * @param content The 3D content to render within this Subspace.
 */
@Composable
@ComposableOpenTarget(index = -1)
@Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun Subspace(content: @Composable @SubspaceComposable SpatialBoxScope.() -> Unit) {
    val activity = LocalContext.current.getActivity()

    // If spatial UI capabilities are not enabled, do nothing
    if (!LocalSpatialCapabilities.current.isSpatialUiEnabled || activity !is ComponentActivity)
        return

    if (currentComposer.applier is SubspaceNodeApplier) {
        // We are already in a Subspace, so we can just render the content directly
        SpatialBox(content = content)
    } else if (LocalIsInApplicationSubspace.current) {
        NestedSubspace(activity, content)
    } else {
        ApplicationSubspace(
            activity = activity,
            constraints = SubspaceDefaults.fallbackFieldOfViewConstraints,
            constraintsBehavior = ConstraintsBehavior.FieldOfView,
            content = content,
        )
    }
}

/** Defines the behavior for applying [VolumeConstraints] to an ApplicationSubspace. */
@JvmInline
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public value class ConstraintsBehavior private constructor(private val value: Int) {
    public companion object {
        /**
         * Use the passed-in [VolumeConstraints] directly, without attempting to calculate field of
         * view constraints.
         */
        public val Specified: ConstraintsBehavior = ConstraintsBehavior(0)

        /**
         * Attempt to calculate the [ApplicationSubspace]'s [VolumeConstraints] based on the
         * SpatialUser's field of view. If the field of view cannot be determined (e.g., due to the
         * perception stack not being ready), the [VolumeConstraints] provided to the
         * [ApplicationSubspace] will be used as a fallback.
         */
        public val FieldOfView: ConstraintsBehavior = ConstraintsBehavior(1)
    }
}

/**
 * Create a 3D area that the app can render spatial content into with optional [VolumeConstraints].
 *
 * [ApplicationSubspace] should be used to create the topmost [Subspace] in your application's
 * spatial UI hierarchy. This composable will throw an [IllegalStateException] if it is used to
 * create a Subspace that is nested within another [Subspace] or [ApplicationSubspace]. For nested
 * 3D content areas, use the [Subspace] composable.
 *
 * This composable is a no-op and does not render anything in non-XR environments (i.e., Phone and
 * Tablet).
 *
 * @param constraints The volume constraints to apply to this [ApplicationSubspace]. The behavior of
 *   these constraints depends on the [constraintsBehavior]. By default, this is set to the default
 *   field of view constraints.
 * @param constraintsBehavior Specifies how the provided [constraints] should be applied. Use
 *   [ConstraintsBehavior.Specified] to directly use the provided constraints, or
 *   [ConstraintsBehavior.FieldOfView] to attempt to use calculated field of view constraints,
 *   falling back to the provided constraints if calculation fails or if the `HEAD_TRACKING` Android
 *   permission is not granted.
 * @param content The 3D content to render within this Subspace
 */
@Composable
@ComposableOpenTarget(index = -1)
@Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun ApplicationSubspace(
    constraints: VolumeConstraints = SubspaceDefaults.fallbackFieldOfViewConstraints,
    constraintsBehavior: ConstraintsBehavior = ConstraintsBehavior.FieldOfView,
    content: @Composable @SubspaceComposable SpatialBoxScope.() -> Unit,
) {
    val activity = LocalContext.current.getActivity()

    // If spatial UI capabilities are not enabled, do nothing
    if (!LocalSpatialCapabilities.current.isSpatialUiEnabled || activity !is ComponentActivity)
        return

    if (currentComposer.applier is SubspaceNodeApplier) {
        // We are already in a Subspace, so we can just render the content directly
        SpatialBox(content = content)
    } else if (LocalIsInApplicationSubspace.current) {
        throw IllegalStateException("ApplicationSubspace cannot be nested within another Subspace.")
    } else {
        ApplicationSubspace(
            activity = activity,
            constraints = constraints,
            constraintsBehavior = constraintsBehavior,
            content = content,
        )
    }
}

/**
 * Create a Subspace that is rooted in the application space.
 *
 * This is used as the top-level [Subspace] within the context of the default task window. Nested
 * Subspaces should use their nearest Panel that contains the [Subspace] to determine the sizing
 * constraints and position of the [Subspace].
 *
 * In the near future when HSM is spatialized, the Subspace should consider the app bounds when
 * determining its top-level constraints.
 */
@Composable
private fun ApplicationSubspace(
    activity: ComponentActivity,
    constraints: VolumeConstraints,
    constraintsBehavior: ConstraintsBehavior,
    content: @Composable @SubspaceComposable SpatialBoxScope.() -> Unit,
) {

    val session = checkNotNull(LocalSession.current) { "session must be initialized" }
    val compositionContext = rememberCompositionContext()
    val rootConstraints =
        when (constraintsBehavior) {
            ConstraintsBehavior.Specified -> constraints
            else -> rememberCalculatedFovConstraints(constraints) ?: return
        }

    // TODO(b/406288019): Update the root constraints while maintaining the existing scene.
    val scene by
        remember(rootConstraints) {
            session.scene.mainPanelEntity.setHidden(true)
            disposableValueOf(
                SpatialComposeScene(
                    ownerActivity = activity,
                    jxrSession = session,
                    parentCompositionContext = compositionContext,
                    rootVolumeConstraints = rootConstraints,
                )
            ) {
                it.dispose()
                session.scene.mainPanelEntity.setHidden(false)
            }
        }

    scene.setContent {
        CompositionLocalProvider(LocalIsInApplicationSubspace provides true) {
            SpatialBox(content = content)
        }
    }
}

@Composable
private fun NestedSubspace(
    activity: ComponentActivity,
    content: @Composable @SubspaceComposable SpatialBoxScope.() -> Unit,
) {
    val session = checkNotNull(LocalSession.current) { "session must be initialized" }
    val compositionContext = rememberCompositionContext()
    val coreEntity = checkNotNull(LocalCoreEntity.current) { "CoreEntity unavailable for subspace" }
    // The subspace root node will be owned and manipulated by the containing composition, we need a
    // container that we can manipulate at the Subspace level in order to position the entire
    // subspace
    // properly.
    val subspaceRootContainer by remember {
        disposableValueOf(
            ContentlessEntity.create(session, "SubspaceRootContainer").apply {
                setParent(coreEntity.entity)
                setHidden(true)
            }
        ) {
            it.dispose()
        }
    }
    val scene by remember {
        val subspaceRoot =
            ContentlessEntity.create(session, "SubspaceRoot").apply {
                setParent(subspaceRootContainer)
            }
        disposableValueOf(
            SpatialComposeScene(
                ownerActivity = activity,
                jxrSession = session,
                parentCompositionContext = compositionContext,
                rootEntity = CoreContentlessEntity(subspaceRoot),
            )
        ) {
            it.dispose()
            subspaceRoot.dispose()
        }
    }
    var measuredSize by remember { mutableStateOf(IntVolumeSize.Zero) }
    var contentOffset by remember { mutableStateOf(Offset.Zero) }
    val viewSize = LocalView.current.size
    val density = LocalDensity.current

    LaunchedEffect(measuredSize, contentOffset, viewSize, density) {
        subspaceRootContainer.setPose(
            calculatePose(
                contentOffset,
                viewSize,
                measuredSize.run { IntSize(width, height) },
                density
            )
        )
        // We need to wait for a single frame to ensure that the pose changes are batched to the
        // root
        // container before we show it.
        if (subspaceRootContainer.isHidden(false) && awaitFrame() > 0) {
            subspaceRootContainer.setHidden(false)
        }
    }

    Layout(modifier = Modifier.onGloballyPositioned { contentOffset = it.positionInRoot() }) {
        _,
        constraints ->
        scene.setContent {
            SubspaceLayout(content = { SpatialBox(content = content) }) { measurables, _ ->
                val placeables =
                    measurables.map {
                        it.measure(
                            VolumeConstraints(
                                minWidth = constraints.minWidth,
                                maxWidth = constraints.maxWidth,
                                minHeight = constraints.minHeight,
                                maxHeight = constraints.maxHeight,
                                // TODO(b/366564066) Nested Subspaces should get their depth
                                // constraints from
                                // the parent Subspace
                                minDepth = 0,
                                maxDepth = Int.MAX_VALUE,
                            )
                        )
                    }
                measuredSize =
                    IntVolumeSize(
                        width = placeables.maxOf { it.measuredWidth },
                        height = placeables.maxOf { it.measuredHeight },
                        depth = placeables.maxOf { it.measuredDepth },
                    )
                layout(measuredSize.width, measuredSize.height, measuredSize.depth) {
                    placeables.forEach { it.place(Pose.Identity) }
                    subspaceRootContainer.setPose(
                        calculatePose(
                            contentOffset,
                            viewSize,
                            measuredSize.run { IntSize(width, height) },
                            density,
                        )
                    )
                }
            }
        }

        layout(measuredSize.width, measuredSize.height) {}
    }
}

private object PerceptionStackRetryConstants {
    const val MAX_ATTEMPTS = 50
    const val RETRY_INTERVAL_MILLIS = 10L
}

/**
 * Calculates [VolumeConstraints] based on the user's field of view relative to the ActivitySpace.
 *
 * Used internally by [ApplicationSubspace] when [ConstraintsBehavior.FieldOfView] is specified.
 *
 * Calculates the FOV width/height in pixels at the user's distance from the origin. Uses the
 * provided [fallbackFovConstraints] if the calculation times out, fails, or the distance is zero,
 * or the `HEAD_TRACKING` Android permission is not granted.
 *
 * If the perception stack components (Head, Cameras) are not yet available, it retries periodically
 * ([PerceptionStackRetryConstants.RETRY_INTERVAL_MILLIS]) up to a maximum number of attempts
 * ([PerceptionStackRetryConstants.MAX_ATTEMPTS]).
 *
 * If the perception stack is ready but the ActivitySpace scale is zero, it registers a listener to
 * trigger potential re-evaluation and continues retrying with delays. The listener is removed after
 * use.
 *
 * @param fallbackFovConstraints The [VolumeConstraints] to return if the FOV-based calculation
 *   fails (e.g., perception stack unavailable after retries), times out, or results in zero
 *   distance.
 * @return Initially `null` while calculating or waiting. Once the calculation finishes or times
 *   out, it returns either the dynamically calculated FOV-based [VolumeConstraints] or the
 *   [fallbackFovConstraints]. Callers relying on this function must handle the initial `null` state
 *   and subsequent recomposition when the non-null value becomes available.
 */
@Composable
private fun rememberCalculatedFovConstraints(
    fallbackFovConstraints: VolumeConstraints
): VolumeConstraints? {
    val session = LocalSession.current ?: return null
    val density = LocalDensity.current
    val calculatedFovConstraints = remember { mutableStateOf<VolumeConstraints?>(null) }

    if (session.config.headTracking == HeadTrackingMode.Disabled) {
        calculatedFovConstraints.value = fallbackFovConstraints
        return calculatedFovConstraints.value
    }

    val spaceUpdated = remember { mutableStateOf(false) }
    val activitySpace = session.scene.activitySpace

    LaunchedEffect(spaceUpdated) {
        var attempts = 0

        while (
            attempts < PerceptionStackRetryConstants.MAX_ATTEMPTS &&
                calculatedFovConstraints.value == null
        ) {
            val head: Head? = session.scene.spatialUser.head
            val leftCamera: CameraView? =
                session.scene.spatialUser.getCameraView(CameraType.LEFT_EYE)
            val rightCamera: CameraView? =
                session.scene.spatialUser.getCameraView(CameraType.RIGHT_EYE)
            val currentScale = activitySpace.getScale(Space.REAL_WORLD)
            if (head != null && leftCamera != null && rightCamera != null) {
                if (currentScale == 0f) {
                    activitySpace.setOnSpaceUpdatedListener({
                        if (!spaceUpdated.value) {
                            spaceUpdated.value = true
                        }
                    })
                    delay(PerceptionStackRetryConstants.RETRY_INTERVAL_MILLIS)
                    attempts++

                    continue
                }

                if (spaceUpdated.value) {
                    activitySpace.setOnSpaceUpdatedListener(null)
                }

                val distance: Meter =
                    getDistanceBetweenUserAndActivitySpaceOrigin(
                        head.getActivitySpacePose(),
                        activitySpace.getActivitySpacePose(),
                        currentScale,
                    )
                val fov = getSpatialUserFov(leftCamera.fov, rightCamera.fov)

                if (distance.value == 0.0f) {
                    calculatedFovConstraints.value = SubspaceDefaults.fallbackFieldOfViewConstraints
                } else {
                    calculatedFovConstraints.value =
                        VolumeConstraints(
                            minWidth = 0,
                            maxWidth = getFovWidthAtDistance(distance, fov, density),
                            minHeight = 0,
                            maxHeight = getFovHeightAtDistance(distance, fov, density),
                            minDepth = 0,
                            maxDepth = VolumeConstraints.INFINITY,
                        )
                }
            } else {
                delay(PerceptionStackRetryConstants.RETRY_INTERVAL_MILLIS)
                attempts++
            }
        }

        if (calculatedFovConstraints.value == null) {
            calculatedFovConstraints.value = fallbackFovConstraints
        }
    }

    return calculatedFovConstraints.value
}

/**
 * Calculates the distance between the SpatialUser's Head and the origin of Activity Space.
 *
 * The distance is calculated in physical reality meters, taking into account the Activity Space
 * scale.
 *
 * @param headActivitySpacePose The pose of the SpatialUser's head in Activity Space.
 * @param activitySpacePose The pose of the Activity Space origin in Activity Space
 * @param scale The scale factor of the Activity Space to real-world space.
 * @return The distance in [Meter].
 */
private fun getDistanceBetweenUserAndActivitySpaceOrigin(
    headActivitySpacePose: Pose,
    activitySpacePose: Pose,
    scale: Float,
): Meter {
    val distanceInActivitySpaceUnit: Float = Pose.distance(headActivitySpacePose, activitySpacePose)

    return Meter(distanceInActivitySpaceUnit / scale)
}

/**
 * Returns the combined field of view of the SpatialUser.
 *
 * Returns a [Fov] representing the maximum extent of the left and right cameras' fields of view.
 *
 * @param leftFov The field of view of the left camera.
 * @param rightFov The field of view of the right camera.
 * @return The combined field of view [Fov].
 */
private fun getSpatialUserFov(leftFov: Fov, rightFov: Fov): Fov {
    val combinedLeft: Float = min(leftFov.angleLeft, rightFov.angleLeft)
    val combinedRight: Float = max(leftFov.angleRight, rightFov.angleRight)
    val combinedUp: Float = max(leftFov.angleUp, rightFov.angleUp)
    val combinedDown: Float = min(leftFov.angleDown, rightFov.angleDown)

    return Fov(combinedLeft, combinedRight, combinedUp, combinedDown)
}

/**
 * Calculates the width in pixels corresponding to a field of view at a given distance.
 *
 * Takes into account the density to convert the width from meters to pixels.
 *
 * @param distance The distance to the object in [Meter].
 * @param fov The field of view [Fov].
 * @param density The current [Density].
 * @return The width in pixels.
 */
private fun getFovWidthAtDistance(distance: Meter, fov: Fov, density: Density): Int {
    val width: Meter = distance * (tan(fov.angleRight) - tan(fov.angleLeft))

    return width.roundToPx(density)
}

/**
 * Calculates the height in pixels corresponding to a field of view at a given distance.
 *
 * Takes into account the density to convert the height from meters to pixels.
 *
 * @param distance The distance to the object in [Meter].
 * @param fov The field of view [Fov].
 * @param density The current [Density].
 * @return The height in pixels.
 */
private fun getFovHeightAtDistance(distance: Meter, fov: Fov, density: Density): Int {
    val height: Meter = distance * (tan(fov.angleUp) - tan(fov.angleDown))

    return height.roundToPx(density)
}
