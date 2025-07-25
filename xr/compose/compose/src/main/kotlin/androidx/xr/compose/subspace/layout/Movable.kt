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

package androidx.xr.compose.subspace.layout

import androidx.annotation.RestrictTo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.subspace.node.CompositionLocalConsumerSubspaceModifierNode
import androidx.xr.compose.subspace.node.LayoutCoordinatesAwareModifierNode
import androidx.xr.compose.subspace.node.SubspaceLayoutModifierNode
import androidx.xr.compose.subspace.node.SubspaceModifierNodeElement
import androidx.xr.compose.subspace.node.currentValueOf
import androidx.xr.compose.subspace.node.requestRelayout
import androidx.xr.compose.unit.IntVolumeSize
import androidx.xr.compose.unit.VolumeConstraints
import androidx.xr.compose.unit.toDimensionsInMeters
import androidx.xr.compose.unit.toIntVolumeSize
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Ray
import androidx.xr.scenecore.BasePanelEntity
import androidx.xr.scenecore.Entity
import androidx.xr.scenecore.MovableComponent
import androidx.xr.scenecore.MoveListener
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

/**
 * When the movable modifier is present and enabled, draggable UI controls will be shown that allow
 * the user to move the element in 3D space. This feature is only available for [SpatialPanels] at
 * the moment.
 *
 * The order of the [SubspaceModifier]s is important. Please take note of this when using movable.
 * If you have the following modifier chain: SubspaceModifier.offset().size().movable(), the
 * modifiers will work as expected. If instead you have this modifier chain:
 * SubspaceModifier.size().offset().movable(), you will experience unexpected placement behavior
 * when using the movable modifier. In general, the offset modifier should be specified before the
 * size modifier, and the movable modifier should be specified last.
 *
 * @param enabled true if this composable should be movable.
 * @param stickyPose if enabled, the user specified position will be retained when the modifier is
 *   disabled or removed.
 * @param scaleWithDistance true if this composable should scale in size when moved in depth. When
 *   this scaleWithDistance is enabled, the subspace element moved will grow or shrink. It will also
 *   maintain any explicit scale that it had before movement.
 * @param onPoseChange a callback to process the pose change during movement, with translation in
 *   pixels. This will only be called if [enabled] is true. If the callback returns false or isn't
 *   specified, the default behavior of moving this composable's subspace hierarchy will be
 *   executed. If it returns true, it is the responsibility of the callback to process the event.
 * @see [PoseChangeEvent]
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun SubspaceModifier.movable(
    enabled: Boolean = true,
    stickyPose: Boolean = false,
    scaleWithDistance: Boolean = true,
    onPoseChange: ((PoseChangeEvent) -> Boolean)? = null,
): SubspaceModifier =
    this.then(MovableElement(enabled, onPoseChange, stickyPose, scaleWithDistance))

/**
 * An event representing a change in pose, scale, and size.
 *
 * @property pose The new pose of the composable in the subspace, relative to its parent, with its
 *   translation being expressed in pixels.
 * @property scale The scale of the composable as a result of its motion. This value will change
 *   with the composable's depth when scaleWithDistance is true on the modifier.
 * @property size The [IntVolumeSize] value that includes the width, height and depth of the
 *   composable, factoring in shrinking or stretching due to [scale].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class PoseChangeEvent(
    public var pose: Pose = Pose.Identity,
    public var scale: Float = 1.0F,
    public var size: IntVolumeSize = IntVolumeSize.Zero,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PoseChangeEvent) return false

        if (pose != other.pose) return false
        if (scale != other.scale) return false
        if (size != other.size) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pose.hashCode()
        result = 31 * result + scale.hashCode()
        result = 31 * result + size.hashCode()
        return result
    }

    override fun toString(): String {
        return "PoseChangeEvent(pose=$pose, scale=$scale, size=$size)"
    }
}

private class MovableElement(
    private val enabled: Boolean,
    private val onPoseChange: ((PoseChangeEvent) -> Boolean)?,
    private val stickyPose: Boolean,
    private val scaleWithDistance: Boolean,
) : SubspaceModifierNodeElement<MovableNode>() {

    override fun create(): MovableNode =
        MovableNode(
            enabled = enabled,
            stickyPose = stickyPose,
            onPoseChange = onPoseChange,
            scaleWithDistance = scaleWithDistance,
        )

    override fun update(node: MovableNode) {
        node.enabled = enabled
        node.onPoseChange = onPoseChange
        node.stickyPose = stickyPose
        node.scaleWithDistance = scaleWithDistance
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MovableElement) return false

        if (enabled != other.enabled) return false
        if (onPoseChange !== other.onPoseChange) return false
        if (stickyPose != other.stickyPose) return false
        if (scaleWithDistance != other.scaleWithDistance) return false

        return true
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + onPoseChange.hashCode()
        result = 31 * result + stickyPose.hashCode()
        result = 31 * result + scaleWithDistance.hashCode()
        return result
    }
}

internal class MovableNode(
    public var enabled: Boolean,
    public var stickyPose: Boolean,
    public var scaleWithDistance: Boolean,
    public var onPoseChange: ((PoseChangeEvent) -> Boolean)?,
) :
    SubspaceModifier.Node(),
    CompositionLocalConsumerSubspaceModifierNode,
    CoreEntityNode,
    LayoutCoordinatesAwareModifierNode,
    MoveListener,
    SubspaceLayoutModifierNode {

    private inline val density: Density
        get() = currentValueOf(LocalDensity)

    private inline val session: Session
        get() = checkNotNull(currentValueOf(LocalSession)) { "Movable requires a Session." }

    /** The previous pose of this entity from the last MoveEvent. */
    private var previousPose: Pose = Pose.Identity

    /** Pose based on user adjustments from MoveEvents from SceneCore. */
    private var userPose: Pose = Pose.Identity

    /** The scale of this entity when it is moved. */
    private var scaleFromMovement: Float = 1.0F

    private var component: MovableComponent? = null

    override fun CoreEntityScope.modifyCoreEntity() {
        updateState()
        setOrAppendScale(scaleFromMovement)
    }

    override fun onDetach() {
        if (component != null) {
            disableComponent()
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: VolumeConstraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.measuredWidth, placeable.measuredHeight, placeable.measuredDepth) {
            placeable.place(userPose)
        }
    }

    override fun onLayoutCoordinates(coordinates: SubspaceLayoutCoordinates) {
        // Update the size of the component to match the final size of the layout.
        component?.size = coordinates.size.toDimensionsInMeters(density)
    }

    /** Updates the movable state of this CoreEntity. */
    private fun updateState() {
        if (coreEntity !is MovableCoreEntity) {
            return
        }
        // Enabled is on the Node. It means "should be enabled" for the Component.
        if (enabled && component == null) {
            enableComponent()
        } else if (!enabled && component != null) {
            disableComponent()
        }
    }

    /** Enables the MovableComponent for this CoreEntity. */
    private fun enableComponent() {
        check(component == null) { "MovableComponent already enabled." }
        component = MovableComponent.create(session, systemMovable = false)
        check(component?.let { coreEntity.addComponent(it) } == true) {
            "Could not add MovableComponent to Core Entity."
        }
        component?.addMoveListener(MainExecutor, this)
    }

    /**
     * Disables the MovableComponent for this CoreEntity. Takes care of life cycle tasks for the
     * underlying component in SceneCore.
     */
    private fun disableComponent() {
        check(component != null) { "MovableComponent already disabled." }
        component?.removeMoveListener(this)
        component?.let { coreEntity.removeComponent(it) }
        component = null
        if (!stickyPose) {
            userPose = Pose.Identity
            requestRelayout()
        }
    }

    override fun onMoveStart(
        entity: Entity,
        initialInputRay: Ray,
        initialPose: Pose,
        initialScale: Float,
        initialParent: Entity,
    ) {
        // updatePoseOnMove() not called because there is no previous pose to compare to.
        previousPose = initialPose
    }

    override fun onMoveUpdate(
        entity: Entity,
        currentInputRay: Ray,
        currentPose: Pose,
        currentScale: Float,
    ) {
        updatePoseOnMove(
            previousPose,
            currentPose,
            currentScale,
            when (entity) {
                is BasePanelEntity<*> -> entity.getSize().toIntVolumeSize(density)
                else -> IntVolumeSize.Zero
            },
        )
        previousPose = currentPose
    }

    override fun onMoveEnd(
        entity: Entity,
        finalInputRay: Ray,
        finalPose: Pose,
        finalScale: Float,
        updatedParent: Entity?,
    ) {
        updatePoseOnMove(
            previousPose,
            finalPose,
            finalScale,
            when (entity) {
                is BasePanelEntity<*> -> entity.getSize().toIntVolumeSize(density)
                else -> IntVolumeSize.Zero
            },
        )
        previousPose = Pose.Identity
    }

    /** Called every time there is a MoveEvent in SceneCore, if this CoreEntity is movable. */
    private fun updatePoseOnMove(
        previousPose: Pose,
        nextPose: Pose,
        scale: Float,
        size: IntVolumeSize,
    ) {
        if (!enabled) {
            return
        }

        // SceneCore uses meters, Compose XR uses pixels.
        val previousCorePose = previousPose.convertMetersToPixels(density)
        val corePose = nextPose.convertMetersToPixels(density)

        if (onPoseChange?.invoke(PoseChangeEvent(corePose, scale, size)) == true) {
            // We're done, the user app will handle the event.
            return
        }

        // Find the delta from the previous move event.
        val coreDeltaPose =
            Pose(
                corePose.translation - previousCorePose.translation,
                previousCorePose.rotation.inverse * corePose.rotation,
            )
        userPose =
            Pose(
                userPose.translation + coreDeltaPose.translation,
                userPose.rotation * coreDeltaPose.rotation,
            )
        if (scaleWithDistance) {
            scaleFromMovement = scale
        }
        requestRelayout()
    }

    public companion object {
        private val MainExecutor: Executor = Dispatchers.Main.asExecutor()
    }
}
