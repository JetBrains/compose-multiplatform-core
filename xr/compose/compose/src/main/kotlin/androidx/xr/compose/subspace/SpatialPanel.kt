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

package androidx.xr.compose.subspace

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.View.MeasureSpec
import androidx.annotation.RestrictTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import androidx.xr.compose.platform.LocalCoreMainPanelEntity
import androidx.xr.compose.platform.LocalDialogManager
import androidx.xr.compose.platform.LocalOpaqueEntity
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.disposableValueOf
import androidx.xr.compose.platform.getValue
import androidx.xr.compose.subspace.layout.CoreActivityPanelEntity
import androidx.xr.compose.subspace.layout.CorePanelEntity
import androidx.xr.compose.subspace.layout.PlaneOrientation
import androidx.xr.compose.subspace.layout.PlaneSemantic
import androidx.xr.compose.subspace.layout.SpatialMoveEndEvent
import androidx.xr.compose.subspace.layout.SpatialMoveEvent
import androidx.xr.compose.subspace.layout.SpatialMoveStartEvent
import androidx.xr.compose.subspace.layout.SpatialRoundedCornerShape
import androidx.xr.compose.subspace.layout.SpatialShape
import androidx.xr.compose.subspace.layout.SubspaceLayout
import androidx.xr.compose.subspace.layout.SubspaceMeasurable
import androidx.xr.compose.subspace.layout.SubspaceMeasurePolicy
import androidx.xr.compose.subspace.layout.SubspaceMeasureResult
import androidx.xr.compose.subspace.layout.SubspaceMeasureScope
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.anchorable
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.resizable
import androidx.xr.compose.subspace.node.ComposeSubspaceNode
import androidx.xr.compose.subspace.node.ComposeSubspaceNode.Companion.SetCompositionLocalMap
import androidx.xr.compose.subspace.node.ComposeSubspaceNode.Companion.SetCoreEntity
import androidx.xr.compose.subspace.node.ComposeSubspaceNode.Companion.SetMeasurePolicy
import androidx.xr.compose.subspace.node.ComposeSubspaceNode.Companion.SetModifier
import androidx.xr.compose.unit.DpVolumeSize
import androidx.xr.compose.unit.IntVolumeSize
import androidx.xr.compose.unit.Meter.Companion.millimeters
import androidx.xr.compose.unit.VolumeConstraints
import androidx.xr.runtime.math.FloatSize2d
import androidx.xr.runtime.math.IntSize2d
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.ActivityPanelEntity
import androidx.xr.scenecore.PanelEntity

private const val DEFAULT_SIZE_PX = 400

// Max allowed size for makeMeasureSpec is (1 << MeasureSpec.MODE_SHIFT) - 1.
private const val MAX_MEASURE_SPEC_SIZE = (1 shl 30) - 1

/** Set the scrim alpha to 32% opacity across all spatial panels. */
private const val DEFAULT_SCRIM_ALPHA = 0x52000000

private object SpatialPanelDimensions {
    /** Default minimum dimensions for a Spatial Panel in Meters. */
    val minimumPanelDimension: FloatSize2d = FloatSize2d(0.1f, 0.1f)
}

/** Contains default values used by spatial panels. */
public object SpatialPanelDefaults {

    /** Default shape for a Spatial Panel. */
    public val shape: SpatialShape = SpatialRoundedCornerShape(CornerSize(32.dp))
}

/**
 * Base Policy for motion behavior of spatial objects.
 *
 * This class serves as the foundation for defining how a spatial object can be moved or anchored in
 * the environment. Implementations of this class, such as [MovePolicy] and [AnchorPolicy], are
 * mutually exclusive.
 */
public abstract class DragPolicy internal constructor()

