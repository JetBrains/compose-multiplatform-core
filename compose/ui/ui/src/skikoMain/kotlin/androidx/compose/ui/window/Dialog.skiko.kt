/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.animation.easeOutTimingFunction
import androidx.compose.ui.animation.withAnimationProgress
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.exclude
import androidx.compose.ui.platform.union
import androidx.compose.ui.scene.ComposeSceneLayer
import androidx.compose.ui.scene.Content
import androidx.compose.ui.scene.rememberComposeSceneLayer
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.center
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The default scrim opacity.
 */
private const val DefaultScrimOpacity = 0.78f
private val DefaultScrimColor = Color.Black.copy(alpha = DefaultScrimOpacity)
private const val AnimatedLayerAppearanceOffsetDp = 16f
private const val AnimatedLayerDisappearanceOffsetDp = 8f
private const val AnimatedLayerInitialAlphaProgress = 0.6f

/**
 * Represents an animation scope for dialogs, allowing customization of dialog animations
 * and associated visual properties.
 *
 * This interface provides methods to apply transformations and modify visual properties
 * during dialog animations.
 *
 * Note: This API is experimental and may change in the future.
 */
@ExperimentalComposeUiApi
@Immutable
interface DialogAnimationScope {
    /**
     * Applies graphics layer transformations and customizations within the specified [GraphicsLayerScope].
     * This method can be used to modify visual properties like rotation, scale, translation,
     * shadow, and other effects.
     *
     * @param modify A lambda receiver of [GraphicsLayerScope] allowing customization of graphics layer properties.
     */
    fun graphicsLayer(modify: GraphicsLayerScope.() -> Unit)

    /**
     * Defines the color of the scrim used during dialog animations.
     *
     * The scrim is a semi-transparent layer displayed behind the dialog to
     * focus the user's attention on the foreground content. This property
     * allows customization of the scrim's appearance to match desired visual
     * aesthetics or themes.
     */
    var scrimColor: Color
}

/**
 * Properties used to customize the behavior of a [Dialog].
 *
 * @property dismissOnBackPress whether the popup can be dismissed by pressing the back button
 *  * on Android or escape key on desktop.
 * If true, pressing the back button will call onDismissRequest.
 * @property dismissOnClickOutside whether the dialog can be dismissed by clicking outside the
 * dialog's bounds. If true, clicking outside the dialog will call onDismissRequest.
 * @property usePlatformDefaultWidth Whether the width of the dialog's content should be limited to
 * the platform default, which is smaller than the screen width.
 * @property usePlatformInsets Whether the size of the dialog's content should be limited by
 * platform insets.
 * @property useSoftwareKeyboardInset Whether the size of the dialog's content should be limited by
 * software keyboard inset.
 * @property scrimColor Color of background fill.
 * @property onAppearEffect The effect to be applied when the dialog appears.
 * @property onDisappearEffect The effect to be applied when the dialog disappears.
 */
