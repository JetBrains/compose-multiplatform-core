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
package androidx.compose.ui.layout

/**
 * A collection of [Ruler]s used to define a Rectangle.
 *
 * @sample androidx.compose.ui.samples.WindowInsetsRulersSample
 */
interface RectRulers {
    /** The left position of the rectangle. */
    val left: VerticalRuler

    /** The top position of the rectangle. */
    val top: HorizontalRuler

    /** The right position of the rectangle */
    val right: VerticalRuler

    /** The bottom position of the rectangle */
    val bottom: HorizontalRuler
}

/** Creates a [RectRulers] with the given optional [name] returned from [RectRulers.toString]. */
fun RectRulers(name: String? = null): RectRulers = RectRulersImpl(name)

private class RectRulersImpl(private val name: String? = null) : RectRulers {
    override var left: VerticalRuler = VerticalRuler()
    override var top: HorizontalRuler = HorizontalRuler()
    override var right: VerticalRuler = VerticalRuler()
    override var bottom: HorizontalRuler = HorizontalRuler()

    override fun toString(): String {
        return name ?: super.toString()
    }
}

/**
 * Merges multiple [RectRulers] into a single [RectRulers], using the inner-most value. That is, the
 * [left] will be the greatest [RectRulers.left], the [top] will be the greatest [RectRulers.top],
 * the [right] will be the least [RectRulers.right], and the [bottom] will be the least of all
 * [rulers].
 *
 * When [rulers] provide non-overlapping values, the result may have negative size. For example, if
 * one [RectRulers] provides (10, 20, 30, 40) as their ruler values, and another provides (1, 1, 5,
 * 5), the merged result will be (10, 20, 5, 5).
 *
 * If one of the [rulers] does not provide a value, it will not be considered in the calculation.
 */
class InnerRectRulers(private vararg val rulers: RectRulers) : RectRulers {
    override val left: VerticalRuler =
        MergedVerticalRuler(shouldUseGreater = true, *Array(rulers.size) { rulers[it].left })
    override val top: HorizontalRuler =
        MergedHorizontalRuler(shouldUseGreater = true, *Array(rulers.size) { rulers[it].top })
    override val right: VerticalRuler =
        MergedVerticalRuler(shouldUseGreater = false, *Array(rulers.size) { rulers[it].right })
    override val bottom: HorizontalRuler =
        MergedHorizontalRuler(shouldUseGreater = false, *Array(rulers.size) { rulers[it].bottom })

    override fun toString(): String {
        return rulers.joinToString(prefix = "InnerRectRulers(", postfix = ")")
    }
}

/**
 * Merges multiple [RectRulers] into a single [RectRulers], using the outer-most value. That is, the
 * [left] will be the lest [RectRulers.left], the [top] will be the least [RectRulers.top], the
 * [right] will be the greatest [RectRulers.right], and the [bottom] will be the greatest of all
 * [rulers].
 *
 * If one of the [rulers] does not provide a value, it will not be considered in the calculation.
 */
class OuterRectRulers(private vararg val rulers: RectRulers) : RectRulers {
    override val left: VerticalRuler =
        MergedVerticalRuler(shouldUseGreater = false, *Array(rulers.size) { rulers[it].left })
    override val top: HorizontalRuler =
        MergedHorizontalRuler(shouldUseGreater = false, *Array(rulers.size) { rulers[it].top })
    override val right: VerticalRuler =
        MergedVerticalRuler(shouldUseGreater = true, *Array(rulers.size) { rulers[it].right })
    override val bottom: HorizontalRuler =
        MergedHorizontalRuler(shouldUseGreater = true, *Array(rulers.size) { rulers[it].bottom })

    override fun toString(): String {
        return rulers.joinToString(prefix = "OuterRectRulers(", postfix = ")")
    }
}