/**
 * Represents the anchoring behavior of a spatial object.
 *
 * An AnchorPolicy object can be placed and re-anchored on detected surfaces in the environment.
 * This class defines properties that control how anchoring behaves, such as whether it's enabled
 * and what types of planes it can anchor to.
 *
 * This functionality requires the
 * [android.permission.SCENE_UNDERSTANDING_COARSE][androidx.xr.runtime.manifest.SCENE_UNDERSTANDING_COARSE]
 * permission. If this permission is not granted, anchoring will be disabled and the element will
 * behave as if this policy was not applied.
 *
 * @property isEnabled Whether anchoring is enabled for this object. If `false`, the object will not
 *   be able to anchor to surfaces. Defaults to `true`.
 * @property anchorPlaneOrientations A set of [PlaneOrientation] values that define the orientations
 *   of planes this object can anchor to. An empty set means anchoring is not restricted by
 *   orientation. For example, [PlaneOrientation.Horizontal] for floors/ceilings or
 *   [PlaneOrientation.Vertical] for walls. Defaults to an empty set.
 * @property anchorPlaneSemantics A set of [PlaneSemantic] values that define the semantic types of
 *   planes this object can anchor to. An empty set means anchoring is not restricted by semantic
 *   type. For example, [PlaneSemantic.Floor] or [PlaneSemantic.Wall]. Defaults to an empty set.
 */
public class AnchorPolicy(
    public val isEnabled: Boolean = true,
    @Suppress("PrimitiveInCollection")
    public val anchorPlaneOrientations: Set<PlaneOrientation> = emptySet(),
    @Suppress("PrimitiveInCollection")
    public val anchorPlaneSemantics: Set<PlaneSemantic> = emptySet(),
) : DragPolicy() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnchorPolicy) return false
        if (isEnabled != other.isEnabled) return false
        if (anchorPlaneOrientations != other.anchorPlaneOrientations) return false
        if (anchorPlaneSemantics != other.anchorPlaneSemantics) return false
        return true
    }

    override fun hashCode(): Int {
        var result = anchorPlaneOrientations.hashCode()
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + anchorPlaneSemantics.hashCode()
        return result
    }

    override fun toString(): String {
        return "AnchorPolicy(enabled=$isEnabled, anchorPlaneOrientations=$anchorPlaneOrientations, " +
            "anchorPlaneSemantics=$anchorPlaneSemantics)"
    }
}

/**
 * Defines the movement policy for a spatial object.
 *
 * This class configures how a spatial object can be moved by user interaction or programmatic
 * changes. It provides options for enabling/disabling movement, controlling "stickiness" to its
 * current pose, and defining callbacks for various stages of the move operation.
 *
 * @property isEnabled Whether movement is enabled for this object. If `false`, the object cannot be
 *   moved. Defaults to `true`.
 * @property isStickyPose If `true`, the object will attempt to maintain its relative position and
 *   orientation to the user's view or the environment when moved, making it feel "sticky." If
 *   `false`, movement will be more direct. Defaults to `false`.
 * @property shouldScaleWithDistance If `true`, the object's perceived size will scale with its
 *   distance from the user during movement, giving an illusion of constant visual size. If `false`,
 *   its physical size remains constant. Defaults to `true`.
 * @property onMoveStart A callback function invoked when a move operation begins. It receives a
 *   [SpatialMoveStartEvent] providing initial move details. Defaults to `null`.
 * @property onMoveEnd A callback function invoked when a move operation ends. It receives a
 *   [SpatialMoveEndEvent] providing final move details. Defaults to `null`.
 * @property onMove A callback function invoked repeatedly during a move operation. It receives a
 *   [SpatialMoveEvent] with current move details and should return `true` to indicate the move
 *   should continue, or `false` to cancel it. Defaults to `null`.
 */
public class MovePolicy(
    public val isEnabled: Boolean = true,
    public val isStickyPose: Boolean = false,
    @get:JvmName("shouldScaleWithDistance") public val shouldScaleWithDistance: Boolean = true,
    public val onMoveStart: ((SpatialMoveStartEvent) -> Unit)? = null,
    public val onMoveEnd: ((SpatialMoveEndEvent) -> Unit)? = null,
    public val onMove: ((SpatialMoveEvent) -> Boolean)? = null,
) : DragPolicy() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MovePolicy) return false
        if (isEnabled != other.isEnabled) return false
        if (isStickyPose != other.isStickyPose) return false
        if (shouldScaleWithDistance != other.shouldScaleWithDistance) return false
        if (onMoveStart !== other.onMoveStart) return false
        if (onMoveEnd !== other.onMoveEnd) return false
        if (onMove !== other.onMove) return false
        return true
    }

    override fun hashCode(): Int {
        var result = isStickyPose.hashCode()
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + shouldScaleWithDistance.hashCode()
        result = 31 * result + onMoveStart.hashCode()
        result = 31 * result + onMoveEnd.hashCode()
        result = 31 * result + onMove.hashCode()
        return result
    }

    override fun toString(): String {
        return "MovePolicy(enabled=$isEnabled, stickyPose=$isStickyPose, " +
            "scaleWithDistance=$shouldScaleWithDistance, onMoveStart=$onMoveStart, onMoveEnd=$onMoveEnd, " +
            "onMove=$onMove)"
    }
}

