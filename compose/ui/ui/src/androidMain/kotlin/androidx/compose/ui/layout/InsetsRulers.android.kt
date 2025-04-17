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
@file:Suppress("NOTHING_TO_INLINE")

package androidx.compose.ui.layout

import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.animation.Interpolator
import androidx.collection.IntObjectMap
import androidx.collection.MutableIntObjectMap
import androidx.collection.MutableObjectList
import androidx.collection.MutableScatterMap
import androidx.collection.ScatterMap
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.R
import androidx.compose.ui.layout.InsetsRulers.CaptionBar
import androidx.compose.ui.layout.InsetsRulers.DisplayCutout
import androidx.compose.ui.layout.InsetsRulers.Ime
import androidx.compose.ui.layout.InsetsRulers.MandatorySystemGestures
import androidx.compose.ui.layout.InsetsRulers.NavigationBars
import androidx.compose.ui.layout.InsetsRulers.SafeContent
import androidx.compose.ui.layout.InsetsRulers.SafeDrawing
import androidx.compose.ui.layout.InsetsRulers.SafeGestures
import androidx.compose.ui.layout.InsetsRulers.StatusBars
import androidx.compose.ui.layout.InsetsRulers.SystemGestures
import androidx.compose.ui.layout.InsetsRulers.TappableElement
import androidx.compose.ui.layout.InsetsRulers.Waterfall
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.NodeCoordinator
import androidx.compose.ui.node.Nodes
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.requestRemeasure
import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.core.graphics.Insets
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsAnimationCompat.BoundsCompat
import androidx.core.view.WindowInsetsCompat

object InsetsRulers {
    /**
     * Rulers used for caption bar insets.
     *
     * @see WindowInsetsCompat.Type.captionBar
     */
    val CaptionBar = AnimatableInsetsRulers("caption bar")

    /**
     * Rulers used for display cutout insets.
     *
     * @see WindowInsetsCompat.Type.displayCutout
     */
    val DisplayCutout = DisplayCutoutInsetsRulers()

    /**
     * Rulers used for IME insets.
     *
     * @see WindowInsetsCompat.Type.ime
     */
    val Ime = AnimatableInsetsRulers("ime")

    /**
     * Rulers used for mandatory system gestures insets.
     *
     * @see WindowInsetsCompat.Type.mandatorySystemGestures
     */
    val MandatorySystemGestures = AnimatableInsetsRulers("mandatory system gestures")

    /**
     * Rulers used for navigation bars insets.
     *
     * @see WindowInsetsCompat.Type.navigationBars
     */
    val NavigationBars = AnimatableInsetsRulers("navigation bars")

    /**
     * Rulers used for status bars insets.
     *
     * @see WindowInsetsCompat.Type.statusBars
     */
    val StatusBars = AnimatableInsetsRulers("status bars")

    /**
     * Rulers used for system bars insets, including [StatusBars], [NavigationBars], and
     * [CaptionBar]. When animating, the [BasicAnimatableInsetsRulers.source] and
     * [BasicAnimatableInsetsRulers.target] are populated and
     * [BasicAnimatableInsetsRulers.isAnimating] will be `true`.
     *
     * @see WindowInsetsCompat.Type.systemBars
     */
    val SystemBars: BasicAnimatableInsetsRulers =
        InnerInsetsRulers(
            StatusBars,
            NavigationBars,
            CaptionBar,
        )

    /**
     * Rulers used for system gestures insets.
     *
     * @see WindowInsetsCompat.Type.systemGestures
     */
    val SystemGestures = AnimatableInsetsRulers("system gestures")

    /**
     * Rulers used for tappable elements insets.
     *
     * @see WindowInsetsCompat.Type.tappableElement
     */
    val TappableElement = AnimatableInsetsRulers("tappable element")

    /**
     * Rulers used for waterfall insets.
     *
     * @see androidx.core.view.DisplayCutoutCompat.getWaterfallInsets
     */
    val Waterfall: RectRulers = RectRulers("waterfall")

    /**
     * Rulers used for insets including system bars, IME, and the display cutout. When animating,
     * the [BasicAnimatableInsetsRulers.source] and [BasicAnimatableInsetsRulers.target] are
     * populated, and [BasicAnimatableInsetsRulers.isAnimating] is `true`.
     *
     * @see WindowInsetsCompat.Type.systemBars
     * @see WindowInsetsCompat.Type.displayCutout
     * @see WindowInsetsCompat.Type.ime
     * @see WindowInsetsCompat.Type.tappableElement
     */
    val SafeDrawing: BasicAnimatableInsetsRulers =
        InnerInsetsRulers(
            StatusBars,
            NavigationBars,
            CaptionBar,
            DisplayCutout,
            Ime,
            TappableElement
        )

    /**
     * Rulers used for insets that include places where gestures could conflict. This includes
     * [MandatorySystemGestures], [SystemGestures], [TappableElement], and [Waterfall]. When
     * animating, the [BasicAnimatableInsetsRulers.source] and [BasicAnimatableInsetsRulers.target]
     * are populated, and [BasicAnimatableInsetsRulers.isAnimating] is `true`.
     *
     * @see WindowInsetsCompat.Type.mandatorySystemGestures
     * @see WindowInsetsCompat.Type.systemGestures
     * @see WindowInsetsCompat.Type.tappableElement
     * @see androidx.core.view.DisplayCutoutCompat.getWaterfallInsets
     */
    val SafeGestures: BasicAnimatableInsetsRulers =
        InnerInsetsRulers(
            MandatorySystemGestures,
            SystemGestures,
            TappableElement,
            Waterfall,
        )

