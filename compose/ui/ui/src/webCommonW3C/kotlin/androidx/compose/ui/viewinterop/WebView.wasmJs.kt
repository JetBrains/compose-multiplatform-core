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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntRect
import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement

@Suppress("ACTUAL_WITHOUT_EXPECT") // https://youtrack.jetbrains.com/issue/KT-37316
internal actual typealias InteropViewGroup = HTMLElement

@Composable
fun <T : HTMLElement> WebElementView(
    factory: () -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = NoOp,
    onRelease: (T) -> Unit = NoOp,
    onReset: ((T) -> Unit)? = null,
) {
    val interopContainer = LocalInteropContainer.current
    val properties: WebInteropProperties = WebInteropProperties();

    InteropView(
        factory = { compositeKeyHash ->
            WebInteropViewHolder(
                factory,
                interopContainer,
                properties,
                compositeKeyHash
            )
        },
        modifier,
        onReset,
        onRelease,
        update = {
            update(it)
            val holder = interopContainer.holderOfView(it) as? WebInteropViewHolder<*>
            holder?.properties = properties
        }
    )
}

internal class WebInteropContainer(
    override val root: InteropViewGroup = document.body as HTMLElement,
) : InteropContainer {
    override var rootModifier: TrackInteropPlacementModifierNode? = null
    private var interopViews = mutableMapOf<InteropView, InteropViewHolder>()

    override val snapshotObserver = SnapshotStateObserver { command ->
        command()
    }

    override fun contains(holder: InteropViewHolder): Boolean =
        interopViews.contains(holder.getInteropView())

    override fun holderOfView(view: InteropView): InteropViewHolder? =
        interopViews[view]

    override fun place(holder: InteropViewHolder) {
        val interopView = checkNotNull(holder.getInteropView())

        if (interopViews.isEmpty()) {
            snapshotObserver.start()
        }

        val isAdded = interopViews.put(interopView, holder) == null

        val countBelow = countInteropComponentsBelow(holder)
        println(interopView)
        println(interopViews)

        if (isAdded) {
            scheduleUpdate {
                holder.insertInteropView(root = root, index = countBelow)
            }
        } else {
            scheduleUpdate {
                holder.changeInteropViewIndex(root = root, index = countBelow)
            }
        }
    }

    override fun unplace(holder: InteropViewHolder) {
        val interopView = requireNotNull(holder.getInteropView())

        interopViews.remove(interopView)

        if (interopViews.isEmpty()) {
            snapshotObserver.stop()
        }

        scheduleUpdate {
            holder.removeInteropView(root = root)
        }
    }

    override fun scheduleUpdate(action: () -> Unit) {
        action()
    }

    private fun countInteropComponentsBelow(holder: InteropViewHolder): Int {
        val interopView = checkNotNull(holder.getInteropView())
        return interopViews.keys.indexOf(interopView).coerceAtLeast(0)
    }
}

internal class WebInteropViewHolder<T : HTMLElement>(
    factory: () -> T,
    interopContainer: InteropContainer,
    properties: WebInteropProperties,
    compositeKeyHash: Int,
) : WebInteropElementHolder<T>(
    factory,
    interopContainer,
    properties,
    compositeKeyHash
) {
    init {
        group.appendChild(typedInteropView)
    }

    override var userComponentRect: String
        get() = typedInteropView.style.cssText
        set(value) {
            typedInteropView.style.cssText = value
        }

    override fun insertInteropView(root: InteropViewGroup, index: Int) {
        val referenceNode = root.children.item(index)
        if (referenceNode != null) {
            root.insertBefore(group, referenceNode)
        } else {
            root.appendChild(group)
        }
        super.insertInteropView(root, index)
    }


    override fun removeInteropView(root: InteropViewGroup) {
        root.removeChild(group)
        super.removeInteropView(root)
    }
}

internal abstract class WebInteropElementHolder<T : HTMLElement>(
    factory: () -> T,
    interopContainer: InteropContainer,
    private val interopWrapper: HTMLElement,
    properties: WebInteropProperties,
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
        properties: WebInteropProperties,
        compositeKeyHash: Int,
    ) : this(
        factory,
        interopContainer,
        interopWrapper = (document.createElement("div") as HTMLDivElement).apply { style.position = "absolute "} as HTMLElement,
        properties,
        compositeKeyHash
    )

    private var currentRect: IntRect? = null

    private var isHidden: Boolean = false

    var properties = properties
        set(value) {
            if (field != value) {
                field = value
                onPropertiesChanged()
            }
        }

    protected abstract var userComponentRect: String

    fun getCanvasCoordinates(): Pair<Double, Double> {
        val canvasElement = document.querySelector("canvas") as HTMLCanvasElement
        return canvasElement.getBoundingClientRect().let {
            it.left to it.top
        }
    }

    override fun layoutAccordingTo(layoutCoordinates: LayoutCoordinates) {
        val bounds = layoutCoordinates.boundsInRoot()
        val position = layoutCoordinates.positionInRoot()

        val scaledX = position.x / density.density
        val scaledY = position.y / density.density

        val (canvasX, canvasY) = getCanvasCoordinates()
        val adjustedX = canvasX + scaledX
        val adjustedY = canvasY + scaledY

        val newRect = IntRect(
            adjustedX.toInt(),
            adjustedY.toInt(),
            bounds.width.toInt() / density.density.toInt(),
            bounds.height.toInt() / density.density.toInt()
        )

        if (currentRect != newRect) {
            container.scheduleUpdate {
                interopWrapper.style.apply {
                    left = "${newRect.left}px"
                    top = "${newRect.top}px"
                    width = "${newRect.width}px"
                    height = "${newRect.height}px"
                }
            }
            currentRect = newRect
        }


        updateClipPath(bounds, position)
        currentRect = newRect
    }

    override fun changeInteropViewIndex(root: InteropViewGroup, index: Int) {
        val referenceNode = root.children.item(index)

        root.insertBefore(group, referenceNode)
    }

    private fun updateClipPath(bounds: Rect, position: Offset) {
        if (interopWrapper.offsetWidth <= 0 || interopWrapper.offsetHeight <= 0) return

        val topClip = maxOf(bounds.top - position.y, 0f)
        val leftClip = maxOf(bounds.left - position.x, 0f)
        val bottomClip = maxOf(position.y + interopWrapper.offsetHeight * 2 - bounds.bottom, 0f)
        val rightClip = maxOf(position.x + interopWrapper.offsetWidth * 2 - bounds.right, 0f)

        val newHiddenState = topClip == interopWrapper.offsetHeight.toFloat() || leftClip == interopWrapper.offsetWidth.toFloat()

        if (newHiddenState != isHidden) {
            interopWrapper.style.visibility = if (newHiddenState) "hidden" else "visible"
            isHidden = newHiddenState
        }

        interopWrapper.style.setProperty("clip-path", "inset(${topClip}px ${rightClip}px ${bottomClip}px ${leftClip}px)")
    }

    private fun onPropertiesChanged() {
        interopWrapper.style.setProperty("pointer-events", if (properties.isInteractive) "auto" else "none")

        interopWrapper.style.apply {
            visibility = if (properties.isVisible) "visible" else "hidden"
        }
    }
}

data class WebInteropProperties(
    val isInteractive: Boolean = true,
    val isVisible: Boolean = true
) {
    companion object {
        val Default = WebInteropProperties()
    }
}