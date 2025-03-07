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

package androidx.compose.mpp.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

fun getCanvasCoordinates(): Pair<Double, Double> {
    val canvasElement = document.querySelector("canvas") as HTMLCanvasElement
    return canvasElement.getBoundingClientRect().let {
        it.left to it.top
    }
}

fun createHtmlElement(
    tagName: String,
    id: String,
    configure: HTMLElement.() -> Unit
): HTMLElement {
    val element = document.createElement(tagName) as HTMLElement

    element.id = id
    element.style.position = "absolute"
    element.configure()
    document.body?.appendChild(element)

    return element
}

private class ComponentInfo {
    lateinit var component: HTMLElement;
    var isHidden: Boolean = false;

    fun updateVisibility(visible: Boolean) {
        if (::component.isInitialized && visible != isHidden) {
            component.style.visibility = if (visible) "visible" else "hidden"
            isHidden = visible
        }
    }

    fun updateClipPath(bounds: androidx.compose.ui.geometry.Rect, position: androidx.compose.ui.geometry.Offset, density: Float) {
        if (!::component.isInitialized || component.offsetWidth <= 0 || component.offsetHeight <= 0) return

        val topClip = maxOf((bounds.top.toDouble() - position.y) / density, 0.0)
        val leftClip = maxOf((bounds.left.toDouble() - position.x) / density, 0.0)
        val bottomClip = maxOf((position.y + component.offsetHeight * 2 - bounds.bottom.toDouble()) / density, 0.0)
        val rightClip = maxOf((position.x + component.offsetWidth * 2 - bounds.right.toDouble() ) / density, 0.0)

        val newHiddenState = topClip == component.offsetHeight.toDouble() ||
            leftClip == component.offsetWidth.toDouble()

        updateVisibility(newHiddenState)

        component.style.setProperty("clip-path", "inset(${topClip}px ${rightClip}px ${bottomClip}px ${leftClip}px)")
    }

    fun updatePosition(x: Double, y: Double) {
        if (::component.isInitialized && !isHidden) {
            component.style.apply {
                left = "${x}px"
                top = "${y}px"
            }
        }
    }

    fun dispose() {
        if (::component.isInitialized) {
            component.remove()
        }
    }
}

@Composable
fun Modifier.LayoutModifier(
    tagName: String,
    id: String,
    configure: HTMLElement.() -> Unit,
    onClick: ((Event) -> Unit)? = null
): Modifier {
    val componentInfo = remember { ComponentInfo() }
    val canvasCoordinates = getCanvasCoordinates()
    val density = LocalDensity.current.density

    DisposableEffect(id, tagName, configure) {
        val element = createHtmlElement(tagName, id, configure)
        onClick?.let { handler ->
            element.addEventListener("click") { event -> {
                event.preventDefault();
                handler(event)
            } }
        }
        componentInfo.component = element

        onDispose {
            componentInfo.dispose()
        }
    }

    return this.then(Modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        val position = coordinates.positionInRoot()

        val scaledX = position.x / density
        val scaledY = position.y / density

        val (canvasX, canvasY) = canvasCoordinates
        val adjustedX = canvasX + scaledX
        val adjustedY = canvasY + scaledY

        componentInfo.updatePosition(adjustedX, adjustedY)
        componentInfo.updateClipPath(bounds, position, density)
    })
}

@Composable
fun HtmlElement(
    tagName: String,
    id: String,
    modifier: Modifier = Modifier,
    configure: HTMLElement.() -> Unit = {},
    content: @Composable () -> Unit = {},
    onClick: ((Event) -> Unit)? = null
) {
    Box(modifier = modifier.LayoutModifier(tagName, id, configure, onClick)) {
        content()
    }
}