@Immutable
actual class DialogProperties @ExperimentalComposeUiApi constructor(
    actual val dismissOnBackPress: Boolean = true,
    actual val dismissOnClickOutside: Boolean = true,
    actual val usePlatformDefaultWidth: Boolean = true,
    val usePlatformInsets: Boolean = true,
    val useSoftwareKeyboardInset: Boolean = true,
    val scrimColor: Color = DefaultScrimColor,
    @property:ExperimentalComposeUiApi
    val onAppearEffect: suspend DialogAnimationScope.() -> Unit =
        DialogAnimationScope::defaultDialogAppearEffect,
    @property:ExperimentalComposeUiApi
    val onDisappearEffect: suspend DialogAnimationScope.() -> Unit =
        DialogAnimationScope::defaultDialogDisappearEffect,
) {
    actual constructor(
        dismissOnBackPress: Boolean,
        dismissOnClickOutside: Boolean,
        usePlatformDefaultWidth: Boolean,
    ) : this(
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        usePlatformDefaultWidth = usePlatformDefaultWidth,
        usePlatformInsets = true,
        useSoftwareKeyboardInset = true,
        scrimColor = DefaultScrimColor,
    )

    @ExperimentalComposeUiApi
    constructor(
        dismissOnBackPress: Boolean = true,
        dismissOnClickOutside: Boolean = true,
        usePlatformDefaultWidth: Boolean = true,
        usePlatformInsets: Boolean = true,
        useSoftwareKeyboardInset: Boolean = true,
        scrimColor: Color = DefaultScrimColor,
    ) : this(
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        usePlatformDefaultWidth = usePlatformDefaultWidth,
        usePlatformInsets = usePlatformInsets,
        useSoftwareKeyboardInset = useSoftwareKeyboardInset,
        scrimColor = scrimColor,
        onAppearEffect = DialogAnimationScope::defaultDialogAppearEffect,
        onDisappearEffect = DialogAnimationScope::defaultDialogDisappearEffect,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DialogProperties) return false

        if (dismissOnBackPress != other.dismissOnBackPress) return false
        if (dismissOnClickOutside != other.dismissOnClickOutside) return false
        if (usePlatformDefaultWidth != other.usePlatformDefaultWidth) return false
        if (usePlatformInsets != other.usePlatformInsets) return false
        if (useSoftwareKeyboardInset != other.useSoftwareKeyboardInset) return false
        if (scrimColor != other.scrimColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dismissOnBackPress.hashCode()
        result = 31 * result + dismissOnClickOutside.hashCode()
        result = 31 * result + usePlatformDefaultWidth.hashCode()
        result = 31 * result + usePlatformInsets.hashCode()
        result = 31 * result + useSoftwareKeyboardInset.hashCode()
        result = 31 * result + scrimColor.hashCode()
        return result
    }
}

@Composable
actual fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties,
    content: @Composable () -> Unit
) {
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)

    val onKeyEvent = if (properties.dismissOnBackPress) {
        { event: KeyEvent ->
            if (event.isDismissRequest()) {
                currentOnDismissRequest()
                true
            } else {
                false
            }
        }
    } else {
        null
    }
    val onOutsidePointerEvent = if (properties.dismissOnClickOutside) {
        { eventType: PointerEventType, button: PointerButton? ->
            // Clicking outside dialog is clicking on scrim.
            // So this behavior should match regular clicks or [detectTapGestures] that accepts
            // only primary mouse button clicks.
            if (eventType == PointerEventType.Release &&
                (button == null || button == PointerButton.Primary)
            ) {
                currentOnDismissRequest()
            }
        }
    } else {
        null
    }
    DialogLayout(
        modifier = Modifier.semantics { dialog() },
        onKeyEvent = onKeyEvent,
        onOutsidePointerEvent = onOutsidePointerEvent,
        properties = properties,
        content = content
    )
}

