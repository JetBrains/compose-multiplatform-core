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

import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RestrictTo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.xr.compose.platform.LocalDialogManager
import androidx.xr.compose.platform.LocalOpaqueEntity
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.subspace.layout.CoreBasePanelEntity
import androidx.xr.compose.subspace.layout.CorePanelEntity
import androidx.xr.compose.subspace.layout.SpatialRoundedCornerShape
import androidx.xr.compose.subspace.layout.SpatialShape
import androidx.xr.compose.subspace.layout.SubspaceLayout
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.unit.Meter.Companion.millimeters
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.ActivityPanelEntity
import androidx.xr.scenecore.Dimensions
import androidx.xr.scenecore.PanelEntity

private const val DEFAULT_SIZE_PX = 400

/** Contains default values used by spatial panels. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public object SpatialPanelDefaults {

    /** Default shape for a Spatial Panel. */
    public val shape: SpatialShape = SpatialRoundedCornerShape(CornerSize(32.dp))
}

/**
 * Creates a [SpatialPanel] representing a 2D plane in 3D space in which an application can fill
 * content.
 *
 * @param view Content view to be displayed within the SpatialPanel.
 * @param modifier SubspaceModifiers to apply to the SpatialPanel.
 * @param shape The shape of this Spatial Panel.
 */
