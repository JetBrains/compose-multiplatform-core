package androidx.compose.ui.awt

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.TestNativeKeyEvent
import androidx.compose.ui.input.key.internal
import androidx.compose.ui.input.pointer.PointerEvent

/**
 * The original raw native event from AWT.
 *
 * Null if:
 * - The native event is sent by another framework (when Compose UI is embed into it)
 * - There is no native event (in tests, for example)
 * - The event is a synthetic move event sent by Compose on re-layout
 * - The event is a synthetic event sent by Compose to maintain a logically consistent stream of
 *   events. For example, if a native press event is received without a corresponding move event,
 *   Compose will synthesize a move event. That move event will have a null [awtEventOrNull].
 *
 * It is therefore recommended to always check for `null` when using this property.
 */
val PointerEvent.awtEventOrNull: java.awt.event.MouseEvent? get() {
    return nativeEvent as? java.awt.event.MouseEvent?
}

/**
 * The original raw native event from AWT.
 *
 * Null if:
 * - The native event is sent by another framework (when Compose UI is embed into it)
 * - There is no native event (in tests, for example)
 *
 * It is therefore recommended to always check for `null` when using this property.
 */
val KeyEvent.awtEventOrNull: java.awt.event.KeyEvent? get() {
    return internal.nativeEvent as? java.awt.event.KeyEvent?
}

private fun Char.isPrintable(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return (!Character.isISOControl(this)) &&
        this != java.awt.event.KeyEvent.CHAR_UNDEFINED &&
        block != null &&
        block != Character.UnicodeBlock.SPECIALS
}


val KeyEvent.isTypedEvent: Boolean
    get() =
        awtEventOrNull?.let {
            it.id == java.awt.event.KeyEvent.KEY_TYPED && it.keyChar.isPrintable()
        }
            ?: (internal.nativeEvent as? TestNativeKeyEvent)?.isTyped
            ?: false