    /**
     * Rulers used for insets that are safe for any content. This includes [SafeGestures] and
     * [SafeDrawing]. When animating, the [BasicAnimatableInsetsRulers.source] and
     * [BasicAnimatableInsetsRulers.target] are populated, and
     * [BasicAnimatableInsetsRulers.isAnimating] is `true`.
     */
    val SafeContent: BasicAnimatableInsetsRulers =
        InnerInsetsRulers(
            StatusBars,
            NavigationBars,
            CaptionBar,
            Ime,
            SystemGestures,
            MandatorySystemGestures,
            TappableElement,
            DisplayCutout,
            Waterfall,
        )
}

/**
 * A value class version of insets, made to reduce the number of allocations and State value reads.
 */
@JvmInline
internal value class ValueInsets(val packedValue: Long) {
    val left: Int
        inline get() = ((packedValue ushr 48) and 0xFFFF).toInt()

    val top: Int
        inline get() = ((packedValue ushr 32) and 0xFFFF).toInt()

    val right: Int
        inline get() = ((packedValue ushr 16) and 0xFFFF).toInt()

    val bottom: Int
        inline get() = (packedValue and 0xFFFF).toInt()

    override fun toString(): String {
        return "ValueInsets($left, $top, $right, $bottom)"
    }

    companion object
}

/** Create a [ValueInsets] from a normal [Insets] type. */
private inline fun ValueInsets(insets: Insets): ValueInsets =
    ValueInsets(
        (insets.left.toLong() shl 48) or
            (insets.top.toLong() shl 32) or
            (insets.right.toLong() shl 16) or
            (insets.bottom.toLong())
    )

/** Create a [ValueInsets] from individual values. */
private inline fun ValueInsets(left: Int, top: Int, right: Int, bottom: Int): ValueInsets =
    ValueInsets(
        (left.toLong() shl 48) or
            (top.toLong() shl 32) or
            (right.toLong() shl 16) or
            (bottom.toLong())
    )

/** A [ValueInsets] with all values set to `0`. */
private val ZeroValueInsets = ValueInsets(0L)

/** A [ValueInsets] representing `null` or unset values. */
private val UnsetValueInsets = ValueInsets(0xFFFF_FFFF_FFFF_FFFFUL.toLong())

/**
 * Rulers for Window Insets that can be animated and includes the source and target values for the
 * rulers as well as when the rulers are animating.
 */
sealed interface BasicAnimatableInsetsRulers : RectRulers {
    /** The starting insets value of the animation when [isAnimating] is `true`. */
    val source: RectRulers
    /** The ending insets value of the animation when [isAnimating] is `true`. */
    val target: RectRulers

    /**
     * True when the Window Insets are currently being animated. [source] and [target] will be set
     * while [isAnimating] is `true`.
     *
     * @param node The [DelegatableNode] that the is being used to read the value.
     */
    fun isAnimating(node: DelegatableNode): Boolean

    /**
     * True when the Window Insets are currently being animated. [source] and [target] will be set
     * while [isAnimating] is `true`.
     *
     * @param placementScope The [Placeable.PlacementScope] within [MeasureScope.layout].
     */
    fun isAnimating(placementScope: Placeable.PlacementScope): Boolean
}

/**
 * Calculates values for a merged [Ruler] or returns the default value when
 * [BasicAnimatableInsetsRulers.isAnimating] is `false` for all [animatingRulers].
 */
private inline fun Placeable.PlacementScope.calculateRulerValueWhenAnimating(
    useGreater: Boolean,
    defaultValue: Float,
    animatingRulers: Array<out RectRulers>,
    valueRulers: Array<RectRulers>,
    rulerChooser: (RectRulers) -> Ruler
): Float {
    val insetsValues = coordinates.findValues() ?: return defaultValue
    val isAnimating = animatingRulers.any { insetsValues[it]?.isAnimating == true }
    if (!isAnimating) {
        return defaultValue
    }
    var value = Float.NaN
    valueRulers.forEach { rectRulers ->
        val ruler = rulerChooser(rectRulers)
        val rulerValue = ruler.current(Float.NaN)
        if (!rulerValue.isNaN()) {
            if (value.isNaN() || ((rulerValue > value) == useGreater)) {
                value = rulerValue
            }
        }
    }
    return if (value.isNaN()) defaultValue else value
}

/**
 * Similar to [InnerRectRulers], but returns default values when
 * [BasicAnimatableInsetsRulers.isAnimating] is `false` for all [rulers].
 */
