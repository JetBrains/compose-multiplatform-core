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

package androidx.compose.ui.test.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.allSemanticsNodes
import androidx.compose.ui.test.findFocusedUITextInput
import androidx.compose.ui.test.findSemanticsNode
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import platform.UIKit.UITouch
import platform.UIKit.UIView

// Mirror's foundation `Handle`, except Cursor (no-op on iOS)
internal enum class TestHandle { SelectionStart, SelectionEnd }
// Mirror's foundation `SelectionHandleAnchor`, except Middle (no-op on iOS)
internal enum class TestSelectionHandleAnchor { Left, Right }

/**
 * The contents of a selection handle, either parsed from foundation's internal `SelectionHandleInfo`
 * (Compose-rendered handles) or read off the private UIKit handle views of a native text field.
 *
 * @property position The point the handle is anchored to. For Compose handles it is read verbatim
 *   from foundation (relative to the selectable content; may be [Offset.Unspecified]); for native
 *   handles it is the selection edge in window-space points.
 */
internal data class TestSelectionHandleInfo(
    val handle: TestHandle,
    val position: Offset,
    val anchor: TestSelectionHandleAnchor,
    val visible: Boolean,
)

/**
 * A selection handle located for a test.
 *
 * @property grabPoint The window-space point (Dp) to aim a touch at to grab this handle: the center
 *   of the popup for Compose handles, or the "blob" of the lollipop for native ones.
 * @property node The semantics node backing a Compose-rendered handle popup, or null for a native
 *   iOS handle (which has no Compose semantics).
 */
internal class TestSelectionHandle(
    val info: TestSelectionHandleInfo,
    val grabPoint: DpOffset,
    val node: SemanticsNode? = null,
)

internal class SelectionHandlePair(
    val start: TestSelectionHandle,
    val end: TestSelectionHandle,
)

private const val SelectionHandleInfoKeyName = "SelectionHandleInfo"

/**
 * How long UIKit keeps a finished tap open for one more tap of the same sequence. Touches sent
 * within this window continue the sequence instead of starting a new gesture.
 * 500ms should be enough
 * TODO: CMP-10641
 */
private const val NativeTapSequenceWindowMillis = 500L

private val offsetRegex = Regex("""Offset\((-?[\d.]+),\s*(-?[\d.]+)\)""")

/**
 * Parses the `toString()` of foundation's internal `SelectionHandleInfo` data class, e.g.
 * `SelectionHandleInfo(handle=SelectionStart, position=Offset(12.3, 45.6), anchor=Left, visible=true)`.
 */
internal fun parseSelectionHandleInfo(raw: String): TestSelectionHandleInfo {
    fun field(name: String): String =
        Regex("""$name=(\w+)""").find(raw)?.groupValues?.get(1)
            ?: error("Could not parse '$name' from SelectionHandleInfo: $raw")

    val position = offsetRegex.find(raw)?.let { m ->
        Offset(m.groupValues[1].toFloat(), m.groupValues[2].toFloat())
    } ?: Offset.Unspecified

    return TestSelectionHandleInfo(
        handle = TestHandle.valueOf(field("handle")),
        position = position,
        anchor = TestSelectionHandleAnchor.valueOf(field("anchor")),
        visible = field("visible").toBooleanStrict(),
    )
}

internal fun UIKitInstrumentedTest.selectionHandles(): SelectionHandlePair? =
    composeSelectionHandles() ?: nativeSelectionHandles()

private fun UIKitInstrumentedTest.composeSelectionHandles(): SelectionHandlePair? {
    waitForIdle()
    val handles = allSemanticsNodes().mapNotNull { node ->
        val raw = node.config.firstOrNull { it.key.name == SelectionHandleInfoKeyName }?.value
            ?: return@mapNotNull null
        val info = parseSelectionHandleInfo(raw.toString())
        TestSelectionHandle(info = info, grabPoint = composeGrabPoint(node), node = node)
    }
    val start = handles.singleOrNull { it.info.handle == TestHandle.SelectionStart } ?: return null
    val end = handles.singleOrNull { it.info.handle == TestHandle.SelectionEnd } ?: return null
    return SelectionHandlePair(start, end)
}

/** The center of a Compose handle popup, in window-space Dp. */
private fun UIKitInstrumentedTest.composeGrabPoint(node: SemanticsNode): DpOffset {
    val bounds = node.boundsInWindow
    return with(density) { DpOffset(bounds.center.x.toDp(), bounds.center.y.toDp()) }
}

/**
 * Selection handles drawn by UIKit for a native text field, read off the private
 * `_UITextSelectionLollipopView` views under the focused `UITextInput`. UIKit shows both of them only
 * for a ranged selection: for a caret they stay in the view tree but hidden, so this returns null.
 */
