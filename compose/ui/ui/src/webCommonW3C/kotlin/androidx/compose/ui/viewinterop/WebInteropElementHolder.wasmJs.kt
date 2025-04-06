/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.ui.viewinterop

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement

internal abstract class WebInteropElementHolder<T : HTMLElement>(
    factory: () -> T,
    interopContainer: InteropContainer,
    private val interopWrapper: HTMLElement,
    compositeKeyHash: Int
) : TypedInteropViewHolder<T>(
    factory = factory,
    interopContainer = interopContainer,
    group = interopWrapper,
    compositeKeyHash = compositeKeyHash,
    measurePolicy = MeasurePolicy { _, constraints ->
        layout(constraints.minWidth, constraints.minHeight) {
            // Пока ничего, так как HTML-элементы сами определяют размер
        }
    }
) {
    constructor(
        factory: () -> T,
        interopContainer: InteropContainer,
        compositeKeyHash: Int,
    ) : this(
        factory,
        interopContainer,
        interopWrapper = (document.createElement("div") as HTMLDivElement).apply { style.position = "absolute "} as HTMLElement,
        compositeKeyHash
    )

    private var currentRect: IntRect? = null

    private var isHidden: Boolean = false

    protected abstract var userComponentRect: String

    fun getCanvasCoordinates(): Pair<Double, Double> {
        val canvasElement = document.querySelector("canvas") as HTMLCanvasElement
        return canvasElement.getBoundingClientRect().let {
            it.left to it.top
        }
    }

    private fun Rect.round(density: Density): IntRect {
        val left = floor(left / density.density).toInt()
        val top = floor(top / density.density).toInt()
        val right = ceil(right / density.density).toInt()
        val bottom = ceil(bottom / density.density).toInt()

        return IntRect(left, top, right, bottom)
    }

    override fun layoutAccordingTo(layoutCoordinates: LayoutCoordinates) {
        val position = layoutCoordinates.positionInRoot()

        val rootCoordinates = layoutCoordinates.findRootCoordinates()

        val unclippedRect = rootCoordinates
            .localBoundingBoxOf(layoutCoordinates, clipBounds = false)
            .round(density)

        val clippedRect = rootCoordinates
            .localBoundingBoxOf(layoutCoordinates, clipBounds = true)
            .round(density)

        val scaledX = position.x / density.density
        val scaledY = position.y / density.density

        val (canvasX, canvasY) = getCanvasCoordinates()
        val adjustedX = canvasX + scaledX
        val adjustedY = canvasY + scaledY

        val newRect = IntRect(
            adjustedX.toInt(),
            adjustedY.toInt(),
            clippedRect.width,
            clippedRect.height
        )

        if (currentRect != newRect) {
            container.scheduleUpdate {
                interopWrapper.style.apply {
                    left = "${adjustedX}px"
                    top = "${adjustedY}px"
                    width = "${unclippedRect.width}px"
                    height = "${unclippedRect.height}px"
                }
            }
            currentRect = newRect
        }


        updateClipPath(clippedRect, unclippedRect)
        currentRect = newRect
    }

    override fun changeInteropViewIndex(root: InteropViewGroup, index: Int) {
        val referenceNode = root.children.item(index)

        root.insertBefore(group, referenceNode)
    }

    private fun updateClipPath(clippedRect: IntRect, unclippedRect: IntRect) {
        if (interopWrapper.offsetWidth <= 0 || interopWrapper.offsetHeight <= 0) return

        val topClip = maxOf(clippedRect.top - unclippedRect.top, 0)
        val leftClip = maxOf(clippedRect.left - unclippedRect.left, 0)
        val bottomClip = maxOf(unclippedRect.bottom - clippedRect.bottom, 0)
        val rightClip = maxOf(unclippedRect.right - clippedRect.right, 0)

        val newHiddenState = topClip >= interopWrapper.offsetHeight.toFloat() || leftClip >= interopWrapper.offsetWidth.toFloat()

        if (newHiddenState != isHidden) {
            interopWrapper.style.visibility = if (newHiddenState) "hidden" else "visible"
            isHidden = newHiddenState
        }

        interopWrapper.style.setProperty("clip-path", "inset(${topClip}px ${rightClip}px ${bottomClip}px ${leftClip}px)")
    }
}