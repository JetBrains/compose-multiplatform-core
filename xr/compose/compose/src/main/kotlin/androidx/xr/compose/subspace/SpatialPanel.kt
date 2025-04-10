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
import android.view.View.MeasureSpec
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.xr.compose.platform.LocalDialogManager
import androidx.xr.compose.platform.LocalOpaqueEntity
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.getActivity
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
        rememberCorePanelEntity(shape = shape) {
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

    SubspaceLayout(modifier = modifier, coreEntity = corePanelEntity) { _, constraints ->
        view.measure(
            MeasureSpec.makeMeasureSpec(constraints.maxWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(constraints.maxHeight, MeasureSpec.AT_MOST),
        )
        val width = view.measuredWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = view.measuredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        val depth = constraints.minDepth.coerceAtLeast(0)
        layout(width, height, depth) {}
    }
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
    val view = rememberComposeView()
    val minimumPanelDimension = Dimensions(10f, 10f, 10f)
    val dialogManager = LocalDialogManager.current
    val corePanelEntity =
        rememberCorePanelEntity(shape = shape) {
            PanelEntity.create(
                session = this,
                view = view,
                dimensions = minimumPanelDimension,
                name = entityName("SpatialPanel"),
                pose = Pose.Identity,
            )
        }
    var intrinsicWidth by remember { mutableIntStateOf(DEFAULT_SIZE_PX) }
    var intrinsicHeight by remember { mutableIntStateOf(DEFAULT_SIZE_PX) }

    SubspaceLayout(modifier = modifier, coreEntity = corePanelEntity) { _, volumeConstraints ->
        view.setContent {
            CompositionLocalProvider(LocalOpaqueEntity provides corePanelEntity) {
                Layout(content = content, modifier = Modifier) { measurables, constraints ->
                    intrinsicWidth =
                        measurables.fastMaxOfOrNull {
                            try {
                                it.maxIntrinsicWidth(volumeConstraints.maxHeight)
                            } catch (e: IllegalStateException) {
                                0
                            }
                        } ?: DEFAULT_SIZE_PX
                    intrinsicHeight =
                        measurables.fastMaxOfOrNull {
                            try {
                                it.maxIntrinsicHeight(volumeConstraints.maxWidth)
                            } catch (e: IllegalStateException) {
                                0
                            }
                        } ?: DEFAULT_SIZE_PX
                    val placeables = measurables.map { it.measure(constraints) }
                    layout(
                        placeables.fastMaxOfOrNull { it.measuredWidth } ?: DEFAULT_SIZE_PX,
                        placeables.fastMaxOfOrNull { it.measuredHeight } ?: DEFAULT_SIZE_PX,
                    ) {
                        placeables.fastForEach { placeable -> placeable.place(0, 0) }
                    }
                }
            }

            if (dialogManager.isSpatialDialogActive.value) {
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
        }
        val width = intrinsicWidth.coerceIn(volumeConstraints.minWidth, volumeConstraints.maxWidth)
        val height =
            intrinsicHeight.coerceIn(volumeConstraints.minHeight, volumeConstraints.maxHeight)
        val depth = volumeConstraints.minDepth.coerceAtLeast(0)
        layout(width, height, depth) {}
    }
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
    val view = LocalContext.current.getActivity().window?.decorView ?: LocalView.current

    DisposableEffect(Unit) {
        mainPanel.hidden = false
        onDispose { mainPanel.hidden = true }
    }

    SubspaceLayout(modifier = modifier, coreEntity = mainPanel) { _, constraints ->
        val width = view.measuredWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = view.measuredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        val depth = constraints.minDepth.coerceAtLeast(0)
        layout(width, height, depth) {}
    }
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
        SubspaceLayout(modifier = modifier, coreEntity = activityPanelEntity) { _, constraints ->
            val width = DEFAULT_SIZE_PX.coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = DEFAULT_SIZE_PX.coerceIn(constraints.minHeight, constraints.maxHeight)
            val depth = constraints.minDepth.coerceAtLeast(0)
            layout(width, height, depth) {}
        }

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
