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

package androidx.compose.ui.layout

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.WindowInsetsRulers.Companion.CaptionBar
import androidx.compose.ui.layout.WindowInsetsRulers.Companion.DisplayCutout
import androidx.compose.ui.layout.WindowInsetsRulers.Companion.Ime
import androidx.compose.ui.layout.WindowInsetsRulers.Companion.MandatorySystemGestures
import androidx.compose.ui.layout.WindowInsetsRulers.Companion.NavigationBars
import androidx.compose.ui.layout.WindowInsetsRulers.Companion.StatusBars
import androidx.compose.ui.layout.WindowInsetsRulers.Companion.SystemGestures
import androidx.compose.ui.layout.WindowInsetsRulers.Companion.TappableElement
import androidx.compose.ui.layout.WindowInsetsRulers.Companion.Waterfall
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.unit.Constraints

internal actual fun findDisplayCutouts(placementScope: Placeable.PlacementScope): List<RectRulers> {
    return emptyList()
}

internal actual fun findInsetsAnimationProperties(
    placementScope: Placeable.PlacementScope,
    windowInsetsRulers: WindowInsetsRulers
): WindowInsetsAnimation {
    return NoWindowInsetsAnimation
}

internal class RulerProviderModifierElement(
    val windowInsetsManager: PlatformWindowInsets
): ModifierNodeElement<RulerProviderModifierNode>() {
    override fun create(): RulerProviderModifierNode = RulerProviderModifierNode(windowInsetsManager)
    override fun hashCode(): Int = windowInsetsManager.hashCode()
    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        return (other as? RulerProviderModifierElement)?.windowInsetsManager === windowInsetsManager
    }
    override fun update(node: RulerProviderModifierNode) = Unit
}

private const val RulerKey = "androidx.compose.ui.layout.WindowInsetsRulers"

internal class RulerProviderModifierNode(
    windowInsetsManager: PlatformWindowInsets,
) : Modifier.Node(), LayoutModifierNode, TraversableNode {

    val rulerLambda: RulerScope.() -> Unit = {
        val (width, height) = coordinates.size

        provideInsetsValues(CaptionBar, windowInsetsManager.captionBar, width, height)
        provideInsetsValues(DisplayCutout, windowInsetsManager.displayCutout, width, height)
        provideInsetsValues(Ime, windowInsetsManager.ime, width, height)
        provideInsetsValues(MandatorySystemGestures, windowInsetsManager.mandatorySystemGestures, width, height)
        provideInsetsValues(NavigationBars, windowInsetsManager.navigationBars, width, height)
        provideInsetsValues(StatusBars, windowInsetsManager.statusBars, width, height)
        provideInsetsValues(SystemGestures, windowInsetsManager.systemGestures, width, height)
        provideInsetsValues(TappableElement, windowInsetsManager.tappableElement, width, height)
        provideInsetsValues(Waterfall, windowInsetsManager.waterfall, width, height)
    }

    private fun RulerScope.provideInsetsValues(
        rulers: WindowInsetsRulers,
        platformInsets: PlatformInsets,
        width: Int,
        height: Int
    ) {
        provideInsetsValues(rulers.current, platformInsets, width, height)
        provideInsetsValues(rulers.maximum, platformInsets, width, height)
    }

    private fun RulerScope.provideInsetsValues(
        rulers: RectRulers,
        insets: PlatformInsets,
        width: Int,
        height: Int,
    ) {
        if (insets != PlatformInsets.Unspecified) {
            val left = insets.left
            val top = insets.top
            val right = (width - insets.right)
            val bottom = (height - insets.bottom)

            rulers.left provides left.toFloat()
            rulers.top provides top.toFloat()
            rulers.right provides right.toFloat()
            rulers.bottom provides bottom.toFloat()
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        val width = placeable.width
        val height = placeable.height
        return layout(width, height, rulers = rulerLambda) { placeable.place(0, 0) }
    }

    override val traverseKey: Any
        get() = RulerKey
}