/**
 * Defines the resizing policy for a spatial object.
 *
 * This class specifies how a spatial object can be resized, including enabling/disabling resizing,
 * setting minimum and maximum size constraints, and controlling aspect ratio maintenance.
 *
 * @property isEnabled Whether resizing is enabled for this object. If `false`, the object cannot be
 *   resized. Defaults to `true`.
 * @property minimumSize The minimum allowable size for the object, represented by a [DpVolumeSize].
 *   The object cannot be scaled down beyond these dimensions. Defaults to [DpVolumeSize.Zero].
 * @property maximumSize The maximum allowable size for the object, represented by a [DpVolumeSize].
 *   The object cannot be scaled up beyond these dimensions. Defaults to a [DpVolumeSize] with all
 *   dimensions set to [Dp.Infinity], meaning no upper limit by default.
 * @property shouldMaintainAspectRatio If `true`, the object's aspect ratio (proportions) will be
 *   preserved during resizing. If `false`, individual dimensions can be changed independently.
 *   Defaults to `false`.
 * @property onSizeChange A callback function invoked when the object's size changes. It receives an
 *   [IntVolumeSize] representing the new size and should return `true` to accept the size change,
 *   or `false` to reject it. Defaults to `null`.
 */
public class ResizePolicy(
    public val isEnabled: Boolean = true,
    public val minimumSize: DpVolumeSize = DpVolumeSize.Zero,
    public val maximumSize: DpVolumeSize = DpVolumeSize(Dp.Infinity, Dp.Infinity, Dp.Infinity),
    @get:JvmName("shouldMaintainAspectRatio") public val shouldMaintainAspectRatio: Boolean = false,
    public val onSizeChange: ((IntVolumeSize) -> Boolean)? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResizePolicy) return false
        if (isEnabled != other.isEnabled) return false
        if (minimumSize != other.minimumSize) return false
        if (maximumSize != other.maximumSize) return false
        if (shouldMaintainAspectRatio != other.shouldMaintainAspectRatio) return false
        return true
    }

    override fun hashCode(): Int {
        var result = minimumSize.hashCode()
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + maximumSize.hashCode()
        result = 31 * result + shouldMaintainAspectRatio.hashCode()
        result = 31 * result + onSizeChange.hashCode()
        return result
    }

    override fun toString(): String {
        return "ResizePolicy(enabled=$isEnabled, minimumSize=$minimumSize, maximumSize=$maximumSize, " +
            "maintainAspectRatio=$shouldMaintainAspectRatio, onSizeChange=$onSizeChange)"
    }
}

