package androidx.compose.ui.desktop.macos

import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.SessionMutex
import androidx.compose.ui.desktop.NativePlatformTextInputMethodRequest
import androidx.compose.ui.desktop.Scene
import androidx.compose.ui.desktop.TextInputSessionOwner
import androidx.compose.ui.desktop.logging.logger
import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.input.key.InternalKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.jetbrains.desktop.macos.EventHandlerResult
import org.jetbrains.desktop.macos.KeyCode
import org.jetbrains.desktop.macos.LogicalPoint
import org.jetbrains.desktop.macos.LogicalRect
import org.jetbrains.desktop.macos.LogicalSize
import org.jetbrains.desktop.macos.TextInputClient
import org.jetbrains.desktop.macos.Window

internal val imeLogger by lazy { logger<PlatformTextInputMethodRequestMacOs>() }

interface PlatformTextInputMethodRequestMacOs : NativePlatformTextInputMethodRequest {
    fun hasMarkedText(): Boolean
    fun markedRange(): TextRange?
    fun selectedRange(): TextRange
    fun insertText(text: String, replacementRange: TextRange?)
    fun doCommand(command: String): Boolean
    fun unmarkText()
    fun setMarkedText(text: String, selectedRange: TextRange?, replacementRange: TextRange?)

    data class StringAndRange(val text: String?, val actualRange: TextRange?)

    fun attributedStringForRange(range: TextRange): StringAndRange

    /**
     * @param rect is relative to Window
     */
    data class RectAndRange(val rect: DpRect, val actualRange: TextRange?)
    fun firstRectForCharacterRange(range: TextRange): RectAndRange

    /**
     * @param point is relative to Window
     */
    fun characterIndexForPoint(point: DpOffset): Long?
}

class PlatformTextInputSessionMacOs(
    coroutineScope: CoroutineScope,
    private val nativeWindow: Window,
    private val scene: Scene<*>,
    internal val density: () -> Density,
) : PlatformTextInputSessionScope<PlatformTextInputMethodRequestMacOs>,
    CoroutineScope by coroutineScope {

    @Volatile
    var currentTextInputClient: TextInputClient? = null

    override suspend fun startInputMethod(request: PlatformTextInputMethodRequestMacOs): Nothing {
        withContext(ComposeUIDispatcher.immediate) {
            val textInputClient = request.toTextInputClient(scene, nativeWindow)
            nativeWindow.setTextInputClient(textInputClient)
            currentTextInputClient = textInputClient
            imeLogger.trace { "setTextInputClient(request=$textInputClient)" }
        }
        try {
            awaitCancellation()
        } finally {
            withContext(ComposeUIDispatcher.immediate + NonCancellable) {
                nativeWindow.setTextInputClient(TextInputClient.Noop)
                nativeWindow.textInputContext.discardMarkedText()
                nativeWindow.textInputContext.invalidateCharacterCoordinates()
                currentTextInputClient = null
                imeLogger.trace { "setTextInputClient(TextInputClient.Noop)" }
            }
        }
    }
}

@OptIn(InternalComposeUiApi::class)
class MacOsTextInputSessionOwner(
    private val nativeWindow: Window,
    private val scene: Scene<*>,
    private val density: () -> Density,
) : TextInputSessionOwner {
    @OptIn(InternalComposeUiApi::class)
    private val textInputSessionMutex = SessionMutex<PlatformTextInputSessionMacOs>()

    @OptIn(InternalComposeUiApi::class)
    override suspend fun textInputSession(session: suspend PlatformTextInputSessionScope<*>.() -> Nothing): Nothing {
        textInputSessionMutex.withSessionCancellingPrevious(
            sessionInitializer = {
                PlatformTextInputSessionMacOs(
                    coroutineScope = it,
                    nativeWindow = nativeWindow,
                    scene = scene,
                    density = density,
                )
            },
            session,
        )
    }

    @OptIn(InternalComposeUiApi::class)
    override fun isTextInputSessionActive(): Boolean {
        // TODO [pavel.sergeev] make sure that when session is active the IME is already initialized
        return textInputSessionMutex.currentSession?.currentTextInputClient != null
    }

    override fun handleEventWithInputSession(keyEvent: KeyEvent): Boolean {
        return if (isTextInputSessionActive() && keyEvent.nativeKeyEvent() != null) {
            // TODO [pavel.sergeev] check that keyEvent is currentEvent
            imeLogger.trace { "handleEventWithInputSession(keyEvent=${keyEvent.nativeKeyEvent()})" }
            nativeWindow.textInputContext.handleCurrentEvent() == EventHandlerResult.Stop
        } else {
            false
        }
    }

    internal fun offerEventBeforeSendingToApplication(event: KeyEvent): Boolean {
        val textInputClient = textInputSessionMutex.currentSession?.currentTextInputClient
        val hasMarkedRange = textInputClient?.hasMarkedText() ?: false
        val isIMENavigationEvent = event.nativeKeyEvent()?.let(::isIMENavigationEvent) ?: false
        return if (isTextInputSessionActive() && hasMarkedRange || isIMENavigationEvent) {
            handleEventWithInputSession(event)
        } else {
            false
        }
    }

    private fun KeyEvent.nativeKeyEvent(): org.jetbrains.desktop.macos.Event.KeyDown? {
        return (this.nativeKeyEvent as InternalKeyEvent).nativeEvent as? org.jetbrains.desktop.macos.Event.KeyDown
    }

    private val imeNavigationKeys by lazy {
        setOf(
            KeyCode.LeftArrow,
            KeyCode.RightArrow,
            KeyCode.UpArrow,
            KeyCode.DownArrow,
            KeyCode.Escape,
            KeyCode.Return,
        )
    }

    private fun isIMENavigationEvent(event: org.jetbrains.desktop.macos.Event.KeyDown): Boolean {
        val noModifiers = !event.modifiers.control && !event.modifiers.command
        return noModifiers && imeNavigationKeys.contains(event.keyCode)
    }
}