private fun UIKitInstrumentedTest.nativeSelectionHandles(): SelectionHandlePair? {
    waitForIdle()
    val handles = nativeHandleViews().mapNotNull { nativeHandle(it) }
    val start = handles.singleOrNull { it.info.handle == TestHandle.SelectionStart } ?: return null
    val end = handles.singleOrNull { it.info.handle == TestHandle.SelectionEnd } ?: return null
    return SelectionHandlePair(start, end)
}

private fun UIKitInstrumentedTest.nativeHandleViews(): List<UIView> =
    (findFocusedUITextInput() as? UIView)?.descendants()
        ?.filter { it.isNativeSelectionHandle && !it.hidden && it.alpha > 0.0 }
        .orEmpty()

/** The stem covering the line at the selection edge and the blob to grab, or null for other views. */
private fun UIView.nativeHandleParts(): Pair<DpRect, DpRect>? {
    val parts = subviews.map { (it as UIView).dpRectInWindow() }
    if (parts.size != 2) return null
    return parts.minBy { it.width } to parts.maxBy { it.width }
}

/**
 * Builds a native [TestSelectionHandle] out of a handle view.
 */
private fun nativeHandle(handleView: UIView): TestSelectionHandle? {
    val (stem, blob) = handleView.nativeHandleParts() ?: return null

    val leading = blob.center().y < stem.center().y
    val edge = if (leading) stem.topCenter() else stem.bottomCenter()
    return TestSelectionHandle(
        info = TestSelectionHandleInfo(
            handle = if (leading) TestHandle.SelectionStart else TestHandle.SelectionEnd,
            position = Offset(edge.x.value, edge.y.value),
            anchor = if (leading) TestSelectionHandleAnchor.Left else TestSelectionHandleAnchor.Right,
            visible = true,
        ),
        grabPoint = blob.center(),
    )
}

private val UIView.isNativeSelectionHandle: Boolean
    get() = objcClassName(this)?.contains("_UITextSelectionLollipopView") == true

private fun UIView.descendants(): List<UIView> =
    subviews.flatMap { val view = it as UIView; listOf(view) + view.descendants() }

/**
 * Drags the [handle] selection handle to character [toOffset] of the text field tagged [tag].
 *
 * The handle is grabbed at its [grabPoint][TestSelectionHandle.grabPoint] — the popup center for a
 * Compose handle, or the lollipop blob for a native one — and from there the two paths differ.
 *
 * A Compose drag moves the grabbed edge by how far the finger travelled rather than to where it
 * stopped, so the finger follows the caret-to-caret vector (see [characterPosition]), stretched by
 * [clearingLineBoundary].
 *
 * A native handle cannot be driven that way — UIKit moves it to the next line only after the finger
 * has travelled far past that line, and publishes no selection until the touch is lifted, so there
 * is nothing to correct against. It is stepped towards its destination instead, see [stepHandleTo].
 *
 * The edge does not always land on [toOffset]. A Compose drag that expands the selection snaps to
 * word boundaries (`SelectionAdjustment.CharacterWithWordAccelerate`) — always when it changes line,
 * and within a line whenever the edge already sits on a boundary — so an offset inside a word is
 * reachable only while shrinking. UIKit does not snap and lands on the requested offset.
 */
internal fun UIKitInstrumentedTest.dragSelectionHandle(
    handle: TestHandle,
    tag: String,
    toOffset: Int,
    duration: Duration = 0.5.seconds,
) {
    val isNative = composeSelectionHandles() == null
    if (isNative) delay(NativeTapSequenceWindowMillis)

    val target = characterPosition(tag, toOffset)
    val selection = selectionRange(tag)
    val grabPoint = selectionHandle(handle).grabPoint
    val touch = touchDown(grabPoint)
    var failure: String? = null

    if (selection == null) {
        // A SelectionContainer publishes no selection to measure against, so a single aimed drag is
        // all there is and the edge lands only approximately.
        touch.dragTo(x = target.x, y = target.y, duration = duration)
    } else if (isNative) {
        failure = stepHandleTo(touch, from = grabPoint, aim = target)
    } else {
        val vector = (target - characterPosition(tag, selection.edgeOf(handle))).clearingLineBoundary()
        touch.dragBy(offset = vector, duration = duration)
        waitForIdle()
    }

    touch.up()
    waitForIdle()

    // Lift the touch before failing, so one stuck drag does not leave a finger down for the tests
    // that follow.
    failure?.let { error(it) }

    if (isNative) {
        // TODO: CMP-10641
        waitUntil("Selection loupe should hide after the drag") {
            findFirstDescendant { it.isLoupeView } == null
        }
    }
}

