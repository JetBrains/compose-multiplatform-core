package androidx.compose.ui.desktop.macos

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.desktop.Scene
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.text.substring
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.desktop.logging.logger
import org.jetbrains.desktop.macos.LogicalPoint
import org.jetbrains.desktop.macos.LogicalRect
import org.jetbrains.desktop.macos.LogicalSize
import org.jetbrains.desktop.macos.TextInputClient
import org.jetbrains.desktop.macos.TextRange

private val logger = logger<ComposeTextInputClient>()

internal class ComposeTextInputClient(
    private val platformTextInputMethodRequest: PlatformTextInputMethodRequest,
    private val scene: Scene<*>,
    private val density: () -> Density,
    private val rootToScreen: (LogicalPoint) -> LogicalPoint,
) :
    TextInputClient {

    // When the IME consumed an event without invoking any mutation callback
    // (insertText/setMarkedText/unmarkText), it's managing a system popup like
    // the diacritics character picker. Track this so that later events
    // (like Escape) can be routed through the IME before the Compose key dispatch.
    var silentlyConsumedEvent: Boolean = false
        private set

    // Armed before handleCurrentEvent(); cleared by mutation callbacks.
    private var awaitingMutationCallback: Boolean = false

    fun armSilentConsumptionDetection() {
        awaitingMutationCallback = true
    }

    fun evaluateSilentConsumption(consumed: Boolean) {
        if (consumed) {
            // consumed + no callback → popup is silently consuming (e.g., diacritics picker)
            // consumed + callback fired → normal consumption, not silent
            silentlyConsumedEvent = awaitingMutationCallback
        }
        // When not consumed: leave silentlyConsumedEvent unchanged.
        // A non-consumed event (e.g., KeyUp passing through) doesn't tell us
        // whether the popup is still active.
        awaitingMutationCallback = false
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun hasMarkedText(): Boolean = scene.withPreparedMainThread {
        val composition = platformTextInputMethodRequest.state.composition
        val result = composition != null
        logger.debug { "hasMarkedText() -> composition=$composition, result=$result" }
        result
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun markedRange(): TextRange? = scene.withPreparedMainThread {
        val composition = platformTextInputMethodRequest.state.composition
        val result = composition?.toKdtTextRange()
        logger.debug { "markedRange() -> composition=$composition, result=$result (location=${result?.location}, length=${result?.length})" }
        result
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun selectedRange(): TextRange = scene.withPreparedMainThread {
        val selection = platformTextInputMethodRequest.state.selection
        val result = selection.toKdtTextRange()
        logger.debug { "selectedRange() -> selection=$selection, result=$result (location=${result.location}, length=${result.length})" }
        result
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun insertText(
        text: String,
        replacementRange: TextRange?,
    ) {
        awaitingMutationCallback = false
        silentlyConsumedEvent = false
        logger.debug {
            "insertText(text=\"$text\" [${
                text.codePoints().toArray().joinToString { String.format("U+%04X", it) }
            }], " +
                "replacementRange=${replacementRange?.let { "(loc=${it.location}, len=${it.length})" } ?: "null"})"
        }
        scene.withPreparedMainThread {
            logger.debug {
                val stateBefore = platformTextInputMethodRequest.state.let {
                    "selection=${it.selection}, composition=${it.composition}, length=${it.length}"
                }
                "  state BEFORE: $stateBefore"
            }

            platformTextInputMethodRequest.editText {
                if (replacementRange != null) {
                    val effectiveReplacementRangeStart = replacementRange.location
                    check(effectiveReplacementRangeStart >= Int.MIN_VALUE && effectiveReplacementRangeStart <= Int.MAX_VALUE) {
                        "effectiveReplacementRangeStart is out of Int range: $effectiveReplacementRangeStart"
                    }
                    val effectiveReplacementRangeEnd =
                        effectiveReplacementRangeStart + replacementRange.length
                    check(effectiveReplacementRangeEnd >= Int.MIN_VALUE && effectiveReplacementRangeEnd <= Int.MAX_VALUE) {
                        "effectiveReplacementRangeEnd is out of Int range: $effectiveReplacementRangeEnd"
                    }
                    val effectiveReplacementRange = androidx.compose.ui.text.TextRange(
                        effectiveReplacementRangeStart.toInt(),
                        effectiveReplacementRangeEnd.toInt(),
                    )
                    logger.debug { "  calling setSelection($effectiveReplacementRange) for replacementRange" }
                    setSelection(effectiveReplacementRange)
                }
                logger.debug { "  calling commitText(\"$text\", 1)" }
                commitText(text, 1)
            }
            logger.debug {
                val stateAfter = platformTextInputMethodRequest.state.let {
                    "selection=${it.selection}, composition=${it.composition}, length=${it.length}"
                }
                "  state AFTER: $stateAfter"
            }
        }
    }

    override fun doCommand(command: String): Boolean {
        logger.debug { "doCommand(command=\"$command\") -> returning false (NOT IMPLEMENTED)" }
        // todo[unterhofer] Implement these
        return false
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun unmarkText() {
        awaitingMutationCallback = false
        silentlyConsumedEvent = false
        logger.debug { "unmarkText()" }
        scene.withPreparedMainThread {
            logger.debug {
                val stateBefore = platformTextInputMethodRequest.state.let {
                    "selection=${it.selection}, composition=${it.composition}, length=${it.length}"
                }
                "  state BEFORE: $stateBefore"
            }

            platformTextInputMethodRequest.editText {
                finishComposingText()
            }
            logger.debug {
                val stateAfter = platformTextInputMethodRequest.state.let {
                    "selection=${it.selection}, composition=${it.composition}, length=${it.length}"
                }
                "  state AFTER: $stateAfter"
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun setMarkedText(
        text: String,
        selectedRange: TextRange?,
        replacementRange: TextRange?,
    ) {
        awaitingMutationCallback = false
        silentlyConsumedEvent = false
        logger.debug {
            "setMarkedText(text=\"$text\" [${
                text.codePoints().toArray().joinToString { String.format("U+%04X", it) }
            }], " +
                "selectedRange=${selectedRange?.let { "(loc=${it.location}, len=${it.length})" } ?: "null"}, " +
                "replacementRange=${replacementRange?.let { "(loc=${it.location}, len=${it.length})" } ?: "null"})"
        }
        scene.withPreparedMainThread {
            val stateBefore = platformTextInputMethodRequest.state.let {
                "selection=${it.selection}, composition=${it.composition}, length=${it.length}"
            }
            logger.debug { "  state BEFORE: $stateBefore" }

            platformTextInputMethodRequest.editText {
                val potentialReplacementRange =
                    platformTextInputMethodRequest.state.let { it.composition ?: it.selection }
                logger.debug { "  potentialReplacementRange=$potentialReplacementRange (from ${if (platformTextInputMethodRequest.state.composition != null) "composition" else "selection"})" }

                val effectiveReplacementRangeStart = replacementRange?.location
                    ?: potentialReplacementRange.start.toLong()
                check(effectiveReplacementRangeStart >= Int.MIN_VALUE && effectiveReplacementRangeStart <= Int.MAX_VALUE) {
                    "effectiveReplacementRangeStart is out of Int range: $effectiveReplacementRangeStart"
                }
                val effectiveReplacementRangeEnd = replacementRange?.let {
                    it.location + it.length
                } ?: potentialReplacementRange.end.toLong()
                check(effectiveReplacementRangeEnd >= Int.MIN_VALUE && effectiveReplacementRangeEnd <= Int.MAX_VALUE) {
                    "effectiveReplacementRangeEnd is out of Int range: $effectiveReplacementRangeEnd"
                }
                val effectiveReplacementRange = androidx.compose.ui.text.TextRange(
                    effectiveReplacementRangeStart.toInt(),
                    effectiveReplacementRangeEnd.toInt(),
                )
                logger.debug { "  effectiveReplacementRange=$effectiveReplacementRange (start=${effectiveReplacementRange.start}, end=${effectiveReplacementRange.end})" }
                logger.debug { "  calling setComposition($effectiveReplacementRange)" }
                setComposition(effectiveReplacementRange)

                logger.debug { "  calling setComposingText(\"$text\", 1)" }
                setComposingText(text, 1)

                selectedRange?.let {
                    val effectiveSelectedRangeStart = effectiveReplacementRangeStart + it.location
                    check(effectiveSelectedRangeStart >= Int.MIN_VALUE && effectiveSelectedRangeStart <= Int.MAX_VALUE) {
                        "effectiveSelectedRangeStart is out of Int range: $effectiveSelectedRangeStart"
                    }
                    val effectiveSelectedRangeEnd = effectiveSelectedRangeStart + it.length
                    check(effectiveSelectedRangeEnd >= Int.MIN_VALUE && effectiveSelectedRangeEnd <= Int.MAX_VALUE) {
                        "effectiveSelectedRangeEnd is out of Int range: $effectiveSelectedRangeEnd"
                    }
                    val effectiveSelection = androidx.compose.ui.text.TextRange(
                        effectiveSelectedRangeStart.toInt(),
                        effectiveSelectedRangeEnd.toInt(),
                    )
                    logger.debug { "  calling setSelection($effectiveSelection) for selectedRange" }
                    setSelection(effectiveSelection)
                }
            }

            val stateAfter = platformTextInputMethodRequest.state.let {
                "selection=${it.selection}, composition=${it.composition}, length=${it.length}"
            }
            logger.debug { "  state AFTER: $stateAfter" }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun attributedStringForRange(range: TextRange): TextInputClient.StringAndRange {
        logger.debug { "attributedStringForRange(range=(loc=${range.location}, len=${range.length}))" }
        return scene.withPreparedMainThread {
            val text = platformTextInputMethodRequest.state
            when {
                range.location >= text.length -> {
                    logger.debug { "  range.location(${range.location}) >= text.length(${text.length}), returning null" }
                    TextInputClient.StringAndRange(null, null)
                }
                else -> {
                    val requestedEnd = (range.location + range.length).toInt()
                    val adjustedEnd = requestedEnd.coerceAtMost(text.length)
                    val adjustedTextRange = androidx.compose.ui.text.TextRange(
                        range.location.toInt(),
                        adjustedEnd,
                    )
                    val substring = text.substring(adjustedTextRange)
                    val actualRange =
                        adjustedTextRange.takeIf { adjustedEnd != requestedEnd }?.let {
                            TextRange(it.start.toLong(), it.length.toLong())
                        }
                    logger.debug { "  returning string=\"$substring\", actualRange=${actualRange?.let { "(loc=${it.location}, len=${it.length})" } ?: "null"}" }
                    TextInputClient.StringAndRange(
                        substring,
                        actualRange,
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun firstRectForCharacterRange(range: TextRange): TextInputClient.RectAndRange {
        logger.debug { "firstRectForCharacterRange(range=(loc=${range.location}, len=${range.length}))" }
        return scene.withPreparedMainThread {
            val (firstTextRange, firstRect) = platformTextInputMethodRequest.firstTextRangeAndRectInRoot(
                range.toComposeTextRange(),
            )
            val result = TextInputClient.RectAndRange(
                density().run {
                    LogicalRect(
                        rootToScreen(firstRect.topLeft.toKdtLogicalPoint()),
                        firstRect.size.toKdtLogicalSize(),
                    )
                },
                firstTextRange.toKdtTextRange().takeIf { it != range },
            )
            logger.debug { "  returning rect=${result.rect}, actualRange=${result.actualRange?.let { "(loc=${it.location}, len=${it.length})" } ?: "null"}" }
            result
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun characterIndexForPoint(point: LogicalPoint): Long? {
        logger.debug { "characterIndexForPoint(point=$point)" }
        return scene.withPreparedMainThread {
            val offset = point.toDpOffset().toOffset(density())
            val result =
                platformTextInputMethodRequest.characterIndexAtOffsetInRoot(offset).toLong()
            logger.debug { "  returning $result" }
            result
        }
    }
}

private fun TextRange.toComposeTextRange(): androidx.compose.ui.text.TextRange {
    check(location >= Int.MIN_VALUE && location <= Int.MAX_VALUE) { "location is out of Int range: $location" }
    val end = location + length
    check(end <= Int.MAX_VALUE - location) { "end is out of Int range: $end" }
    return androidx.compose.ui.text.TextRange(location.toInt(), end.toInt())
}

private fun androidx.compose.ui.text.TextRange.toKdtTextRange(): TextRange =
    TextRange(start.toLong(), length.toLong())

context(density: Density)
private fun androidx.compose.ui.geometry.Offset.toKdtLogicalPoint(): LogicalPoint = density.run {
    LogicalPoint(x.toDp().value.toDouble(), y.toDp().value.toDouble())
}

context(density: Density)
private fun androidx.compose.ui.geometry.Size.toKdtLogicalSize(): LogicalSize = density.run {
    LogicalSize(width.toDp().value.toDouble(), height.toDp().value.toDouble())
}

context(density: Density)
private fun androidx.compose.ui.geometry.Rect.toKdtLogicalRect(): LogicalRect = density.run {
    LogicalRect(topLeft.toKdtLogicalPoint(), size.toKdtLogicalSize())
}
