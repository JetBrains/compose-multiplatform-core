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

import kotlin.js.JsAny
import kotlin.js.js
import org.w3c.dom.MediaQueryList

internal class TestFontSizeProbe(var fontSize: Float? = 16f) : WebFontSizeProbe {
    var disposeCount = 0
        private set
    var observeCount = 0
        private set
    var onObserve: () -> Unit = {}
    private var onChange: (() -> Unit)? = null

    override fun readFontSize(): Float? = fontSize

    override fun observe(onChange: () -> Unit) {
        observeCount++
        this.onChange = onChange
        onObserve()
    }

    fun changeTo(fontSize: Float?) {
        this.fontSize = fontSize
        // Keep the callback after disposal, to simulate an already queued browser notification.
        onChange?.invoke()
    }

    override fun dispose() {
        disposeCount++
    }
}

internal class TestMediaQuery(
    initialMatches: Boolean,
    legacy: Boolean,
    listenersSupported: Boolean
) {
    private val query = testMediaQueryObject(initialMatches, legacy, listenersSupported)
    val media: MediaQueryList get() = asMediaQueryList(query)
    val listenerCount: Int get() = query.listenerCount
    fun changeTo(matches: Boolean) = query.changeTo(matches)
    fun sendQueuedChange(matches: Boolean) = query.sendQueuedChange(matches)
}

/** An isolated MediaQueryList, never installed on window or any other browser global. */
internal fun testMediaQuery(
    initialMatches: Boolean = false,
    legacy: Boolean = false,
    listenersSupported: Boolean = true
) = TestMediaQuery(initialMatches, legacy, listenersSupported)

private external interface TestMediaQueryObject : JsAny {
    val listenerCount: Int
    fun changeTo(matches: Boolean)
    fun sendQueuedChange(matches: Boolean)
}

private fun asMediaQueryList(query: TestMediaQueryObject): MediaQueryList = js("query")

private fun testMediaQueryObject(
    initialMatches: Boolean,
    legacy: Boolean,
    listenersSupported: Boolean
): TestMediaQueryObject = js("""({
    matches: initialMatches,
    media: 'test',
    listeners: new Set(),
    removedListeners: new Set(),
    listenerCount: 0,
    addEventListener: !legacy && listenersSupported ? function(type, listener) {
        this.listeners.add(listener);
        this.listenerCount = this.listeners.size;
    } : undefined,
    removeEventListener: !legacy && listenersSupported ? function(type, listener) {
        this.listeners.delete(listener);
        this.removedListeners.add(listener);
        this.listenerCount = this.listeners.size;
    } : undefined,
    addListener: listenersSupported ? function(listener) {
        this.listeners.add(listener);
        this.listenerCount = this.listeners.size;
    } : undefined,
    removeListener: listenersSupported ? function(listener) {
        this.listeners.delete(listener);
        this.removedListeners.add(listener);
        this.listenerCount = this.listeners.size;
    } : undefined,
    changeTo: function(matches) {
        this.matches = matches;
        this.listeners.forEach(function(listener) { listener({ matches: matches }); });
    },
    sendQueuedChange: function(matches) {
        this.matches = matches;
        this.removedListeners.forEach(function(listener) { listener({ matches: matches }); });
    }
})""")