/**
 * Creates a [SpatialAndroidViewPanel] representing a 2D plane in 3D space where an Android View
 * will be hosted.
 *
 * The presented View is obtained from [factory]. The [factory] block will be called exactly once to
 * obtain the [View] being composed into this panel, and it is also guaranteed to be executed on the
 * main thread. Therefore, in addition to creating the [View], the [factory] block can also be used
 * to perform one-off initializations and [View] constant properties' setting. The factory inside of
 * the constructor is used to avoid the need to pass the context to the factory. There is one [View]
 * for every [SpatialAndroidViewPanel] instance and it is reused across recompositions. This [View]
 * is shown effectively in isolation and does not interact directly with the other composable's that
 * surround it. The [update] block can run multiple times (on the UI thread as well) due to
 * recomposition, and it is the right place to set the new properties. Note that the block will also
 * run once right after the [factory] block completes. [SpatialAndroidViewPanel] will clip the view
 * content to fit the panel.
 *
 * @param T The type of the Android View to be created.
 * @param factory A lambda that creates an instance of the Android View [T].
 * @param modifier SubspaceModifiers to apply to the SpatialPanel.
 * @param update A lambda that allows updating the created Android View [T].
 * @param shape The shape of this Spatial Panel.
 * @param dragPolicy An optional [DragPolicy] that defines the motion behavior of the
 *   [SpatialPanel]. This can be either a [MovePolicy] for free movement or an [AnchorPolicy] for
 *   anchoring to real-world surfaces. If a policy is provided, draggable UI controls will be shown,
 *   allowing the user to manipulate the panel in 3D space. If null, no motion behavior is applied.
 * @param resizePolicy An optional [ResizePolicy] configuration object that resizing behavior of
 *   this [SpatialPanel]. The draggable UI controls will be shown that allow the user to resize the
 *   element in 3D space. If null, there is no resize behavior applied to the element.
 */
@Composable
@SubspaceComposable
public fun <T : View> SpatialAndroidViewPanel(
    factory: (Context) -> T,
    modifier: SubspaceModifier = SubspaceModifier,
    update: (T) -> Unit = {},
    shape: SpatialShape = SpatialPanelDefaults.shape,
    dragPolicy: DragPolicy? = null,
    resizePolicy: ResizePolicy? = null,
) {
    val finalModifier =
        buildSpatialPanelModifier(
            baseModifier = modifier,
            dragPolicy = dragPolicy,
            resizePolicy = resizePolicy,
        )
    val dialogManager = LocalDialogManager.current
    val context = LocalContext.current

    @Suppress("UnnecessaryLambdaCreation")
    AndroidViewPanel(
        factory = { factory(context) },
        modifier = finalModifier,
        update = { view ->
            if (dialogManager.isSpatialDialogActive.value) {
                view.setOnClickListener(null)
                view.foreground = DEFAULT_SCRIM_ALPHA.toDrawable()
            } else {
                // Re-enable clicks without any action
                view.setOnClickListener {}
                view.foreground = Color.TRANSPARENT.toDrawable()
            }
            update(view)
        },
        shape = shape,
    )
}

/**
 * Private [AndroidViewPanel] implementation that reports its created PanelEntity. ComposeNode is
 * used directly for better timing when it comes to Update invocations.
 *
 * @param factory A lambda that creates an instance of the Android View [T].
 * @param modifier SubspaceModifiers.
 * @param update A lambda that allows updating the created Android View [T].
 * @param shape The shape of this Spatial Panel.
 */
@Composable
@SubspaceComposable
private fun <T : View> AndroidViewPanel(
    factory: (Context) -> T,
    modifier: SubspaceModifier = SubspaceModifier,
    update: (T) -> Unit = {},
    shape: SpatialShape,
) {
    val context = LocalContext.current
    val view = remember { factory(context) }
    val session = checkNotNull(LocalSession.current) { "session must be initialized" }
    val density = LocalDensity.current

    val corePanelEntity: CorePanelEntity = remember {
        CorePanelEntity(
                PanelEntity.create(
                    session = session,
                    view = view,
                    dimensions = SpatialPanelDimensions.minimumPanelDimension,
                    name = "ViewPanel:${view.id}",
                    pose = Pose.Identity,
                )
            )
            .also { it.setShape(shape, density) }
    }

    LaunchedEffect(shape, density) { corePanelEntity.setShape(shape, density) }

    val measurePolicy = SpatialViewPanelMeasurePolicy(view)

    val compositionLocalMap = currentComposer.currentCompositionLocalMap
    ComposeNode<ComposeSubspaceNode, Applier<Any>>(
        factory = ComposeSubspaceNode.Constructor,
        update = {
            set(compositionLocalMap, SetCompositionLocalMap)
            set(measurePolicy, SetMeasurePolicy)
            set(corePanelEntity, SetCoreEntity)
            set(modifier, SetModifier)
            update(view)
        },
    )
}

