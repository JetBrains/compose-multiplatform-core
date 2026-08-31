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

package androidx.compose.ui.window

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.sendFromScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.channels.Channel
import org.w3c.dom.get

/**
 * Verifies the disposal contract implemented in `ComposeWindow.web.kt`:
 * removing the `<compose-component>` custom element from the DOM must trigger
 * `ComposeWindow.dispose()` via the custom element's `disconnectedCallback`.
 *
 * See the WeakMap-based wiring in `ComposeViewport(...)` and the
 * `disconnectedCallback` in `composeComponentElementCtor(...)`.
 */
class ComposeWindowDisposeOnDomRemovalTest : OnCanvasTests {

    /**
     * Primary case: clearing the user-provided container removes the
     * `<compose-component>` child and must dispose the `ComposeWindow`.
     */
    @Test
    fun disposeIsCalledWhenComposeComponentIsDetachedFromDom() = runApplicationTest {
        val destroyEvents = Channel<Lifecycle.Event>(4)
        val onDisposeSignals = Channel<Unit>(1)

        createComposeWindow {
            DisposableEffect(Unit) {
                onDispose { onDisposeSignals.sendFromScope(Unit) }
            }
        }

        val window = assertNotNull(
            getComposeWindowOrNull(),
            "ComposeWindow was not created"
        )
        window.archComponentsOwner.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    destroyEvents.sendFromScope(event)
                }
            }
        )

        // Sanity: not disposed yet.
        assertNotEquals(
            Lifecycle.State.DESTROYED,
            window.archComponentsOwner.lifecycle.currentState,
            "ComposeWindow must not be disposed before DOM removal"
        )

        // Trigger DOM destruction WITHOUT calling dispose() manually.
        // replaceChildren() clears the container, which detaches the
        // <compose-component> child and fires disconnectedCallback.
        (getContainer() as CanReplaceChildren).replaceChildren()

        // Give the browser a frame to fire disconnectedCallback.
        awaitAnimationFrame()

        // Assert dispose actually happened:
        assertEquals(
            Lifecycle.Event.ON_DESTROY,
            destroyEvents.receiveWithTimeout(),
            "ON_DESTROY was not delivered after DOM removal"
        )

        // The composition itself was torn down:
        onDisposeSignals.receiveWithTimeout()

        assertEquals(
            Lifecycle.State.DESTROYED,
            window.archComponentsOwner.lifecycle.currentState
        )

        // Prevent afterTest from disposing an already-disposed window.
        clearComposeWindowReference()
    }

    /**
     * Removing only the `<compose-component>` element must dispose the window as well.
     * This proves that the mechanism relies on the custom element being disconnected,
     * not on the outer container being cleared.
     */
    @Test
    fun disposeIsCalledWhenPositioningContainerIsRemovedDirectly() = runApplicationTest {
        val destroyEvents = Channel<Lifecycle.Event>(1)

        createComposeWindow {}

        val window = assertNotNull(
            getComposeWindowOrNull(),
            "ComposeWindow was not created"
        )
        window.archComponentsOwner.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    destroyEvents.sendFromScope(event)
                }
            }
        )

        val positioningContainer = getContainer().children[0]
            ?: error("positioning container not found")
        (positioningContainer as RemovableElement).remove()

        awaitAnimationFrame()

        assertEquals(
            Lifecycle.Event.ON_DESTROY,
            destroyEvents.receiveWithTimeout(),
            "ON_DESTROY was not delivered after removing <compose-component>"
        )
        assertEquals(
            Lifecycle.State.DESTROYED,
            window.archComponentsOwner.lifecycle.currentState
        )

        clearComposeWindowReference()
    }

    /**
     * Removing the outer container (the whole subtree) from the document must also
     * trigger disposal, because the `<compose-component>` child gets disconnected.
     */
    @Test
    fun disposeIsCalledWhenContainerIsRemovedFromDocument() = runApplicationTest {
        val destroyEvents = Channel<Lifecycle.Event>(4)

        createComposeWindow {}

        val window = assertNotNull(
            getComposeWindowOrNull(),
            "ComposeWindow was not created"
        )
        window.archComponentsOwner.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    destroyEvents.sendFromScope(event)
                }
            }
        )

        // Note: the container itself is required by afterTest to reset the canvas,
        // so we re-attach an empty replacement with the same id right away.
        val container = getContainer()
        val parent = container.parentNode ?: error("container has no parent")
        (container as RemovableElement).remove()

        awaitAnimationFrame()

        assertEquals(
            Lifecycle.Event.ON_DESTROY,
            destroyEvents.receiveWithTimeout(),
            "ON_DESTROY was not delivered after removing the container from document"
        )
        assertEquals(
            Lifecycle.State.DESTROYED,
            window.archComponentsOwner.lifecycle.currentState
        )

        // Restore a fresh container so that afterTest's resetCanvas() can find it.
        val restored = kotlinx.browser.document.createElement("div")
        restored.id = "canvasApp"
        parent.appendChild(restored)
    }
}

private external interface RemovableElement {
    fun remove()
}

// See https://developer.mozilla.org/en-US/docs/Web/API/Element/replaceChildren
private external interface CanReplaceChildren {
    fun replaceChildren()
}