private class InnerOnlyWhenAnimatingRectRulers(
    val name: String,
    val rulers: Array<out RectRulers>,
    val valueRulers: Array<RectRulers>
) : RectRulers {
    override val left =
        VerticalRuler.derived { defaultValue ->
            calculateRulerValueWhenAnimating(true, defaultValue, rulers, valueRulers) { it.left }
        }
    override val top =
        HorizontalRuler.derived { defaultValue ->
            calculateRulerValueWhenAnimating(true, defaultValue, rulers, valueRulers) { it.top }
        }
    override val right =
        VerticalRuler.derived { defaultValue ->
            calculateRulerValueWhenAnimating(false, defaultValue, rulers, valueRulers) { it.right }
        }
    override val bottom =
        HorizontalRuler.derived { defaultValue ->
            calculateRulerValueWhenAnimating(false, defaultValue, rulers, valueRulers) { it.bottom }
        }

    override fun toString(): String = name
}

/**
 * A [BasicAnimatableInsetsRulers] for merging WindowInsets [RectRulers]. The [source] and [target]
 * will have values only when [isAnimating] is `true`. [isAnimating] will be `true` when any of the
 * [rulers] are [BasicAnimatableInsetsRulers] and are animating.
 */
class InnerInsetsRulers(vararg val rulers: RectRulers) : BasicAnimatableInsetsRulers {
    override val left: VerticalRuler = VerticalRuler.maxOf(*Array(rulers.size) { rulers[it].left })
    override val top: HorizontalRuler =
        HorizontalRuler.maxOf(*Array(rulers.size) { rulers[it].top })
    override val right: VerticalRuler =
        VerticalRuler.minOf(*Array(rulers.size) { rulers[it].right })
    override val bottom: HorizontalRuler =
        HorizontalRuler.minOf(*Array(rulers.size) { rulers[it].bottom })

    override val source: RectRulers =
        InnerOnlyWhenAnimatingRectRulers(
            name = "${toString()} source",
            rulers = rulers,
            valueRulers =
                Array(rulers.size) { index ->
                    val ruler = rulers[index]
                    if (ruler is BasicAnimatableInsetsRulers) {
                        ruler.source
                    } else {
                        ruler
                    }
                }
        )

    override val target: RectRulers =
        InnerOnlyWhenAnimatingRectRulers(
            name = "${toString()} target",
            rulers = rulers,
            valueRulers =
                Array(rulers.size) { index ->
                    val ruler = rulers[index]
                    if (ruler is BasicAnimatableInsetsRulers) {
                        ruler.target
                    } else {
                        ruler
                    }
                }
        )

    /**
     * True when the Window Insets are currently being animated. [source] and [target] will be set
     * while [isAnimating] is `true`.
     *
     * @param node The [DelegatableNode] that the is being used to read the value.
     */
    override fun isAnimating(node: DelegatableNode): Boolean =
        rulers.any { it is BasicAnimatableInsetsRulers && it.isAnimating(node) }

    /**
     * True when the Window Insets are currently being animated. [source] and [target] will be set
     * while [isAnimating] is `true`.
     *
     * @param placementScope The [Placeable.PlacementScope] within [MeasureScope.layout].
     */
    override fun isAnimating(placementScope: Placeable.PlacementScope): Boolean =
        rulers.any { it is BasicAnimatableInsetsRulers && it.isAnimating(placementScope) }

    override fun toString(): String =
        rulers.joinToString(",", prefix = "InnerInsetsRulers(", postfix = ")")
}

private fun LayoutCoordinates?.findValues(rectRulers: RectRulers): WindowInsetsValues? {
    return findValues()?.let { it[rectRulers] }
}

private fun LayoutCoordinates?.findValues(): ScatterMap<RectRulers, WindowInsetsValues>? {
    var node = this?.findRootCoordinates() as? NodeCoordinator
    while (node != null) {
        node.visitNodes(Nodes.Traversable) { traversableNode ->
            if (traversableNode.traverseKey === RulerKey) {
                return (traversableNode as RulerProviderModifierNode).insetsValues
            }
        }
        node = node.wrapped
    }
    return null // it hasn't been set on the root node
}

/**
 * Rulers for Window Insets that can be animated.
 *
 * This includes the position of the Window Insets without regard for whether the insets are
 * [visible][rulersIgnoringVisibility] as well as the [start][source] and [end][target] rulers of
 * any current animation. Developers can read whether the insets are current [visible][isVisible],
 * [isAnimating], the [fraction], [interpolatedFraction], [interpolator], and
 * [duration of the animation][durationMillis] when animating by using a [DelegatableNode].
 *
 * @sample androidx.compose.ui.samples.AnimatableInsetsRulersSample
 */
