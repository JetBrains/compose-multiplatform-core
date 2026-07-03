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

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.findSemanticsNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpOffset
import platform.UIKit.UIView
import platform.UIKit.UIWindow

internal enum class BasicTextFieldType { V1, V2 }

internal val loupeClassNames = listOf(
    "_UITextMagnifiedLoupeView",
    "_UITextLoupeView",
    "LoupeView"
)

internal val UIView.isLoupeView: Boolean get() {
    val name = objcClassName(this) ?: return false
    return loupeClassNames.any { name.contains(it) }
}

private fun findFirstDescendant(root: UIView, predicate: (UIView) -> Boolean): UIView? {
    if (predicate(root)) return root
    root.subviews.forEach { view ->
        view as UIView
        val hit = findFirstDescendant(view, predicate)
        if (hit != null) return hit
    }
    return null
}

internal fun UIKitInstrumentedTest.findFirstDescendant(predicate: (UIView) -> Boolean): UIView? {
    val windowScene = viewController.view.window?.windowScene ?: return null
    windowScene.windows.forEach { window ->
        window as UIWindow
        val hit = findFirstDescendant(window, predicate)
        if (hit != null) return hit
    }
    return null
}

/**
 * The window-space point (in Dp) of the caret for character [offset] in the text field tagged
 * [tag]. Use it to aim touch gestures at a specific character.
 *
 * Caveat — a tap on an iOS field (including the focus-gaining tap) does not leave the caret
 * mid-word: it snaps to a word boundary, so this point is an aim, not a guaranteed landing offset.
 * On the Compose path the snap splits at the word's midpoint (first half → word start, second half
 * → word end; see `determineCursorDesiredOffset`). The native UITextInput path follows the same
 * idea, but its split point is private to iOS and varies with word length, font and more; treat it
 * as the Compose path, yet only clearly-leading and clearly-trailing taps are deterministic — a tap
 * in the start-to-middle zone may snap either way.
 */
internal fun UIKitInstrumentedTest.characterPosition(tag: String, offset: Int): DpOffset {
    val node = findSemanticsNode(tag)
    val results = mutableListOf<TextLayoutResult>()
    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
    val layout = results.firstOrNull()
        ?: error("Node with testTag \"$tag\" has no GetTextLayoutResult action (not a text field?).")
    val caret = layout.getCursorRect(offset)
    val origin = node.positionInWindow
    return with(density) {
        DpOffset(
            (origin.x + caret.center.x).toDp(),
            (origin.y + caret.center.y).toDp(),
        )
    }
}

/** Taps character [offset] in the text field tagged [tag]. */
internal fun UIKitInstrumentedTest.tapCharacter(tag: String, offset: Int) {
    tap(characterPosition(tag, offset))
}

/** Long-presses character [offset] in the text field tagged [tag]. */
internal fun UIKitInstrumentedTest.longPressCharacter(tag: String, offset: Int) {
    val touch = touchDown(characterPosition(tag, offset))
    waitUntil("Selection loupe should appear after long press") {
        findFirstDescendant { it.isLoupeView } != null
    }
    touch.up()
}

/**
 * Taps character [offset] in the text field tagged [tag] [count] times in a row (e.g. 2 = double
 * tap to select a word, 3 = triple tap to select all). [count] must be >= 1.
 */
internal fun UIKitInstrumentedTest.multiTapCharacter(tag: String, offset: Int, count: Int) {
    require(count >= 1) { "count must be >= 1, was $count" }
    val point = characterPosition(tag, offset)
    repeat(count) { i ->
        if (i > 0) delay(50)
        tap(point)
    }
}
