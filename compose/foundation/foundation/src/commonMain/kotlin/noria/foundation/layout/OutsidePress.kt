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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.round
import androidx.compose.ui.util.fastFirstOrNull

/** What an [OutsidePressListener] does when a press lands on its anchor. */
enum class AnchorPressPolicy {
    /** Report the press. The anchor also gets it. */
    Notify,

    /** Report the press and consume it. The anchor does not get it. */
    NotifyAndConsume,

    /** Do not report the press. */
    Ignore,
}

/**
 * One overlay that waits for a press outside itself.
 *
 * Every bound is in the coordinates of the overlay host.
 *
 * @param overlayBounds the bounds of the overlay. A press inside them is not an outside press.
 *   Return [IntRect.Zero] if the overlay excludes no area. Return `null` while the overlay has no
 *   bounds yet, and the registry then reports no press at all.
 * @param anchorBounds the bounds of the anchor.
 * @param anchorPressPolicy what to do when the press lands on the anchor.
 * @param onOutsidePress receives the position of the press.
 */
internal class OutsidePressListener(
    val overlayBounds: () -> IntRect?,
    val anchorBounds: () -> IntRect,
    val anchorPressPolicy: AnchorPressPolicy,
    val onOutsidePress: (IntOffset) -> Unit,
)

/**
 * The outside press listeners of one [OverlayHost].
 */
internal class OutsidePressRegistry {
    private val listeners = mutableListOf<OutsidePressListener>()

    fun register(listener: OutsidePressListener) {
        listeners.add(listener)
    }

    fun unregister(listener: OutsidePressListener) {
        listeners.remove(listener)
    }

    /**
     * Reports [position] to every listener that reads it as an outside press.
     *
     * @return `true` if the press must be consumed.
     */
    fun dispatch(position: IntOffset): Boolean {
        if (listeners.isEmpty()) {
            return false
        }
        var consume = false
        // A listener usually closes its overlay, and that unregisters the listener. Iterate over a
        // copy.
        for (listener in listeners.toList()) {
            val overlayBounds = listener.overlayBounds() ?: continue
            if (overlayBounds.contains(position)) {
                continue
            }
            if (listener.anchorBounds().contains(position)) {
                when (listener.anchorPressPolicy) {
                    AnchorPressPolicy.Ignore -> continue
                    AnchorPressPolicy.NotifyAndConsume -> consume = true
                    AnchorPressPolicy.Notify -> {}
                }
            }
            listener.onOutsidePress(position)
        }
        return consume
    }
}

/**
 * Reports a press outside this overlay while the caller composes.
 *
 * The listener joins the registry of the host [key], which is always an ancestor of the caller.
 */
@Composable
fun outsidePressListener(
    key: OverlayHostKey,
    overlayBounds: () -> IntRect?,
    anchorBounds: () -> IntRect,
    anchorPressPolicy: AnchorPressPolicy,
    onOutsidePress: (IntOffset) -> Unit,
) {
    val registry = key.current.outsidePressRegistry
    val currentOverlayBounds by rememberUpdatedState(overlayBounds)
    val currentAnchorBounds by rememberUpdatedState(anchorBounds)
    val currentOnOutsidePress by rememberUpdatedState(onOutsidePress)
    DisposableEffect(registry, anchorPressPolicy) {
        val listener =
            OutsidePressListener(
                overlayBounds = { currentOverlayBounds() },
                anchorBounds = { currentAnchorBounds() },
                anchorPressPolicy = anchorPressPolicy,
                onOutsidePress = { currentOnOutsidePress(it) },
            )
        registry.register(listener)
        onDispose { registry.unregister(listener) }
    }
}

/**
 * Observes a press for the [registry].
 *
 * The modifier goes on the overlay host, so the node is an ancestor of the content and of every
 * overlay. An ancestor never hides a descendant from the hit test, so the content and the overlays
 * keep every press.
 *
 * The node reads the press on [PointerEventPass.Initial], before the anchor can act on it, and it
 * ignores an earlier consumption. It consumes only when a listener asks for it.
 */
internal fun Modifier.outsidePressObserver(registry: OutsidePressRegistry): Modifier =
    pointerInput(registry) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Press) {
                    continue
                }
                val press =
                    event.changes.fastFirstOrNull { it.changedToDownIgnoreConsumed() } ?: continue
                if (registry.dispatch(press.position.round())) {
                    press.consume()
                }
            }
        }
    }