class AnimatableInsetsRulers internal constructor(private val name: String) :
    BasicAnimatableInsetsRulers {

    override val source: RectRulers = RectRulers("$name source")
    override val target: RectRulers = RectRulers("$name target")

    override fun isAnimating(node: DelegatableNode): Boolean =
        node.findValues()?.isAnimating == true

    override fun isAnimating(placementScope: Placeable.PlacementScope): Boolean =
        placementScope.findValues()?.isAnimating == true

    override val left: VerticalRuler = VerticalRuler()
    override val top: HorizontalRuler = HorizontalRuler()
    override val right: VerticalRuler = VerticalRuler()
    override val bottom: HorizontalRuler = HorizontalRuler()

    /**
     * The value of the Window Insets when they are visible. [Ime] never provides this value.
     *
     * @see WindowInsetsCompat.getInsetsIgnoringVisibility
     */
    val rulersIgnoringVisibility: RectRulers = RectRulers("$name ignoring visibility")

    // TODO:
    // The following should use context receiver scopes when they become a kotlin language feature.
    //
    // context(DelegatableNode)
    // val isVisible: Boolean get() = ...

    /**
     * Return `true` when the Window Insets are visible.
     *
     * @param node The [DelegatableNode] that the is being used to read the value.
     * @see WindowInsetsCompat.getInsets
     */
    fun isVisible(node: DelegatableNode): Boolean = node.findValues()?.isVisible == true

    /**
     * Return `true` when the Window Insets are visible.
     *
     * @param placementScope The [Placeable.PlacementScope] within [MeasureScope.layout].
     * @see WindowInsetsCompat.getInsets
     */
    fun isVisible(placementScope: Placeable.PlacementScope): Boolean =
        placementScope.findValues()?.isVisible == true

    /**
     * Returns the translucency of the animating window or `1` if [isAnimating] is `false`.
     *
     * @sample androidx.compose.ui.samples.InsetsRulersAlphaSample
     * @param node The [DelegatableNode] that the is being used to read the value.
     * @see WindowInsetsAnimationCompat.getAlpha
     */
    fun alpha(node: DelegatableNode): Float = node.findValues()?.alpha ?: 1f

    /**
     * Returns the translucency of the animating window or `1` if [isAnimating] is `false`.
     *
     * @sample androidx.compose.ui.samples.InsetsRulersAlphaSample
     * @param placementScope The [Placeable.PlacementScope] within [MeasureScope.layout].
     * @see WindowInsetsAnimationCompat.getAlpha
     */
    fun alpha(placementScope: Placeable.PlacementScope): Float =
        placementScope.findValues()?.alpha ?: 1f

    /**
     * Return the current fraction of the animation if the Window Insets are being animated or `0`
     * if [isAnimating] is `false`.
     *
     * @param node The [DelegatableNode] that the is being used to read the value.
     * @see WindowInsetsAnimationCompat.getFraction
     */
    fun fraction(node: DelegatableNode): Float = node.findValues()?.fraction ?: 0f

    /**
     * Return the current fraction of the animation if the Window Insets are being animated or `0`
     * if [isAnimating] is `false`.
     *
     * @param placementScope The [Placeable.PlacementScope] within [MeasureScope.layout].
     * @see WindowInsetsAnimationCompat.getFraction
     */
    fun fraction(placementScope: Placeable.PlacementScope): Float =
        placementScope.findValues()?.fraction ?: 0f

    /**
     * The current interpolated fraction of the animation, or `0` if [isAnimating] is `false`.
     *
     * @param node The [DelegatableNode] that the is being used to read the value.
     * @see WindowInsetsAnimationCompat.getInterpolatedFraction
     */
    fun interpolatedFraction(node: DelegatableNode): Float =
        node.findValues()?.interpolatedFraction ?: 0f

    /**
     * The current interpolated fraction of the animation, or `0` if [isAnimating] is `false`.
     *
     * @param placementScope The [Placeable.PlacementScope] within [MeasureScope.layout].
     * @see WindowInsetsAnimationCompat.getInterpolatedFraction
     */
    fun interpolatedFraction(placementScope: Placeable.PlacementScope): Float =
        placementScope.findValues()?.interpolatedFraction ?: 0f

    /**
     * The [Interpolator] that is being used in the animation of the Window Insets or `null` if
     * [isAnimating] is `false`.
     *
     * @param node The [DelegatableNode] that the is being used to read the value.
     * @see WindowInsetsAnimationCompat.getInterpolator
     */
    fun interpolator(node: DelegatableNode): Interpolator? = node.findValues()?.interpolator

    /**
     * The [Interpolator] that is being used in the animation of the Window Insets or `null` if
     * [isAnimating] is `false`.
     *
     * @param placementScope The [Placeable.PlacementScope] within [MeasureScope.layout].
     * @see WindowInsetsAnimationCompat.getInterpolator
     */
    fun interpolator(placementScope: Placeable.PlacementScope): Interpolator? =
        placementScope.findValues()?.interpolator

    /**
     * The duration of the animation or `0` if [isAnimating] is `false`.
     *
     * @param node The [DelegatableNode] that the is being used to read the value.
     * @see WindowInsetsAnimationCompat.getInterpolator
     */
    fun durationMillis(node: DelegatableNode): Long = node.findValues()?.durationMillis ?: 0L

    /**
     * The duration of the animation or `0` if [isAnimating] is `false`.
     *
     * @param placementScope The [Placeable.PlacementScope] within [MeasureScope.layout].
     * @see WindowInsetsAnimationCompat.getInterpolator
     */
    fun durationMillis(placementScope: Placeable.PlacementScope): Long =
        placementScope.findValues()?.durationMillis ?: 0L

    override fun toString(): String {
        return name
    }

    private inline fun DelegatableNode.findValues(): WindowInsetsValues? =
        node.coordinator.findValues(this@AnimatableInsetsRulers)

    private inline fun Placeable.PlacementScope.findValues(): WindowInsetsValues? =
        coordinates.findValues(this@AnimatableInsetsRulers)
}

