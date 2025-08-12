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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import androidx.xr.compose.platform.LocalCoreMainPanelEntity
import androidx.xr.compose.platform.LocalDialogManager
import androidx.xr.compose.platform.LocalOpaqueEntity
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.disposableValueOf
import androidx.xr.compose.platform.getValue
import androidx.xr.compose.subspace.layout.CorePanelEntity
import androidx.xr.compose.subspace.layout.SpatialRoundedCornerShape
import androidx.xr.compose.subspace.layout.SpatialShape
import androidx.xr.compose.subspace.layout.SubspaceLayout
import androidx.xr.compose.subspace.layout.SubspaceMeasurable
import androidx.xr.compose.subspace.layout.SubspaceMeasurePolicy
import androidx.xr.compose.subspace.layout.SubspaceMeasureResult
import androidx.xr.compose.subspace.layout.SubspaceMeasureScope
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.node.ComposeSubspaceNode
import androidx.xr.compose.subspace.node.ComposeSubspaceNode.Companion.SetCompositionLocalMap
import androidx.xr.compose.subspace.node.ComposeSubspaceNode.Companion.SetCoreEntity
import androidx.xr.compose.subspace.node.ComposeSubspaceNode.Companion.SetMeasurePolicy
import androidx.xr.compose.subspace.node.ComposeSubspaceNode.Companion.SetModifier
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
 */
@Composable
@SubspaceComposable
public fun <T : View> SpatialAndroidViewPanel(
    factory: (Context) -> T,
    modifier: SubspaceModifier = SubspaceModifier,
    update: (T) -> Unit = {},
    shape: SpatialShape = SpatialPanelDefaults.shape,
) {
    val dialogManager = LocalDialogManager.current
    val context = LocalContext.current

    @Suppress("UnnecessaryLambdaCreation")
    AndroidViewPanel(
        factory = { factory(context) },
        modifier = modifier,
        update = { view ->
            if (dialogManager.isSpatialDialogActive.value) {
                view.foreground = DEFAULT_SCRIM_ALPHA.toDrawable()
                view.setOnClickListener { dialogManager.isSpatialDialogActive.value = false }
            } else {
                view.foreground = Color.TRANSPARENT.toDrawable()
                view.setOnClickListener(null)
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

    val corePanelEntity =
        rememberCorePanelEntity(shape = shape) {
            PanelEntity.create(
                session = this,
                view = view,
                dimensions = SpatialPanelDimensions.minimumPanelDimension,
                name = "ViewPanel",
                pose = Pose.Identity,
            )
        }

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
 * @param content The composable content to render within the SpatialPanel.
 */
@Composable
@SubspaceComposable
public fun SpatialPanel(
    modifier: SubspaceModifier = SubspaceModifier,
    shape: SpatialShape = SpatialPanelDefaults.shape,
    content: @Composable @UiComposable () -> Unit,
) {
    val view = rememberComposeView()
    val corePanelEntity =
        rememberCorePanelEntity(shape = shape) {
            PanelEntity.create(
                session = this,
                view = view,
                dimensions = SpatialPanelDimensions.minimumPanelDimension,
                name = entityName("SpatialPanel"),
                pose = Pose.Identity,
            )
        }

    val measurePolicy = SpatialViewPanelMeasurePolicy(view)

    val compositionLocalMap = currentComposer.currentCompositionLocalMap

    // Set the content on the ComposeView.
    view.setContent {
        val dialogManager = LocalDialogManager.current
        val isDialogActive = dialogManager.isSpatialDialogActive.value

        CompositionLocalProvider(LocalOpaqueEntity provides corePanelEntity, content = content)

        if (isDialogActive) {
            Box(
                modifier =
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures { dialogManager.isSpatialDialogActive.value = false }
                    }
            ) {}
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
            set(modifier, SetModifier)
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
 */
@Composable
@SubspaceComposable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun SpatialMainPanel(
    modifier: SubspaceModifier = SubspaceModifier,
    shape: SpatialShape = SpatialPanelDefaults.shape,
) {
    val mainPanel = LocalCoreMainPanelEntity.current ?: return
    val density = LocalDensity.current
    val view = LocalView.current

    LaunchedEffect(shape, density) { mainPanel.setShape(shape, density) }

    SubspaceLayout(modifier = modifier, coreEntity = mainPanel) { _, constraints ->
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
 */
@Composable
@SubspaceComposable
public fun SpatialActivityPanel(
    intent: Intent,
    modifier: SubspaceModifier = SubspaceModifier,
    shape: SpatialShape = SpatialPanelDefaults.shape,
) {
    val session = checkNotNull(LocalSession.current) { "session must be initialized" }
    val dialogManager = LocalDialogManager.current
    val density = LocalDensity.current

    val pixelDimensions = IntSize2d(DEFAULT_SIZE_PX, DEFAULT_SIZE_PX)

    val activityPanelEntity: ActivityPanelEntity by
        remember(session, pixelDimensions) {
            disposableValueOf(
                ActivityPanelEntity.create(
                    session,
                    pixelDimensions,
                    entityName("ActivityPanel-${intent.action}"),
                )
            ) {
                it.dispose()
            }
        }

    val corePanelEntity: CorePanelEntity by
        remember(activityPanelEntity, density) {
            disposableValueOf(CorePanelEntity(activityPanelEntity)) { it.dispose() }
        }

    SideEffect { corePanelEntity.setShape(shape, density) }

    LaunchedEffect(intent) {
        (corePanelEntity.entity as ActivityPanelEntity).launchActivity(intent)
    }

    SpatialBox {
        SubspaceLayout(modifier = modifier, coreEntity = corePanelEntity) { _, constraints ->
            val width = DEFAULT_SIZE_PX.coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = DEFAULT_SIZE_PX.coerceIn(constraints.minHeight, constraints.maxHeight)
            val depth = constraints.minDepth.coerceAtLeast(0)
            layout(width, height, depth) {}
        }

        if (dialogManager.isSpatialDialogActive.value) {
            val localContext = LocalContext.current
            val scrimView =
                remember(localContext) {
                    View(localContext).apply {
                        foreground = DEFAULT_SCRIM_ALPHA.toDrawable()
                        setOnClickListener { dialogManager.isSpatialDialogActive.value = false }
                    }
                }

            val scrimPanelEntity by
                remember(session, corePanelEntity.entity, scrimView) {
                    disposableValueOf(
                        PanelEntity.create(
                                session = session,
                                view = scrimView,
                                dimensions = activityPanelEntity.size,
                                name = entityName("ScrimPanel"),
                                pose = Pose.Identity,
                            )
                            .apply {
                                parent = corePanelEntity.entity
                                setPose(Pose(translation = Vector3(0f, 0f, 3.millimeters.toM())))
                            }
                    ) {
                        it.dispose()
                    }
                }

            SideEffect {
                scrimPanelEntity.size = activityPanelEntity.size
                scrimPanelEntity.cornerRadius = activityPanelEntity.cornerRadius
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
