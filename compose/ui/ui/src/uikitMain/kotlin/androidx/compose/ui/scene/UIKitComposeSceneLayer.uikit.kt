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

package androidx.compose.ui.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformWindowContext
import androidx.compose.ui.uikit.ComposeUIViewControllerConfiguration
import androidx.compose.ui.uikit.density
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.asDpOffset
import androidx.compose.ui.unit.asDpSize
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.window.FocusStack
import androidx.compose.ui.window.GestureEvent
import androidx.compose.ui.window.MetalView
import kotlin.coroutines.CoroutineContext
import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPoint

internal class UIKitComposeSceneLayer(
    private val onClosed: (UIKitComposeSceneLayer) -> Unit,
    private val createComposeSceneContext: (PlatformContext) -> ComposeSceneContext,
    private val providingCompositionLocals: @Composable (@Composable () -> Unit) -> Unit,
    metalView: MetalView,
    onGestureEvent: (GestureEvent) -> Unit,
    private val initDensity: Density,
    private val initLayoutDirection: LayoutDirection,
    configuration: ComposeUIViewControllerConfiguration,
    focusStack: FocusStack?,
    windowContext: PlatformWindowContext,
    compositionContext: CompositionContext,
) : ComposeSceneLayer {

    override var focusable: Boolean = focusStack != null

    val view = UIKitComposeSceneLayerView(
        ::isInsideInteractionBounds,
        isInterceptingOutsideEvents = { focusable }
    )

    private val mediator = ComposeSceneMediator(
        view,
        configuration,
        focusStack,
        windowContext,
        coroutineContext = compositionContext.effectCoroutineContext,
        metalView.redrawer,
        onGestureEvent = onGestureEvent,
        composeSceneFactory = ::createComposeScene
    )

    private fun isInsideInteractionBounds(point: CValue<CGPoint>): Boolean =
        boundsInWindow.contains(point.asDpOffset().toOffset(view.density).round())
    
    private fun createComposeScene(
        invalidate: () -> Unit,
        platformContext: PlatformContext,
        coroutineContext: CoroutineContext,
    ): ComposeScene =
        PlatformLayersComposeScene(
            density = initDensity, // We should use the local density already set for the current layer.
            layoutDirection = initLayoutDirection,
            coroutineContext = coroutineContext,
            composeSceneContext = createComposeSceneContext(platformContext),
            invalidate = invalidate,
        )

    val hasInvalidations by mediator::hasInvalidations

    override var density by mediator::density

    override var layoutDirection by mediator::layoutDirection

    override var boundsInWindow by mediator::interactionBounds

    override var compositionLocalContext by mediator::compositionLocalContext

    override var scrimColor: Color? = null
        set(value) {
            if (field != value) {
                field = value
                value?.let {
                    scrimPaint.color = value
                }
            }
        }

    private val scrimPaint = Paint()

    fun render(canvas: Canvas, nanoTime: Long) {
        if (scrimColor != null) {
            val size = view.bounds.useContents { with(density) { size.asDpSize().toSize() } }

            canvas.drawRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                paint = scrimPaint
            )
        }

        mediator.render(canvas, nanoTime)
    }

    fun retrieveInteropTransaction() = mediator.retrieveInteropTransaction()

    override fun close() {
        onClosed(this)

        dispose()
    }

    internal fun dispose() {
        mediator.dispose()
        view.removeFromSuperview()
    }

    override fun setContent(content: @Composable () -> Unit) {
        mediator.setContent {
            providingCompositionLocals {
                content()
            }
        }
    }

    override fun setKeyEventListener(
        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
        onKeyEvent: ((KeyEvent) -> Boolean)?
    ) {
        mediator.setKeyEventListener(onPreviewKeyEvent, onKeyEvent)
    }

    override fun setOutsidePointerEventListener(
        onOutsidePointerEvent: ((eventType: PointerEventType, button: PointerButton?) -> Unit)?
    ) {
        view.onOutsidePointerEvent = {
            onOutsidePointerEvent?.invoke(it, null)
        }
    }

    /**
     * Since layer is assumed to be the same size as the window it is attached to, just return the same position.
     */
    override fun calculateLocalPosition(positionInWindow: IntOffset): IntOffset = positionInWindow

    fun sceneDidAppear() {
        mediator.sceneDidAppear()
    }

    fun sceneWillDisappear() {
        mediator.sceneWillDisappear()
    }
}