/**
 * Rulers for the display cutout. The [left], [top], [right], and [bottom] indicate the bounds where
 * drawing will not intersect with the display cutout. [cutoutInsets] returns a list of [RectRulers]
 * surrounding the display cutouts themselves.
 */
class DisplayCutoutInsetsRulers internal constructor() : RectRulers {
    override val left: VerticalRuler = VerticalRuler()
    override val top: HorizontalRuler = HorizontalRuler()
    override val right: VerticalRuler = VerticalRuler()
    override val bottom: HorizontalRuler = HorizontalRuler()

    /** Returns a collection of display cutout bounds. */
    fun cutoutInsets(node: DelegatableNode): List<RectRulers> = node.node.coordinator.findRulers()

    /** Returns a collection of display cutout bounds. */
    fun cutoutInsets(placementScope: Placeable.PlacementScope): List<RectRulers> =
        placementScope.coordinates.findRulers()

    private fun LayoutCoordinates?.findRulers(): List<RectRulers> {
        var node = this?.findRootCoordinates() as? NodeCoordinator
        while (node != null) {
            node.visitNodes(Nodes.Traversable) { traversableNode ->
                if (traversableNode.traverseKey === RulerKey) {
                    return (traversableNode as RulerProviderModifierNode).cutoutRulers
                }
            }
            node = node.wrapped
        }
        return emptyList() // it hasn't been set on the root node
    }

    override fun toString(): String {
        return "display cutout"
    }
}

/**
 * Values for a single [RectRulers], including any values for [AnimatableInsetsRulers]. If the ruler
 * is not [AnimatableInsetsRulers], then the extra values are left unmodified. These are kept in
 * [InsetsListener.insetsValues].
 */
internal class WindowInsetsValues {
    /** True when the Window Insets are visible. */
    var isVisible by mutableStateOf(true)

    /** True when the Window Insets are currently being animated. */
    var isAnimating by mutableStateOf(false)

    /**
     * The current fraction of the animation if the Window Insets are being animated or `0` if
     * [isAnimating] is `false`.
     */
    var fraction by mutableFloatStateOf(0f)

    /** The current interpolated fraction of the animation, or `0` if [isAnimating] is `false`. */
    var interpolatedFraction by mutableFloatStateOf(0f)

    /**
     * The [Interpolator] that is being used in the animation of the Window Insets or `null` if
     * [isAnimating] is `false`.
     */
    var interpolator: Interpolator? by mutableStateOf(null)

    /** The duration of the animation. */
    var durationMillis by mutableLongStateOf(0)

    /** The translucency of the animating window */
    var alpha by mutableFloatStateOf(1f)

    private var _insets by mutableLongStateOf(0L)

    /** The current Window Insets values. */
    var insets: ValueInsets
        get() = ValueInsets(_insets)
        set(value) {
            _insets = value.packedValue
        }

    private var _ignoringVisibility by mutableLongStateOf(UnsetValueInsets.packedValue)

    /** The value of thw Window Insets when they are visible. [Ime] never provides this value. */
    var ignoringVisibility: ValueInsets
        get() = ValueInsets(_ignoringVisibility)
        set(value) {
            _ignoringVisibility = value.packedValue
        }

    private var _source by mutableLongStateOf(UnsetValueInsets.packedValue)

    /** The starting insets value of the animation when [isAnimating] is `true`. */
    var source: ValueInsets
        get() = ValueInsets(_source)
        set(value) {
            _source = value.packedValue
        }

    private var _target by mutableLongStateOf(UnsetValueInsets.packedValue)

    /** The ending insets value of the animation when [isAnimating] is `true`. */
    var target: ValueInsets
        get() = ValueInsets(_target)
        set(value) {
            _target = value.packedValue
        }
}

/** Applies the rulers for window insets. */
internal fun Modifier.applyWindowInsetsRulers(insetsListener: InsetsListener) =
    this.then(RulerProviderModifierElement(insetsListener))
        .then(ImeRulerProviderModifierElement(insetsListener))

/** [ModifierNodeElement] that provides all [RectRulers] except for [Ime]. */
private class RulerProviderModifierElement(val insetsListener: InsetsListener) :
    ModifierNodeElement<RulerProviderModifierNode>() {
    override fun create(): RulerProviderModifierNode = RulerProviderModifierNode(insetsListener)

    override fun hashCode(): Int = 0

    override fun equals(other: Any?): Boolean = other === this

    override fun update(node: RulerProviderModifierNode) {
        node.insetsListener = insetsListener
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "windowInsetsRulers"
    }
}

private val RulerKey = "androidx.compose.ui.layout.WindowInsetsRulers"

/**
 * [Modifier.Node] that provides all [RectRulers] except for [Ime] and other Rulers animated with
 * the IME. The private [WindowInsetsValues] are provided as a [TraversableNode].
 */