/**
 * Creates a [SpatialPanel] representing a 2D plane in 3D space in which an application can fill
 * content.
 *
 * @param modifier SubspaceModifiers to apply to the SpatialPanel.
 * @param shape The shape of this Spatial Panel.
 * @param dragPolicy An optional [DragPolicy] that defines the motion behavior of the
 *   [SpatialPanel]. This can be either a [MovePolicy] for free movement or an [AnchorPolicy] for
 *   anchoring to real-world surfaces. If a policy is provided, draggable UI controls will be shown,
 *   allowing the user to manipulate the panel in 3D space. If null, no motion behavior is applied.
 * @param resizePolicy An optional [ResizePolicy] that defines the resizing behavior of this
 *   [SpatialPanel]. If a policy is provided, resize UI controls will be shown, allowing the user to
 *   resize the element in 3D space. If null, no resize behavior is applied to the element.
 * @param content The composable content to render within the SpatialPanel.
 */
@Composable
@SubspaceComposable
public fun SpatialPanel(
    modifier: SubspaceModifier = SubspaceModifier,
    shape: SpatialShape = SpatialPanelDefaults.shape,
    dragPolicy: DragPolicy? = null,
    resizePolicy: ResizePolicy? = null,
    content: @Composable @UiComposable () -> Unit,
) {
    val finalModifier =
        buildSpatialPanelModifier(
            baseModifier = modifier,
            dragPolicy = dragPolicy,
            resizePolicy = resizePolicy,
        )
    val view = rememberComposeView()
    val session = checkNotNull(LocalSession.current) { "session must be initialized" }
    val density = LocalDensity.current

    val corePanelEntity: CorePanelEntity = remember {
        CorePanelEntity(
                PanelEntity.create(
                    session = session,
                    view = view,
                    dimensions = SpatialPanelDimensions.minimumPanelDimension,
                    name = entityName("SpatialPanel"),
                    pose = Pose.Identity,
                )
            )
            .also { it.setShape(shape, density) }
    }

    LaunchedEffect(shape, density) { corePanelEntity.setShape(shape, density) }

    val measurePolicy = SpatialViewPanelMeasurePolicy(view)

    val compositionLocalMap = currentComposer.currentCompositionLocalMap

    // Set the content on the ComposeView.
    view.setContent {
        val dialogManager = LocalDialogManager.current
        val isDialogActive = dialogManager.isSpatialDialogActive.value

        // The root is a Box. Its size is determined by its content.
        Box {
            // The user's content is the first child. It determines the size of the parent Box.
            CompositionLocalProvider(LocalOpaqueEntity provides corePanelEntity, content = content)

            // The scrim for input handling. It uses matchParentSize to avoid affecting
            // the measurement of the parent Box.
            if (isDialogActive) {
                Box(
                    modifier =
                        Modifier.matchParentSize() // This sizes the overlay without affecting the
                            // parent's size.
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    // Prevent clicks to compose
                                }
                            }
                )
            }
        }

        SideEffect {
            view.foreground =
                if (isDialogActive) {
                    DEFAULT_SCRIM_ALPHA.toDrawable()
                } else {
                    Color.TRANSPARENT.toDrawable()
                }
        }
    }

    ComposeNode<ComposeSubspaceNode, Applier<Any>>(
        factory = ComposeSubspaceNode.Constructor,
        update = {
            set(compositionLocalMap, SetCompositionLocalMap)
            set(measurePolicy, SetMeasurePolicy)
            set(corePanelEntity, SetCoreEntity)
            set(finalModifier, SetModifier)
        },
    )
}

/**
 * Creates a [SpatialPanel] backed by the main Window content.
 *
 * This panel requires the following specific configuration in the Android Manifest for proper
 * sizing/resizing behavior:
 * ```
 * <activity
 * android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize>
 * <!--suppress AndroidElementNotAllowed -->
 * <layout android:defaultWidth="50dp" android:defaultHeight="50dp" android:minHeight="50dp"
 * android:minWidth="50dp"/>
 * </activity>
 * ```
 *
 * @param modifier SubspaceModifier to apply to the MainPanel.
 * @param shape The shape of this Spatial Panel.
 * @param dragPolicy An optional [DragPolicy] that defines the motion behavior of the
 *   [SpatialPanel]. This can be either a [MovePolicy] for free movement or an [AnchorPolicy] for
 *   anchoring to real-world surfaces. If a policy is provided, draggable UI controls will be shown,
 *   allowing the user to manipulate the panel in 3D space. If null, no motion behavior is applied.
 * @param resizePolicy An optional [ResizePolicy] configuration object that resizing behavior of
 *   this [SpatialPanel]. The draggable UI controls will be shown that allow the user to resize the
 *   element in 3D space. If null, there is no resize behavior applied to the element.
 */
