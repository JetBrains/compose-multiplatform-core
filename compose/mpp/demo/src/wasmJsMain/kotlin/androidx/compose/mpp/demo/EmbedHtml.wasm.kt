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
    element.style.setProperty("pointer-events", "none")
    element.style.apply {
        color = "white"
    }
    element.configure()
    document.body?.appendChild(element)

    return element
}

private class ComponentInfo {
    var component: HTMLElement? = null;
    var isHidden: Boolean = false;
}

@Composable
fun Modifier.addHtmlElementWithCompose(
    tagName: String,
    id: String,
    configure: HTMLElement.() -> Unit
): Modifier {
    val componentInfo = remember { ComponentInfo() }
    val canvasCoordinates = getCanvasCoordinates()
    val density = LocalDensity.current.density

    DisposableEffect(id, tagName) {
        val element = createHtmlElement(tagName, id, configure)
        componentInfo.component = element

        onDispose {
            componentInfo.component = null
            element.remove()
        }
    }

    return this.then(Modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        val position = coordinates.positionInRoot()
        val existingElement = componentInfo.component ?: return@onGloballyPositioned

        val scaledX = position.x / density
        val scaledY = position.y / density

        val (canvasX, canvasY) = canvasCoordinates
        val adjustedX = canvasX + scaledX
        val adjustedY = canvasY + scaledY

        if (!componentInfo.isHidden) {
            existingElement.style.apply {
                left = "${adjustedX}px"
                top = "${adjustedY}px"
            }
        }

        if (existingElement.offsetWidth > 0 && existingElement.offsetHeight > 0) {
            val topClip = maxOf((bounds.top.toDouble() - position.y) / density, 0.0)
            val leftClip = maxOf((bounds.left.toDouble() - position.x) / density, 0.0)
            val bottomClip = maxOf((position.y + existingElement.offsetHeight * 2 - bounds.bottom.toDouble()) / density, 0.0)
            val rightClip = maxOf((position.x + existingElement.offsetWidth * 2 - bounds.right.toDouble() ) / density, 0.0)

            val newHiddenState = topClip == existingElement.offsetHeight.toDouble() ||
                leftClip == existingElement.offsetWidth.toDouble()

            if (newHiddenState != componentInfo.isHidden) {
                existingElement.style.visibility = if (newHiddenState) "hidden" else "visible"
                componentInfo.isHidden = newHiddenState
            }

            existingElement.style.setProperty("clip-path", "inset(${topClip}px ${rightClip}px ${bottomClip}px ${leftClip}px)")
        }
    })
}