private class RulerProviderModifierNode(insetsListener: InsetsListener) :
    Modifier.Node(), LayoutModifierNode, TraversableNode {
    val insetsValues = insetsListener.insetsValues

    val cutoutRects: MutableObjectList<MutableState<Rect>> = insetsListener.displayCutouts
    val cutoutRulers: List<RectRulers> = insetsListener.displayCutoutRulers

    var insetsListener: InsetsListener = insetsListener
        set(value) {
            if (field !== value) {
                field = value
                requestRemeasure()
            }
        }

    val rulerLambda: RulerScope.() -> Unit = {
        val size = coordinates.size
        provideValuesForRulers(
            size.width,
            size.height,
            insetsListener,
            NonImeWindowInsetsRulers,
            AnimatableNonImeWindowInsetsRulers
        )
        if (cutoutRects.isNotEmpty()) {
            cutoutRects.forEachIndexed { index, rectState ->
                val rulers = cutoutRulers[index]
                val rect = rectState.value
                rulers.left provides rect.left.toFloat()
                rulers.top provides rect.top.toFloat()
                rulers.right provides rect.right.toFloat()
                rulers.bottom provides rect.bottom.toFloat()
            }
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        val width = placeable.width
        val height = placeable.height
        return layout(width, height, rulers = rulerLambda) { placeable.place(0, 0) }
    }

    override val traverseKey: Any
        get() = RulerKey
}

/** [ModifierNodeElement] that provides [Ime], [SafeDrawing], and [SafeContent] values. */
private class ImeRulerProviderModifierElement(val insetsListener: InsetsListener) :
    ModifierNodeElement<ImeRulerProviderModifierNode>() {
    override fun create(): ImeRulerProviderModifierNode =
        ImeRulerProviderModifierNode(insetsListener)

    override fun hashCode(): Int = 0

    override fun equals(other: Any?): Boolean = other === this

    override fun update(node: ImeRulerProviderModifierNode) {
        node.insetsListener = insetsListener
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "animatedWindowInsetsRulers"
    }
}

/** [Modifier.Node] that provides [Ime], [SafeDrawing], and [SafeContent] values. */
private class ImeRulerProviderModifierNode(insetsListener: InsetsListener) :
    Modifier.Node(), LayoutModifierNode {
    var insetsListener: InsetsListener = insetsListener
        set(value) {
            if (field !== value) {
                field = value
                requestRemeasure()
            }
        }

    val rulerLambda: RulerScope.() -> Unit = {
        val size = coordinates.size
        provideValuesForRulers(
            size.width,
            size.height,
            insetsListener,
            ImeWindowInsetsRulers,
            ImeWindowInsetsRulers
        )
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        val width = placeable.width
        val height = placeable.height
        return layout(width, height, rulers = rulerLambda) { placeable.place(0, 0) }
    }
}

private fun <T : RectRulers> RulerScope.provideValuesForRulers(
    width: Int,
    height: Int,
    insetsListener: InsetsListener,
    allRulers: Array<T>,
    animatableRulers: Array<AnimatableInsetsRulers>
) {
    val insetsValues = insetsListener.insetsValues
    allRulers.forEach { rulers ->
        val values = insetsValues[rulers]!!
        provideInsetsValues(rulers, values.insets, width, height)
    }
    animatableRulers.forEach { rulers ->
        val values = insetsValues[rulers]!!
        if (values.isAnimating) {
            provideInsetsValues(rulers.source, values.source, width, height)
            provideInsetsValues(rulers.target, values.target, width, height)
        }
        provideInsetsValues(
            rulers.rulersIgnoringVisibility,
            values.ignoringVisibility,
            width,
            height
        )
    }
}

/** Provide values for a [RectRulers]. */
private fun RulerScope.provideInsetsValues(
    rulers: RectRulers,
    insets: ValueInsets,
    width: Int,
    height: Int
) {
    if (insets != UnsetValueInsets) {
        val left = maxOf(0, insets.left)
        val top = maxOf(0, insets.top)
        val right = maxOf(0, insets.right)
        val bottom = maxOf(0, insets.bottom)

        rulers.left provides left.toFloat()
        rulers.top provides top.toFloat()
        rulers.right provides (width - right).toFloat()
        rulers.bottom provides (height - bottom).toFloat()
    }
}

/**
 * A listener for WindowInsets changes. This updates the [insetsValues] values whenever values
 * change.
 */
internal class InsetsListener(
    val composeView: AndroidComposeView,
) :
    WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE),
    Runnable,
    OnApplyWindowInsetsListener,
    OnAttachStateChangeListener {
    /**
     * When [android.view.WindowInsetsController.controlWindowInsetsAnimation] is called, the
     * [onApplyWindowInsets] is called after [onPrepare] with the target size. We don't want to
     * report the target size, we want to always report the current size, so we must ignore those
     * calls. However, the animation may be canceled before it progresses. On R, it won't make any
     * callbacks, so we have to figure out whether the [onApplyWindowInsets] is from a canceled
     * animation or if it is from the controlled animation. When [prepared] is `true` on R, we post
     * a callback to set the [onApplyWindowInsets] insets value.
     */
    private var prepared = false

    /** `true` if there is an animation in progress. */
    private var runningAnimationMask = 0

    private var savedInsets: WindowInsetsCompat? = null

    /**
     * A mapping of [RectRulers] to the actual values [WindowInsetsValues] that back them. Each
     * [AndroidComposeView] will have different values.
     */
    val insetsValues: ScatterMap<RectRulers, WindowInsetsValues> =
        MutableScatterMap<RectRulers, WindowInsetsValues>(9).also {
            it[CaptionBar] = WindowInsetsValues()
            it[DisplayCutout] = WindowInsetsValues()
            it[Ime] = WindowInsetsValues()
            it[MandatorySystemGestures] = WindowInsetsValues()
            it[NavigationBars] = WindowInsetsValues()
            it[StatusBars] = WindowInsetsValues()
            it[SystemGestures] = WindowInsetsValues()
            it[TappableElement] = WindowInsetsValues()
            it[Waterfall] = WindowInsetsValues()
        }

    val displayCutouts = MutableObjectList<MutableState<Rect>>(4)
    val displayCutoutRulers = mutableStateListOf<RectRulers>()

    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
        prepared = true
        super.onPrepare(animation)
    }

    override fun onStart(
        animation: WindowInsetsAnimationCompat,
        bounds: BoundsCompat
    ): BoundsCompat {
        val insets = savedInsets
        if (animation.durationMillis > 0L && insets != null) {
            val type = animation.typeMask
            runningAnimationMask = runningAnimationMask or type
            // This is the animation's target value
            val rulers = AnimatableRulers[type]
            if (rulers != null) {
                val insetsValue = insetsValues[rulers]!!
                val target = ValueInsets(insets.getInsets(type))
                val current = insetsValue.insets
                if (target != current) {
                    // It is really animating. The target is different from the current value
                    insetsValue.source = current
                    insetsValue.target = target
                    insetsValue.isAnimating = true
                    updateInsetAnimationInfo(insetsValue, animation)
                }
            }
        }

        prepared = false
        savedInsets = null
        Snapshot.sendApplyNotifications()
        return super.onStart(animation, bounds)
    }

    private fun WindowInsetsValues.targetValues(): ValueInsets {
        return if (isAnimating) {
            target
        } else {
            insets
        }
    }

    private fun updateInsetAnimationInfo(
        insetsValue: WindowInsetsValues,
        animation: WindowInsetsAnimationCompat
    ) {
        insetsValue.interpolator = animation.interpolator
        insetsValue.fraction = animation.fraction
        insetsValue.alpha = animation.alpha
        insetsValue.interpolatedFraction = animation.interpolatedFraction
        insetsValue.durationMillis = animation.durationMillis
    }

    override fun onProgress(
        insets: WindowInsetsCompat,
        runningAnimations: MutableList<WindowInsetsAnimationCompat>
    ): WindowInsetsCompat {
        runningAnimations.fastForEach { animation ->
            val typeMask = animation.typeMask
            val rulers = AnimatableRulers[typeMask]
            if (rulers != null) {
                val insetsValue = insetsValues[rulers]!!
                if (insetsValue.isAnimating) {
                    // It is really animating. It could be animating to the same value, so there
                    // is no need to pretend that it is animating.
                    updateInsetAnimationInfo(insetsValue, animation)
                    insetsValue.insets = ValueInsets(insets.getInsets(typeMask))
                }
            }
        }
        updateInsets(insets)
        return insets
    }

    override fun onEnd(animation: WindowInsetsAnimationCompat) {
        prepared = false
        val type = animation.typeMask
        val rulers = AnimatableRulers[type]
        if (rulers != null) {
            val insetsValue = insetsValues[rulers]!!
            insetsValue.interpolator = null
            insetsValue.fraction = 0f
            insetsValue.interpolatedFraction = 0f
            insetsValue.alpha = 1f
            insetsValue.durationMillis = 0L
            insetsValue.fraction = 0f
            insetsValue.interpolatedFraction = 0f
            insetsValue.interpolator = null
            stopAnimationForRuler(insetsValue)
        }
        runningAnimationMask = runningAnimationMask and type.inv()
        savedInsets = null
        Snapshot.sendApplyNotifications()
        super.onEnd(animation)
    }

    private fun stopAnimationForRuler(insetsValue: WindowInsetsValues) {
        insetsValue.isAnimating = false
        insetsValue.source = UnsetValueInsets
        insetsValue.target = UnsetValueInsets
    }

    override fun onApplyWindowInsets(view: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        // Keep track of the most recent insets we've seen, to ensure onEnd will always use the
        // most recently acquired insets
        if (prepared) {
            savedInsets = insets // save for onStart()

            // There may be no callback on R if the animation is canceled after onPrepare(),
            // so we won't know if the onPrepare() was canceled or if this is an
            // onApplyWindowInsets() after the cancellation. We'll just post the value
            // and if it is still preparing then we just use the value.
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                view.post(this)
            }
        } else if (runningAnimationMask == 0) {
            // If an animation is running, rely on onProgress() to update the insets
            // On APIs less than 30 where the IME animation is backported, this avoids reporting
            // the final insets for a frame while the animation is running.
            updateInsets(insets)
        }
        return insets
    }

    private fun updateInsets(insets: WindowInsetsCompat) {
        WindowInsetsTypeMap.forEach { type, rulers ->
            val insetsValue = ValueInsets(insets.getInsets(type))
            val values = insetsValues[rulers]!!
            values.insets = insetsValue
        }
        AnimatableRulers.forEach { type, rulers ->
            val values = insetsValues[rulers]!!
            if (type != WindowInsetsCompat.Type.ime()) {
                val insetsValue = ValueInsets(insets.getInsetsIgnoringVisibility(type))
                values.ignoringVisibility = insetsValue
            }
            values.isVisible = insets.isVisible(type)
        }
        val cutout = insets.displayCutout
        val waterfall =
            if (cutout == null) {
                ZeroValueInsets
            } else {
                ValueInsets(cutout.waterfallInsets)
            }
        insetsValues[Waterfall]!!.insets = waterfall
        val cutoutInsets =
            if (cutout == null) {
                ZeroValueInsets
            } else {
                with(cutout) {
                    ValueInsets(safeInsetLeft, safeInsetTop, safeInsetRight, safeInsetBottom)
                }
            }
        insetsValues[DisplayCutout]!!.insets = cutoutInsets
        if (cutout == null) {
            if (displayCutouts.size > 0) {
                displayCutouts.clear()
                displayCutoutRulers.clear()
            }
        } else {
            val boundingRects = cutout.boundingRects
            if (boundingRects.size < displayCutouts.size) {
                displayCutouts.removeRange(boundingRects.size, displayCutouts.size)
                displayCutoutRulers.removeRange(boundingRects.size, displayCutoutRulers.size)
            } else {
                repeat(boundingRects.size - displayCutouts.size) {
                    displayCutouts += mutableStateOf(boundingRects[displayCutouts.size])
                    displayCutoutRulers += RectRulers("display cutout rect ${displayCutouts.size}")
                }
            }

            boundingRects.fastForEachIndexed { index, rect -> displayCutouts[index].value = rect }
        }
        Snapshot.sendApplyNotifications()
    }

    /**
     * On [R], we don't receive the [onEnd] call when an animation is canceled, so we post the value
     * received in [onApplyWindowInsets] immediately after [onPrepare]. If [onProgress] or [onEnd]
     * is received before the runnable executes then the value won't be used. Otherwise, the
     * [onApplyWindowInsets] value will be used. It may have a janky frame, but it is the best we
     * can do.
     */
    override fun run() {
        if (prepared) {
            runningAnimationMask = 0
            prepared = false
            savedInsets?.let {
                updateInsets(it)
                savedInsets = null
            }
        }
    }

    override fun onViewAttachedToWindow(view: View) {
        // Until merging the foundation layout implementation and this implementation, we'll
        // listen on the ComposeView containing the AndroidComposeView so that there isn't
        // a collision
        val listenerView = view.parent as? View ?: view
        ViewCompat.setOnApplyWindowInsetsListener(listenerView, this)
        ViewCompat.setWindowInsetsAnimationCallback(listenerView, this)
    }

    override fun onViewDetachedFromWindow(view: View) {
        // Until merging the foundation layout implementation and this implementation, we'll
        // listen on the ComposeView containing the AndroidComposeView so that there isn't
        // a collision
        val listenerView = view.parent as? View ?: view
        ViewCompat.setOnApplyWindowInsetsListener(listenerView, null)
        ViewCompat.setWindowInsetsAnimationCallback(listenerView, null)
    }
}