@Composable
@SubspaceComposable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun SpatialMainPanel(
    modifier: SubspaceModifier = SubspaceModifier,
    shape: SpatialShape = SpatialPanelDefaults.shape,
    dragPolicy: DragPolicy? = null,
    resizePolicy: ResizePolicy? = null,
) {
    val finalModifier =
        buildSpatialPanelModifier(
            baseModifier = modifier,
            dragPolicy = dragPolicy,
            resizePolicy = resizePolicy,
        )
    val mainPanel = LocalCoreMainPanelEntity.current ?: return
    val density = LocalDensity.current
    val view = LocalView.current

    LaunchedEffect(shape, density) { mainPanel.setShape(shape, density) }

    SubspaceLayout(modifier = finalModifier, coreEntity = mainPanel) { _, constraints ->
        val width = view.measuredWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = view.measuredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        val depth = constraints.minDepth.coerceAtLeast(0)
        layout(width, height, depth) {}
    }
}

/**
 * Creates a [SpatialActivityPanel] and launches an Activity within it.
 *
 * The only supported use case for this SpatialPanel is to launch activities that are a part of the
 * same application.
 *
 * @param intent The intent of an Activity to launch within this panel.
 * @param modifier SubspaceModifiers to apply to the SpatialPanel.
 * @param shape The shape of this Spatial Panel.
 * @param dragPolicy An optional [DragPolicy] that defines the motion behavior of the
 *   [SpatialPanel]. This can be either a [MovePolicy] for free movement or an [AnchorPolicy] for
 *   anchoring to real-world surfaces. If a policy is provided, draggable UI controls will be shown,
 *   allowing the user to manipulate the panel in 3D space. If null, no motion behavior is applied.
 * @param resizePolicy An optional [ResizePolicy] configuration object that resizing behavior of
 *   this [SpatialPanel]. The draggable UI controls will be shown that allow the user to resize the
 *   element in 3D space. If null, there is no resize behavior applied to the element.
 */
@Composable
@SubspaceComposable
public fun SpatialActivityPanel(
    intent: Intent,
    modifier: SubspaceModifier = SubspaceModifier,
    shape: SpatialShape = SpatialPanelDefaults.shape,
    dragPolicy: DragPolicy? = null,
    resizePolicy: ResizePolicy? = null,
) {
    val finalModifier =
        buildSpatialPanelModifier(
            baseModifier = modifier,
            dragPolicy = dragPolicy,
            resizePolicy = resizePolicy,
        )
    val session = checkNotNull(LocalSession.current) { "session must be initialized" }
    val dialogManager = LocalDialogManager.current
    val density = LocalDensity.current

    val pixelDimensions = IntSize2d(DEFAULT_SIZE_PX, DEFAULT_SIZE_PX)

    val corePanelEntity: CoreActivityPanelEntity = remember {
        CoreActivityPanelEntity(
            ActivityPanelEntity.create(
                session,
                pixelDimensions,
                entityName("ActivityPanel-${intent.action}"),
            )
        )
    }

    SideEffect { corePanelEntity.setShape(shape, density) }

    LaunchedEffect(intent) { corePanelEntity.startActivity(intent) }

    SpatialBox {
        SubspaceLayout(modifier = finalModifier, coreEntity = corePanelEntity) { _, constraints ->
            val width = DEFAULT_SIZE_PX.coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = DEFAULT_SIZE_PX.coerceIn(constraints.minHeight, constraints.maxHeight)
            val depth = constraints.minDepth.coerceAtLeast(0)
            layout(width, height, depth) {}
        }

        if (dialogManager.isSpatialDialogActive.value) {
            val localContext = LocalContext.current
            val scrimView =
                remember(localContext) {
                    View(localContext).apply { foreground = DEFAULT_SCRIM_ALPHA.toDrawable() }
                }

            val scrimPanelEntity by
                remember(session, scrimView) {
                    disposableValueOf(
                        CorePanelEntity(
                                PanelEntity.create(
                                    session = session,
                                    view = scrimView,
                                    pixelDimensions =
                                        corePanelEntity.size.run { IntSize2d(width, height) },
                                    name = entityName("ScrimPanel"),
                                    pose = Pose.Identity,
                                )
                            )
                            .apply {
                                parent = corePanelEntity
                                poseInMeters =
                                    Pose(translation = Vector3(0f, 0f, 3.millimeters.toM()))
                            }
                    ) {
                        it.dispose()
                    }
                }

            SideEffect {
                scrimPanelEntity.size = corePanelEntity.mutableSize
                scrimPanelEntity.setShape(shape, density)
            }
        }
    }
}