/**
 * Walks [touch] from [from] in small steps until the dragged selection edge reaches [aim]. Returns
 * null once it does, or why it did not.
 *
 * UIKit holds a dragged handle on its line until the finger has travelled far past the next one, by
 * an amount it never publishes, so the travel is discovered rather than computed: every step re-reads
 * where the edge actually is and aims the next one at what is left. The handle views are also the
 * only feedback there is, the selection being published only once the touch is lifted.
 */
private fun UIKitInstrumentedTest.stepHandleTo(
    touch: UITouch,
    from: DpOffset,
    aim: DpOffset,
): String? {
    var finger = from
    var previous: DpOffset? = null
    var motionless = 0

    repeat(NativeHandleDrag.MaxSteps) {
        val edge = draggedSelectionEdge(underPosition = finger)
            ?: return "Lost the native handle at $finger."
        val dx = aim.x.value - edge.x.value
        val dy = aim.y.value - edge.y.value
        // Vertical first and alone: UIKit obeys horizontal movement at once while still holding the
        // line change back, so a diagonal step would slide the handle far along its current line.
        val move = when {
            abs(dy) > NativeHandleDrag.Tolerance -> DpOffset(0.dp, dy.clampedToStep().dp)
            abs(dx) > NativeHandleDrag.Tolerance -> DpOffset(dx.clampedToStep().dp, 0.dp)
            else -> return null
        }

        // Standing still is normal while UIKit holds a line change back; standing still for longer
        // than that hysteresis means the edge is against something and will not move at all.
        val stillness = previous?.let { abs(edge.x.value - it.x.value) + abs(edge.y.value - it.y.value) }
        motionless = if (stillness != null && stillness <= NativeHandleDrag.Tolerance) motionless + 1 else 0
        if (motionless > NativeHandleDrag.MotionlessSteps) {
            return "Native selection edge stuck at $edge, aiming for $aim."
        }
        previous = edge

        touch.dragBy(offset = move, duration = NativeHandleDrag.StepDuration)
        finger += move
    }
    return "Native selection edge did not reach $aim in ${NativeHandleDrag.MaxSteps} steps."
}

/**
 * The line center at the selection edge of the handle whose blob is closest to [underPosition] — the
 * one the finger holds. Handles are told apart by side, not by role, and a drag past the other one
 * swaps the sides, so the finger is what to follow.
 */
private fun UIKitInstrumentedTest.draggedSelectionEdge(underPosition: DpOffset): DpOffset? {
    waitForIdle()
    return nativeHandleViews()
        .mapNotNull { it.nativeHandleParts() }
        .minByOrNull { (_, blob) ->
            val center = blob.center()
            abs(center.x.value - underPosition.x.value) + abs(center.y.value - underPosition.y.value)
        }
        ?.first?.center()
}

/** The shape of a native handle drag, measured against the simulator rather than derived. */
private object NativeHandleDrag {
    /** Finger travel per step, short enough to catch the line snap within one step. */
    const val Step = 4f

    /** How close to the aim counts as arrived, kept under half a character. */
    const val Tolerance = 1.5f

    /** Upper bound on the loop, not a working count: the drags in the tests settle in about 25. */
    const val MaxSteps = 80

    /** Steps the edge may stand still for, above the ~14 that a downward line change costs. */
    const val MotionlessSteps = 20

    /** Long enough for UIKit to move the handle before the next step re-reads it. */
    val StepDuration = 0.05.seconds
}

private fun Float.clampedToStep(): Float = coerceIn(-NativeHandleDrag.Step, NativeHandleDrag.Step)

/** Makes the vertical travel 1.5x longer, so touch slop does not stop the drag before the next line. */
private fun DpOffset.clearingLineBoundary(): DpOffset = DpOffset(x, y * 1.5f)

/** The selection published by the node tagged [tag], or null if it publishes none. */
private fun UIKitInstrumentedTest.selectionRange(tag: String): TextRange? =
    findSemanticsNode(tag).config.getOrNull(SemanticsProperties.TextSelectionRange)

private fun TextRange.edgeOf(handle: TestHandle): Int =
    if (handle == TestHandle.SelectionStart) start else end

private fun UIKitInstrumentedTest.selectionHandle(handle: TestHandle): TestSelectionHandle =
    when (handle) {
        TestHandle.SelectionStart -> selectionHandles()?.start
        TestHandle.SelectionEnd -> selectionHandles()?.end
    } ?: error("No $handle selection handle present.")
