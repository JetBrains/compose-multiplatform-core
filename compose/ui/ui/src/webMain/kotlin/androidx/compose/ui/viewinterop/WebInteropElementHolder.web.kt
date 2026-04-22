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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.js.js
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

    private fun Rect.round(density: Density): IntRect {
        val left = floor(left / density.density).toInt()
        val top = floor(top / density.density).toInt()
        val right = ceil(right / density.density).toInt()
        val bottom = ceil(bottom / density.density).toInt()

        return IntRect(left, top, right, bottom)
    }

    override fun layoutAccordingTo(layoutCoordinates: LayoutCoordinates) {
        val oldPosition = lastPosition
        val oldSize = lastSize
        val newPosition = layoutCoordinates.positionInWindow()
        lastPosition = newPosition

        val rootCoordinates = layoutCoordinates.findRootCoordinates()

        val unclippedRect = rootCoordinates
            .localBoundingBoxOf(layoutCoordinates, clipBounds = false)
            .round(density)
        lastSize = unclippedRect.size

        val clippedRect = rootCoordinates
            .localBoundingBoxOf(layoutCoordinates, clipBounds = true)
            .round(density)

        //Force change if it's the first time we're positioning
        val positionChanged = oldPosition != newPosition || !isPositioned
        val unclippedRectChanged = unclippedRect.size != oldSize || !isPositioned
        val changeFlags = packBooleans(positionChanged, unclippedRectChanged)
        val returnFlags = packBooleans(isHidden, isPositioned)

        // update the css properties only for visible interop views
        if (!clippedRect.isEmpty) {
            val returned = updateCssElementProperties(
                wrpEl = interopWrapper,
                retrnFlgs = returnFlags,
                dens = density.density,
                chngFlgs = changeFlags,
                nwPosX = newPosition.x,
                nwPosY = newPosition.y,
                cRectTop = clippedRect.top,
                cRectLeft = clippedRect.left,
                cRectBottom = clippedRect.bottom,
                cRectRight = clippedRect.right,
                ucRectTop = unclippedRect.top,
                ucRectLeft = unclippedRect.left,
                ucRectBottom = unclippedRect.bottom,
                ucRectRight = unclippedRect.right
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
    wrpEl: HTMLElement,
    retrnFlgs: Int,
    dens: Float,
    chngFlgs: Int,
    nwPosX: Float,
    nwPosY: Float,
    cRectTop: Int,
    cRectLeft: Int,
    cRectBottom: Int,
    cRectRight: Int,
    ucRectTop: Int,
    ucRectLeft: Int,
    ucRectBottom: Int,
    ucRectRight: Int
): Int {
   return js(
        //language=javascript
        """
        const posChng = (chngFlgs & 1) !== 0;
        const ucRectChng = (chngFlgs & 2) !== 0;
        const oldHid = (retrnFlgs & 1) !== 0;
        let hid = oldHid;
        let pos = (retrnFlgs & 2) !== 0;
        if (posChng) {
            const left = nwPosX / dens;
            const top = nwPosY / dens;
            wrpEl.style.transform = "matrix(1, 0, 0, 1, " + left + ", " + top + ")";
        }
        if (ucRectChng) {
            wrpEl.style.width = "" + (ucRectRight - ucRectLeft) + "px";
            wrpEl.style.height = "" + (ucRectBottom - ucRectTop) + "px";
        }
        const intOffWidth = wrpEl.offsetWidth;
        const intOfftHeight = wrpEl.offsetHeight;
        if (intOffWidth <= 0 || intOfftHeight <= 0) {
        } else {
            const topClp = Math.max(cRectTop - ucRectTop, 0);
            const leftClp = Math.max(cRectLeft - ucRectLeft, 0);
            const bottomClp = Math.max(ucRectBottom - cRectBottom, 0);
            const rightClp = Math.max(ucRectRight - cRectRight, 0);
            hid = topClp >= intOfftHeight || leftClp >= intOffWidth;
            if (oldHid !== hid) {
                wrpEl.style.visibility = hid ? "hidden" : "visible";
            }
            wrpEl.style.setProperty("clip-path", "inset(" + topClp + "px " + rightClp + "px " + bottomClp + "px " + leftClp + "px)");
        }
        if (!pos) {
            pos = true;
            wrpEl.style.visibility = "visible";
        }
        return (hid ? 1 : 0) | (pos ? 2 : 0);
    """
    )
}

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