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

package noria.foundation.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import kotlin.math.roundToInt
import noria.foundation.layout.internal.OverlayBoxMeasurePolicy
import noria.foundation.layout.internal.OverlayChildData

@Composable
fun OverlayHost(
    key: OverlayHostKey,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val overlayHostState = remember(key) { OverlayHostState() }
    CompositionLocalProvider(key provides overlayHostState) {
        Box(
            modifier.onPlaced { overlayHostState.coordinates = it },
            propagateMinConstraints = true,
        ) {
            content()

            if (overlayHostState.coordinates != null) {
                overlayHostState.overlays.forEach { overlay ->
                    SubcomposeLayout(
                        compositionContext = overlay.compositionContext,
                    ) { constraints ->
                        val measurables = subcompose(overlay) {
                            val overlayScope = remember(overlay) {
                                object : OverlayScope {
                                    override val anchorBounds: IntRect
                                        get() = overlay.anchorBounds!!

                                    override fun Modifier.align(alignment: Alignment): Modifier =
                                        this then AlignModifier(alignment)

                                    override fun Modifier.matchParentSize(): Modifier =
                                        this then MatchParentSize

                                    override fun Modifier.alignInAnchor(alignment: Alignment): Modifier =
                                        this then AlignInAnchorModifier(alignment)

                                    override fun Modifier.alignByAnchor(alignment: Alignment): Modifier =
                                        this then AlignByAnchorModifier(alignment)

                                    override fun Modifier.alignByAnchor(
                                        anchor: Alignment.Horizontal,
                                        side: Alignment.Vertical,
                                    ): Modifier =
                                        this then AlignByAnchorHorizontalModifier(anchor, side)

                                    override fun Modifier.alignByAnchor(
                                        anchor: Alignment.Vertical,
                                        side: Alignment.Horizontal,
                                    ): Modifier =
                                        this then AlignByAnchorVerticalModifier(anchor, side)
                                }
                            }
                            Layout(
                                { overlay.content(overlayScope) },
                                Modifier.sizeIn(
                                    overlayHostState.coordinates!!.size.width.toDp(),
                                    overlayHostState.coordinates!!.size.height.toDp(),
                                    overlayHostState.coordinates!!.size.width.toDp(),
                                    overlayHostState.coordinates!!.size.height.toDp(),
                                ),
                                remember(overlay) { OverlayBoxMeasurePolicy(overlay) }
                            )
                        }
                        val placeables = measurables.map { measurable ->
                            measurable.measure(constraints)
                        }
                        layout(
                            overlayHostState.coordinates!!.size.width,
                            overlayHostState.coordinates!!.size.height
                        ) {
                            placeables.forEach { it.place(0, 0) }
                        }
                    }
                }
            }
        }
    }
}

fun OverlayHostKey(): OverlayHostKey =
    compositionLocalOf { error("Cannot provide a default for an OverlayHostKey") }

typealias OverlayHostKey = ProvidableCompositionLocal<OverlayHostState>

val MainOverlayHostKey = OverlayHostKey()

class OverlayHostState {
    internal var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    internal var overlays by mutableStateOf(emptyList<OverlayState>())
}

internal class OverlayState(
    compositionContext: CompositionContext,
    content: @Composable OverlayScope.() -> Unit,
) {
    var compositionContext by mutableStateOf(compositionContext)
    var content by mutableStateOf(content)
    var anchorBounds by mutableStateOf<IntRect?>(null)
}

private object MatchParentSize : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = OverlayChildData.MatchParentSize
}

private data class AlignModifier(private val alignment: Alignment) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = OverlayChildData.Align(alignment)
}

private data class AlignInAnchorModifier(private val alignment: Alignment) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any =
        OverlayChildData.AlignInAnchor(alignment)
}

private data class AlignByAnchorModifier(private val alignment: Alignment) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any =
        OverlayChildData.AlignByAnchor(alignment)
}

private data class AlignByAnchorHorizontalModifier(
    private val anchor: Alignment.Horizontal,
    private val side: Alignment.Vertical,
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any =
        OverlayChildData.AlignByAnchorHorizontally(anchor, side)
}

private data class AlignByAnchorVerticalModifier(
    private val anchor: Alignment.Vertical,
    private val side: Alignment.Horizontal,
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any =
        OverlayChildData.AlignByAnchorVertically(anchor, side)
}

/**
 * Rounds a [Rect] to an [IntRect]
 */
@Stable
fun Rect.roundToIntRect(): IntRect = IntRect(
    left = left.roundToInt(), top = top.roundToInt(), right = right.roundToInt(), bottom =
        bottom.roundToInt()
)
