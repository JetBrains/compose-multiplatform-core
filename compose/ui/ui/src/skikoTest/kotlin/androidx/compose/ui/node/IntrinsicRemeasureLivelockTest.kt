/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.ui.node

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.spatial.ExecuteDelayed
import androidx.compose.ui.spatial.RectManager
import androidx.compose.ui.unit.Constraints
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression test for a measure/layout livelock: a measure block that queries its child's
 * intrinsic size on every measure, combined with a child whose own measure block queries ITS
 * children's intrinsics (the shape produced by e.g. `Row(Modifier.height(IntrinsicSize.Max))`),
 * makes [MeasureAndLayoutDelegate.measureAndLayout] never drain its dirty set:
 *
 *   onIntrinsicsQueried -> requestRemeasure -> invalidateIntrinsicsParent -> requestRemeasure
 *
 * re-marks the intrinsics-querying ancestor on every drain iteration, forever.
 *
 * Composable-level equivalent (livelocks a real app, verified on desktop):
 * ```
 * Box(Modifier.layout { m, c ->
 *     val p = m.measure(c)
 *     val iw = m.maxIntrinsicWidth(c.maxHeight) // per-measure intrinsic query
 *     layout(maxOf(p.width, iw), p.height) { p.place(0, 0) }
 * }) {
 *     Row(Modifier.height(IntrinsicSize.Max)) { Text("left"); Text("right") }
 * }
 * ```
 */
@OptIn(InternalComposeUiApi::class)
class IntrinsicRemeasureLivelockTest {

    private val maxSanePasses = 50

    /** Leaf with fixed size and intrinsics, standing in for Text. */
    private fun leafPolicy() =
        object : MeasurePolicy {
            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints
            ): MeasureResult = layout(10, 10) {}

            override fun IntrinsicMeasureScope.minIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int
            ): Int = 10

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int
            ): Int = 10

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int
            ): Int = 10

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int
            ): Int = 10
        }

    /**
     * Queries its children's max intrinsic height during measure and forwards intrinsic
     * queries to its children - the node-level shape of `Row(Modifier.height(IntrinsicSize.Max))`.
     */
    private fun intrinsicHeightRowPolicy() =
        object : MeasurePolicy {
            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints
            ): MeasureResult {
                val height = measurables.maxOf { it.maxIntrinsicHeight(constraints.maxWidth) }
                val placeables = measurables.map { it.measure(constraints) }
                val width = placeables.sumOf { it.width }
                return layout(width, height) {
                    var x = 0
                    placeables.forEach {
                        it.place(x, 0)
                        x += it.width
                    }
                }
            }

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int
            ): Int = measurables.sumOf { it.maxIntrinsicWidth(height) }

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int
            ): Int = measurables.maxOf { it.maxIntrinsicHeight(width) }
        }

    /**
     * Measures its child and (optionally) queries the child's max intrinsic width on every
     * measure - the node-level shape of the `Modifier.layout` block in the repro.
     */
    private fun itemPolicy(queryIntrinsics: Boolean, onMeasure: () -> Unit) =
        object : MeasurePolicy {
            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints
            ): MeasureResult {
                onMeasure()
                val placeable = measurables.single().measure(constraints)
                val width =
                    if (queryIntrinsics) {
                        maxOf(
                            placeable.width,
                            measurables.single().maxIntrinsicWidth(constraints.maxHeight),
                        )
                    } else {
                        placeable.width
                    }
                return layout(width, placeable.height) { placeable.place(0, 0) }
            }
        }

    private fun measureCountAfterMeasureAndLayout(queryIntrinsics: Boolean): Int {
        val root = LayoutNode()
        val mock = MockOwner(root = root)
        lateinit var delegate: MeasureAndLayoutDelegate
        val owner =
            object : Owner by mock {
                // No-op scheduler: the default one posts to the platform main dispatcher,
                // which is not available in a bare test JVM.
                override val rectManager =
                    RectManager(
                        executeDelayed =
                            object : ExecuteDelayed {
                                override fun executeDelayed(
                                    delayMillis: Long,
                                    block: () -> Unit
                                ): Any = this

                                override fun removeDelayedExecution(token: Any) {}
                            }
                    )

                override fun onRequestMeasure(
                    layoutNode: LayoutNode,
                    affectsLookahead: Boolean,
                    forceRequest: Boolean,
                    scheduleMeasureAndLayout: Boolean,
                ) {
                    if (affectsLookahead) {
                        delegate.requestLookaheadRemeasure(layoutNode, forceRequest)
                    } else {
                        delegate.requestRemeasure(layoutNode, forceRequest)
                    }
                }

                override fun onRequestRelayout(
                    layoutNode: LayoutNode,
                    affectsLookahead: Boolean,
                    forceRequest: Boolean,
                ) {
                    if (affectsLookahead) {
                        delegate.requestLookaheadRelayout(layoutNode, forceRequest)
                    } else {
                        delegate.requestRelayout(layoutNode, forceRequest)
                    }
                }

                override fun forceMeasureTheSubtree(
                    layoutNode: LayoutNode,
                    affectsLookahead: Boolean
                ) {
                    delegate.forceMeasureTheSubtree(layoutNode, affectsLookahead)
                }
            }
        delegate = MeasureAndLayoutDelegate(root)

        var itemMeasures = 0
        val item =
            LayoutNode().apply {
                measurePolicy =
                    itemPolicy(queryIntrinsics) {
                        itemMeasures++
                        // Circuit breaker: a settling pass measures this node a handful of
                        // times; the livelock measures it forever.
                        check(itemMeasures <= maxSanePasses) {
                            "livelock: item measured $itemMeasures times within a single " +
                                "measureAndLayout() pass"
                        }
                    }
            }
        val row = LayoutNode().apply { measurePolicy = intrinsicHeightRowPolicy() }
        val left = LayoutNode().apply { measurePolicy = leafPolicy() }
        val right = LayoutNode().apply { measurePolicy = leafPolicy() }

        row.insertAt(0, left)
        row.insertAt(1, right)
        item.insertAt(0, row)
        root.insertAt(0, item)
        root.measurePolicy =
            object : MeasurePolicy {
                override fun MeasureScope.measure(
                    measurables: List<Measurable>,
                    constraints: Constraints
                ): MeasureResult {
                    val placeable = measurables.single().measure(constraints)
                    return layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(0, 0)
                    }
                }
            }
        root.attach(owner)

        delegate.updateRootConstraints(Constraints(maxWidth = 1000, maxHeight = 1000))
        delegate.requestRemeasure(root)
        delegate.measureAndLayout()
        return itemMeasures
    }

    @Test
    fun perMeasureIntrinsicQueryOverIntrinsicUsingContentSettles() {
        val measures = measureCountAfterMeasureAndLayout(queryIntrinsics = true)
        assertTrue(
            measures <= maxSanePasses,
            "expected measurement to settle, but the item was measured $measures times",
        )
    }

    @Test
    fun controlWithoutIntrinsicQuerySettles() {
        val measures = measureCountAfterMeasureAndLayout(queryIntrinsics = false)
        assertTrue(
            measures <= maxSanePasses,
            "expected measurement to settle, but the item was measured $measures times",
        )
    }
}