@Composable
private fun DialogLayout(
    properties: DialogProperties,
    modifier: Modifier = Modifier,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    onOutsidePointerEvent: ((eventType: PointerEventType, button: PointerButton?) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val currentContent by rememberUpdatedState(content)
    val compositionContext = rememberCompositionContext()
    var graphicsLayerScopeUpdate by remember { mutableStateOf<GraphicsLayerScope.() -> Unit>({}) }
    val layer = rememberComposeSceneLayer(
        focusable = true
    )
    layer.setKeyEventListener(onPreviewKeyEvent, onKeyEvent)
    layer.setOutsidePointerEventListener(onOutsidePointerEvent)
    val dialogAnimationScope = remember {
        object : DialogAnimationScope {
            override fun graphicsLayer(modify: GraphicsLayerScope.() -> Unit) {
                graphicsLayerScopeUpdate = modify
            }

            override var scrimColor: Color
                get() = layer.scrimColor ?: properties.scrimColor
                set(value) { layer.scrimColor = value }
        }
    }
    layer.Content {
        LaunchedEffect(Unit) {
            properties.onAppearEffect(dialogAnimationScope)
            graphicsLayerScopeUpdate = {}
            layer.scrimColor = properties.scrimColor
        }
        val platformInsets = properties.platformInsets
        val containerSize = LocalWindowInfo.current.containerSize
        val measurePolicy = rememberDialogMeasurePolicy(
            layer = layer,
            properties = properties,
            containerSize = containerSize,
            platformInsets = platformInsets
        )

        LocalPlatformWindowInsets.current.exclude(
            safeInsets = properties.usePlatformInsets,
            ime = properties.useSoftwareKeyboardInset
        ) {
            Layout(
                content = currentContent,
                modifier = Modifier.graphicsLayer(graphicsLayerScopeUpdate).then(modifier),
                measurePolicy = measurePolicy
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            CoroutineScope(compositionContext.effectCoroutineContext).launch {
                properties.onDisappearEffect(dialogAnimationScope)
                layer.close()
            }
        }
    }
}

private suspend fun DialogAnimationScope.defaultDialogAppearEffect() {
    val initialScrimColor = this.scrimColor
    withAnimationProgress(0.15.seconds, timingFunction = ::easeOutTimingFunction) { progress ->
        val animatedAlpha =
            AnimatedLayerInitialAlphaProgress + progress * (1f - AnimatedLayerInitialAlphaProgress)
        this.scrimColor = initialScrimColor.copy(initialScrimColor.alpha * animatedAlpha)
        this.graphicsLayer {
            this.alpha = animatedAlpha
            this.translationY = (AnimatedLayerAppearanceOffsetDp * (1f - progress)) * density
        }
    }
}

private suspend fun DialogAnimationScope.defaultDialogDisappearEffect() {
    val initialScrimColor = this.scrimColor
    withAnimationProgress(0.10.seconds, timingFunction = ::easeOutTimingFunction) { progress ->
        val animatedAlpha = 1f - progress * AnimatedLayerInitialAlphaProgress
        this.scrimColor = initialScrimColor.copy(initialScrimColor.alpha * animatedAlpha)
        this.graphicsLayer {
            this.alpha = animatedAlpha
            this.translationY = -AnimatedLayerDisappearanceOffsetDp * progress * density
        }
    }
}

private val DialogProperties.platformInsets: PlatformInsets
    @Composable get() {
        val safeInsets = if (usePlatformInsets) {
            LocalPlatformWindowInsets.current.systemBars
        } else {
            PlatformInsets.Zero
        }

        val ime = if (useSoftwareKeyboardInset) {
            LocalPlatformWindowInsets.current.ime
        } else {
            PlatformInsets.Zero
        }

        return safeInsets.union(ime)
    }

@Composable
private fun rememberDialogMeasurePolicy(
    layer: ComposeSceneLayer,
    properties: DialogProperties,
    containerSize: IntSize,
    platformInsets: PlatformInsets
) = remember(layer, properties, containerSize, platformInsets) {
    RootMeasurePolicy(
        platformInsets = platformInsets,
        usePlatformDefaultWidth = properties.usePlatformDefaultWidth
    ) { contentSize ->
        val positionWithInsets = positionWithInsets(platformInsets, containerSize) { sizeWithoutInsets ->
            sizeWithoutInsets.center - contentSize.center
        }
        layer.boundsInWindow = IntRect(positionWithInsets, contentSize)
        layer.calculateLocalPosition(positionWithInsets)
    }
}

private fun KeyEvent.isDismissRequest() =
    type == KeyEventType.KeyDown && key == Key.Escape

internal fun getDialogScrimBlendMode(isWindowTransparent: Boolean) =
    if (isWindowTransparent) {
        // Use background alpha channel to respect transparent window shape.
        BlendMode.SrcAtop
    } else {
        BlendMode.SrcOver
    }