@Composable
@SubspaceComposable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun SpatialPanel(
    view: View,
    modifier: SubspaceModifier = SubspaceModifier,
    shape: SpatialShape = SpatialPanelDefaults.shape,
) {
    SpatialPanel(modifier, view, shape) {}
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
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun SpatialPanel(
    modifier: SubspaceModifier = SubspaceModifier,
    shape: SpatialShape = SpatialPanelDefaults.shape,
    content: @Composable @UiComposable () -> Unit,
) {
    val composeView = rememberComposeView()

    SpatialPanel(
        modifier = modifier,
        view = composeView,
        shape = shape,
        onCorePanelEntityCreated = { corePanelEntity ->
            composeView.setContent {
                CompositionLocalProvider(
                    LocalOpaqueEntity provides corePanelEntity,
                    content = content
                )
            }
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
 * To disable Title Bars in the activity add the following to Activity#onCreate:
 * ```
 * requestWindowFeature(Window.FEATURE_NO_TITLE)
 * ```
 *
 * Or disable Title Bars in the theme by setting:
 * ```
 * <item name="windowNoTitle">true</item>
 * ```
 *
 * @param modifier SubspaceModifier to apply to the MainPanel.
 * @param shape The shape of this Spatial Panel.
 */
@Composable
@SubspaceComposable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun MainPanel(
    modifier: SubspaceModifier = SubspaceModifier,
    shape: SpatialShape = SpatialPanelDefaults.shape,
) {
    val mainPanel = rememberCoreMainPanelEntity(shape = shape)

    DisposableEffect(Unit) {
        mainPanel.hidden = false
        onDispose { mainPanel.hidden = true }
    }

    LayoutPanelEntity(mainPanel, modifier)
}

/**
 * Creates a [SpatialPanel] and launches an Activity within it.
 *
 * To disable Title Bars in the activity add the following to Activity#onCreate:
 * ```
 * requestWindowFeature(Window.FEATURE_NO_TITLE)
 * ```
 *
 * Or disable Title Bars in the theme by setting:
 * ```
 * <item name="windowNoTitle">true</item>
 * ```
 *
 * @param intent The intent of an Activity to launch within this panel.
 * @param modifier SubspaceModifiers to apply to the SpatialPanel.
 * @param shape The shape of this Spatial Panel.
 */
@Composable
@SubspaceComposable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun SpatialPanel(
    intent: Intent,
    modifier: SubspaceModifier = SubspaceModifier,
    shape: SpatialShape = SpatialPanelDefaults.shape,
) {

    val session = checkNotNull(LocalSession.current) { "session must be initialized" }
    val dialogManager = LocalDialogManager.current

    val minimumPanelDimension = Dimensions(10f, 10f, 10f)
    val rect = Rect(0, 0, DEFAULT_SIZE_PX, DEFAULT_SIZE_PX)
    val activityPanelEntity = rememberCorePanelEntity {
        ActivityPanelEntity.create(session, rect, entityName("ActivityPanel-${intent.action}"))
            .also { it.launchActivity(intent) }
    }

    SpatialBox {
        LayoutPanelEntity(activityPanelEntity, modifier)

        if (dialogManager.isSpatialDialogActive.value) {
            val scrimView = rememberComposeView()

            scrimView.setContent {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(UiColor.Black.copy(alpha = 0.5f))
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    dialogManager.isSpatialDialogActive.value = false
                                }
                            }
                ) {}
            }

            val scrimPanelEntity =
                rememberCorePanelEntity(shape = shape) {
                    PanelEntity.create(
                            session = session,
                            view = scrimView,
                            dimensions = minimumPanelDimension,
                            name = entityName("ScrimPanel"),
                            pose = Pose.Identity,
                        )
                        .also {
                            it.setParent(activityPanelEntity.entity)
                            it.setPose(Pose(translation = Vector3(0f, 0f, 3.millimeters.toM())))
                        }
                }

            LaunchedEffect(activityPanelEntity.size) {
                val size = activityPanelEntity.size
                scrimPanelEntity.size = size
            }
        }
    }
}

/**
 * Private [SpatialPanel] implementation that reports its created PanelEntity.
 *
 * @param modifier SubspaceModifiers.
 * @param view content view to render inside the SpatialPanel
 * @param shape The shape of this Spatial Panel.
 * @param onCorePanelEntityCreated callback to consume the [CorePanelEntity] when it is created
 */
@Composable
@SubspaceComposable
private fun SpatialPanel(
    modifier: SubspaceModifier,
    view: View,
    shape: SpatialShape,
    onCorePanelEntityCreated: (CorePanelEntity) -> Unit,
) {
    val minimumPanelDimension = Dimensions(10f, 10f, 10f)
    val frameLayout = remember {
        FrameLayout(view.context).also {
            if (view.parent != it) {
                val parent = view.parent as? ViewGroup
                parent?.removeView(view)
                it.addView(view)
            }
        }
    }
    val scrim = remember { View(view.context) }
    val dialogManager = LocalDialogManager.current
    val corePanelEntity =
        rememberCorePanelEntity(onCoreEntityCreated = onCorePanelEntityCreated, shape = shape) {
            PanelEntity.create(
                session = this,
                view = frameLayout,
                dimensions = minimumPanelDimension,
                name = entityName("SpatialPanel"),
                pose = Pose.Identity,
            )
        }

    LaunchedEffect(dialogManager.isSpatialDialogActive.value) {
        if (dialogManager.isSpatialDialogActive.value) {
            scrim.setBackgroundColor(Color.argb(90, 0, 0, 0))
            val scrimLayoutParams =
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )

            if (scrim.parent == null) {
                frameLayout.addView(scrim, scrimLayoutParams)
            }

            scrim.setOnClickListener { dialogManager.isSpatialDialogActive.value = false }
        } else {
            frameLayout.removeView(scrim)
        }
    }

    LayoutPanelEntity(corePanelEntity, modifier)
}

/**
 * Lay out the SpatialPanel using the provided [CoreBasePanelEntity].
 *
 * @param coreEntity The [CoreBasePanelEntity] associated with this SpatialPanel.
 * @param modifier The [SubspaceModifier] attached to this compose node.
 */
@Composable
private fun LayoutPanelEntity(coreEntity: CoreBasePanelEntity, modifier: SubspaceModifier) {
    SubspaceLayout(modifier = modifier, coreEntity = coreEntity) { _, constraints ->
        val width = DEFAULT_SIZE_PX.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = DEFAULT_SIZE_PX.coerceIn(constraints.minHeight, constraints.maxHeight)
        val depth = DEFAULT_SIZE_PX.coerceIn(constraints.minDepth, constraints.maxDepth)
        layout(width, height, depth) {}
    }
}