private class SpatialViewPanelMeasurePolicy(private val view: View) : SubspaceMeasurePolicy {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpatialViewPanelMeasurePolicy) return false
        return view == other.view
    }

    override fun hashCode(): Int {
        return view.hashCode()
    }

    override fun SubspaceMeasureScope.measure(
        measurables: List<SubspaceMeasurable>,
        constraints: VolumeConstraints,
    ): SubspaceMeasureResult {
        view.measure(
            MeasureSpec.makeMeasureSpec(
                constraints.maxWidth.coerceAtMost(MAX_MEASURE_SPEC_SIZE),
                MeasureSpec.AT_MOST,
            ),
            MeasureSpec.makeMeasureSpec(
                constraints.maxHeight.coerceAtMost(MAX_MEASURE_SPEC_SIZE),
                MeasureSpec.AT_MOST,
            ),
        )
        // The measured size of the view is used to lay out the SubspaceNode.
        val width = view.measuredWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = view.measuredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        val depth = constraints.minDepth.coerceAtLeast(0)
        return layout(width, height, depth) {}
    }
}

/**
 * Applies move, anchor, and resize policies to a [SubspaceModifier], returning the combined final
 * modifier. This is a private helper function for [SpatialPanel] and [SpatialExternalSurface].
 *
 * @param baseModifier The initial [SubspaceModifier] to which policies will be applied.
 * @param dragPolicy An optional [AnchorPolicy] or [MovePolicy] to configure either anchoring or
 *   movement behavior.
 * @param resizePolicy An optional [ResizePolicy] to configure resizing behavior.
 * @return A [SubspaceModifier] with all applicable policies integrated.
 */
@Suppress("DEPRECATION")
internal fun buildSpatialPanelModifier(
    baseModifier: SubspaceModifier,
    dragPolicy: DragPolicy?,
    resizePolicy: ResizePolicy?,
): SubspaceModifier {

    var finalModifier =
        when (dragPolicy) {
            is AnchorPolicy ->
                baseModifier.anchorable(
                    enabled = dragPolicy.isEnabled,
                    anchorPlaneOrientations = dragPolicy.anchorPlaneOrientations,
                    anchorPlaneSemantics = dragPolicy.anchorPlaneSemantics,
                )

            is MovePolicy ->
                baseModifier.movable(
                    enabled = dragPolicy.isEnabled,
                    stickyPose = dragPolicy.isStickyPose,
                    scaleWithDistance = dragPolicy.shouldScaleWithDistance,
                    onMoveStart = dragPolicy.onMoveStart,
                    onMoveEnd = dragPolicy.onMoveEnd,
                    onMove = dragPolicy.onMove,
                )

            else -> {
                baseModifier
            }
        }

    if (resizePolicy != null) {
        finalModifier =
            finalModifier.resizable(
                enabled = resizePolicy.isEnabled,
                minimumSize = resizePolicy.minimumSize,
                maximumSize = resizePolicy.maximumSize,
                maintainAspectRatio = resizePolicy.shouldMaintainAspectRatio,
                onSizeChange = resizePolicy.onSizeChange,
            )
    }
    return finalModifier
}