/** Mapping the [WindowInsetsCompat.Type] to the [RectRulers] for all single insets types. */
private val WindowInsetsTypeMap: IntObjectMap<RectRulers> =
    MutableIntObjectMap<RectRulers>(8).also {
        it[WindowInsetsCompat.Type.statusBars()] = StatusBars
        it[WindowInsetsCompat.Type.navigationBars()] = NavigationBars
        it[WindowInsetsCompat.Type.captionBar()] = CaptionBar
        it[WindowInsetsCompat.Type.ime()] = Ime
        it[WindowInsetsCompat.Type.systemGestures()] = SystemGestures
        it[WindowInsetsCompat.Type.mandatorySystemGestures()] = MandatorySystemGestures
        it[WindowInsetsCompat.Type.tappableElement()] = TappableElement
    }

/** Rulers that don't animate with the IME */
private val NonImeWindowInsetsRulers =
    arrayOf(
        StatusBars,
        NavigationBars,
        CaptionBar,
        SystemGestures,
        TappableElement,
        MandatorySystemGestures,
        DisplayCutout,
        Waterfall
    )

/** Rulers that can animate, but don't always animate with the IME */
private val AnimatableNonImeWindowInsetsRulers =
    arrayOf(
        StatusBars,
        NavigationBars,
        CaptionBar,
        TappableElement,
        SystemGestures,
        MandatorySystemGestures,
    )

/** Rulers that animate with the IME */
private val ImeWindowInsetsRulers = arrayOf(Ime)

/**
 * Mapping the [WindowInsetsCompat.Type] to the [RectRulers] for only the insets that can animate.
 */
private val AnimatableRulers =
    MutableIntObjectMap<AnimatableInsetsRulers>(7).also {
        it[WindowInsetsCompat.Type.statusBars()] = StatusBars
        it[WindowInsetsCompat.Type.navigationBars()] = NavigationBars
        it[WindowInsetsCompat.Type.captionBar()] = CaptionBar
        it[WindowInsetsCompat.Type.systemGestures()] = SystemGestures
        it[WindowInsetsCompat.Type.tappableElement()] = TappableElement
        it[WindowInsetsCompat.Type.mandatorySystemGestures()] = MandatorySystemGestures
        it[WindowInsetsCompat.Type.ime()] = Ime
    }
