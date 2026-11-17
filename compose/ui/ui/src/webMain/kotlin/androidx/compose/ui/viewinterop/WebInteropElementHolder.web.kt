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

import androidx.compose.runtime.CompositeKeyHashCode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement

internal abstract class WebInteropElementHolder<T : HTMLElement>(
    factory: () -> T,
    interopContainer: InteropContainer,
    private val interopWrapper: HTMLElement,
    compositeKeyHashCode: CompositeKeyHashCode,
) : TypedInteropViewHolder<T>(
    factory = factory,
    interopContainer = interopContainer,
    group = InteropViewGroup(interopWrapper),
    compositeKeyHashCode = compositeKeyHashCode,
) {
    constructor(
        factory: () -> T,
        interopContainer: InteropContainer,
        compositeKeyHashCode: CompositeKeyHashCode,
    ) : this(
        factory = factory,
        interopContainer = interopContainer,
        interopWrapper =
            (document.createElement("div") as HTMLDivElement)
                .apply {
                    style.position = "absolute"
                    // hide it until it's properly positioned,
                    // otherwise it can briefly flash at 0,0
                    toggleVisibility(this, isHidden = true)
                },
        compositeKeyHashCode = compositeKeyHashCode
    )

    private var isPositioned = false

    private var isHidden: Boolean = false

    private var lastPosition = Offset.Zero
    private var lastSize = IntSize.Zero

    protected abstract var userComponentRect: String

    override val measurePolicy: MeasurePolicy = MeasurePolicy { _, constraints ->
        layout(constraints.minWidth, constraints.minHeight) {
            // No-op, no children are expected
        }
    }


    override fun layoutAccordingTo(layoutCoordinates: LayoutCoordinates) {
        val oldPosition = lastPosition
        val oldSize = lastSize
        val newPosition = layoutCoordinates.positionInWindow()
        lastPosition = newPosition

        val rootCoordinates = layoutCoordinates.findRootCoordinates()
        val densityFactor = density.density

        val unclipped = rootCoordinates.localBoundingBoxOf(layoutCoordinates, clipBounds = false)
        val unclippedLeft = floor(unclipped.left / densityFactor).toInt()
        val unclippedTop = floor(unclipped.top / densityFactor).toInt()
        val unclippedRight = ceil(unclipped.right / densityFactor).toInt()
        val unclippedBottom = ceil(unclipped.bottom / densityFactor).toInt()
        lastSize = IntSize(unclippedRight - unclippedLeft, unclippedBottom - unclippedTop)

        val clipped = rootCoordinates.localBoundingBoxOf(layoutCoordinates, clipBounds = true)
        val clippedLeft = floor(clipped.left / densityFactor).toInt()
        val clippedTop = floor(clipped.top / densityFactor).toInt()
        val clippedRight = ceil(clipped.right / densityFactor).toInt()
        val clippedBottom = ceil(clipped.bottom / densityFactor).toInt()

        //Force change if it's the first time we're positioning
        val positionChanged = oldPosition != newPosition || !isPositioned
        val unclippedRectChanged = lastSize != oldSize || !isPositioned
        val changeFlags = packBooleans(positionChanged, unclippedRectChanged)
        val returnFlags = packBooleans(isHidden, isPositioned)

        // update the css properties only for visible interop views
        if (clippedLeft < clippedRight && clippedTop < clippedBottom) {
            val returned = updateCssElementProperties(
                wrapperElement = interopWrapper,
                returnFlags = returnFlags,
                density = densityFactor,
                changeFlags = changeFlags,
                newPositionX = newPosition.x,
                newPositionY = newPosition.y,
                clippedRectTop = clippedTop,
                clippedRectLeft = clippedLeft,
                clippedRectBottom = clippedBottom,
                clippedRectRight = clippedRight,
                unclippedRectTop = unclippedTop,
                unclippedRectLeft = unclippedLeft,
                unclippedRectBottom = unclippedBottom,
                unclippedRectRight = unclippedRight
            )

            this.isHidden = returned and 1 != 0
            this.isPositioned = returned and 2 != 0
        } else if (!isHidden) {
            toggleVisibility(interopWrapper, isHidden = true)
            isHidden = true
        }
    }

    override fun changeInteropViewIndex(root: InteropViewGroup, index: Int) =
        changeInteropViewIndex(root.htmlElement, group.htmlElement, index)
}

@Suppress("NOTHING_TO_INLINE")
private inline fun packBooleans(boolean1: Boolean, boolean2: Boolean): Int =
    (if (boolean1) 1 else 0) or (if (boolean2) 2 else 0)


private fun toggleVisibility(element: HTMLElement, isHidden: Boolean) {
    // language=javascript
    js("""
       element.style.visibility = isHidden ? "hidden" : "visible";
    """)
}

private fun updateCssElementProperties(
    wrapperElement: HTMLElement,
    returnFlags: Int,
    density: Float,
    changeFlags: Int,
    newPositionX: Float,
    newPositionY: Float,
    clippedRectTop: Int,
    clippedRectLeft: Int,
    clippedRectBottom: Int,
    clippedRectRight: Int,
    unclippedRectTop: Int,
    unclippedRectLeft: Int,
    unclippedRectBottom: Int,
    unclippedRectRight: Int
): Int = js(
        //language=javascript
    """
        {
        const positionChanged = (changeFlags & 1) !== 0;
        const unclippedRectChanged = (changeFlags & 2) !== 0;
        const oldHidden = (returnFlags & 1) !== 0;
        let hidden = oldHidden;
        let position = (returnFlags & 2) !== 0;
        if (positionChanged) {
            const left = newPositionX / density;
            const top = newPositionY / density;
            wrapperElement.style.transform = "matrix(1, 0, 0, 1, " + left + ", " + top + ")";
        }
        if (unclippedRectChanged) {
            wrapperElement.style.width = "" + (unclippedRectRight - unclippedRectLeft) + "px";
            wrapperElement.style.height = "" + (unclippedRectBottom - unclippedRectTop) + "px";
        }
        const intOffWidth = wrapperElement.offsetWidth;
        const intOfftHeight = wrapperElement.offsetHeight;
        if (intOffWidth <= 0 || intOfftHeight <= 0) {
        } else {
            const topClp = Math.max(clippedRectTop - unclippedRectTop, 0);
            const leftClp = Math.max(clippedRectLeft - unclippedRectLeft, 0);
            const bottomClp = Math.max(unclippedRectBottom - clippedRectBottom, 0);
            const rightClp = Math.max(unclippedRectRight - clippedRectRight, 0);
            hidden = topClp >= intOfftHeight || leftClp >= intOffWidth;
            if (oldHidden !== hidden) {
                wrapperElement.style.visibility = hidden ? "hidden" : "visible";
            }
            wrapperElement.style.setProperty("clip-path", "inset(" + topClp + "px " + rightClp + "px " + bottomClp + "px " + leftClp + "px)");
        }
        if (!position) {
            position = true;
            wrapperElement.style.visibility = "visible";
        }
        return (hidden ? 1 : 0) | (position ? 2 : 0);
        }
    """
    )


private fun changeInteropViewIndex(
    rootElement: HTMLElement,
    groupElement: HTMLElement,
    index: Int
) {
    js(
        //language=javascript
        """
         const ref = rootElement.children.item(index);
         if (ref === groupElement) return;
         rootElement.insertBefore(groupElement, ref);
        """
    )
}