private fun PlatformTextInputMethodRequestMacOs.toTextInputClient(
    scene: Scene<*>,
    nativeWindow: Window,
): TextInputClient {
    val request = this
    return object : TextInputClient {
        override fun hasMarkedText(): Boolean = scene.withPreparedMainThread {
            request.hasMarkedText().also { imeLogger.trace { "hasMarkedText() -> $it" } }
        }

        override fun markedRange(): org.jetbrains.desktop.macos.TextRange? =
            scene.withPreparedMainThread {
                request.markedRange()?.toKdtTextRange().also { imeLogger.trace { "markedRange() -> $it" } }
            }

        override fun selectedRange(): org.jetbrains.desktop.macos.TextRange =
            scene.withPreparedMainThread {
                request.selectedRange().toKdtTextRange().also { imeLogger.trace { "selectedRange() -> $it" } }
            }

        override fun insertText(
            text: String,
            replacementRange: org.jetbrains.desktop.macos.TextRange?,
        ) = scene.withPreparedMainThread {
            imeLogger.trace { "insertText(text='$text', replacementRange=$replacementRange)" }
            request.insertText(text, replacementRange?.toComposeTextRange())
        }

        override fun doCommand(command: String): Boolean = scene.withPreparedMainThread {
            request.doCommand(command).also { imeLogger.trace { "doCommand(command='$command') -> $it" } }
        }

        override fun unmarkText() = scene.withPreparedMainThread {
            imeLogger.trace { "unmarkText()" }
            request.unmarkText()
        }

        override fun setMarkedText(
            text: String,
            selectedRange: org.jetbrains.desktop.macos.TextRange?,
            replacementRange: org.jetbrains.desktop.macos.TextRange?,
        ) = scene.withPreparedMainThread {
            imeLogger.trace { "setMarkedText(text='$text', selectedRange=$selectedRange, replacementRange=$replacementRange)" }
            request.setMarkedText(
                text,
                selectedRange?.toComposeTextRange(),
                replacementRange?.toComposeTextRange(),
            )
        }

        override fun attributedStringForRange(range: org.jetbrains.desktop.macos.TextRange): TextInputClient.StringAndRange =
            scene.withPreparedMainThread {
                val result = request.attributedStringForRange(range.toComposeTextRange())
                TextInputClient.StringAndRange(result.text, result.actualRange?.toKdtTextRange()).also {
                    imeLogger.trace { "attributedStringForRange(range=$range) -> StringAndRange(text='${it.text}', actualRange=${it.actualRange})" }
                }
            }

        override fun firstRectForCharacterRange(range: org.jetbrains.desktop.macos.TextRange): TextInputClient.RectAndRange =
            scene.withPreparedMainThread {
                val result = request.firstRectForCharacterRange(range.toComposeTextRange())
                TextInputClient.RectAndRange(
                    result.rect.toScreenLogicalRect(nativeWindow.contentOrigin),
                    result.actualRange?.toKdtTextRange(),
                ).also {
                    imeLogger.trace { "firstRectForCharacterRange(range=$range) -> RectAndRange(rect=${it.rect}, actualRange=${it.actualRange})" }
                }
            }

        override fun characterIndexForPoint(point: LogicalPoint): Long? =
            scene.withPreparedMainThread {
                val pointInWindow = point - nativeWindow.contentOrigin
                request.characterIndexForPoint(pointInWindow.toDpOffset()).also {
                    imeLogger.trace { "characterIndexForPoint(point=$point) -> $it" }
                }
            }
    }
}

private fun TextRange.toKdtTextRange(): org.jetbrains.desktop.macos.TextRange =
    org.jetbrains.desktop.macos.TextRange(start.toLong(), length.toLong())

private fun org.jetbrains.desktop.macos.TextRange.toComposeTextRange(): TextRange {
    val end = location + length
    return TextRange(location.toInt(), end.toInt())
}

private fun DpRect.toScreenLogicalRect(contentOrigin: LogicalPoint): LogicalRect =
    LogicalRect(
        LogicalPoint(
            contentOrigin.x + left.value.toDouble(),
            contentOrigin.y + top.value.toDouble(),
        ),
        LogicalSize(
            (right - left).value.toDouble(),
            (bottom - top).value.toDouble(),
        ),
    )
