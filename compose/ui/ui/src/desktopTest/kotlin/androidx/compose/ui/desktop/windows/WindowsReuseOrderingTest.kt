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

package androidx.compose.ui.desktop.windows

import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.ParkedWindowResources
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * State-machine coverage for the window-reuse ordering ([reuseParkedResources]) against a fake
 * `ReusableNativeWindowResources` — the real WindowsWindow ctor needs a native HWND, so this covers
 * the native-free orchestration that WindowsApplication.reuseWindow delegates to. The key assertion
 * is dispose-before-take: the outgoing wrapper must be disposed while its entry is STILL parked, so
 * WindowsWindow.dispose()'s peekContains gate skips destroying the HWND that is about to be reused.
 */
@Category(HeadlessTest::class)
class WindowsReuseOrderingTest {

    private class FakeResources(val label: String)

    @Test
    fun disposesOutgoingWhileStillParkedThenTakesAndBuilds() {
        val warnings = mutableListOf<String>()
        val registry = ParkedWindowResources<FakeResources>(warn = { warnings += it })
        val id = LightweightWindowId(1)
        val parked = FakeResources("hwnd")
        registry.park(id, parked)

        val events = mutableListOf<String>()
        var parkedDuringDispose: Boolean? = null
        var built: FakeResources? = null

        val result = registry.reuseParkedResources(
            id,
            onMissing = { events += "missing" },
            disposeOutgoing = {
                events += "dispose"
                // WindowsWindow.dispose() consults peekContains to decide whether to destroy the
                // HWND; it MUST still be parked here so the native window survives for reuse.
                parkedDuringDispose = registry.peekContains(id)
            },
            build = { resources ->
                events += "build"
                built = resources
                "window-over-${resources.label}"
            },
        )

        assertEquals(listOf("dispose", "build"), events, "dispose must run strictly before build")
        assertEquals(true, parkedDuringDispose, "entry must still be parked when the old wrapper disposes")
        assertSame(parked, built, "the parked resources must feed the new wrapper")
        assertEquals("window-over-hwnd", result)
        assertFalse(registry.peekContains(id), "the entry must be taken by the time reuse returns")
        assertTrue(warnings.isEmpty(), "a successful reuse must not warn: $warnings")
    }

    @Test
    fun missingIdReportsOnMissingAndSkipsDisposeAndBuild() {
        val registry = ParkedWindowResources<FakeResources>(warn = {})
        val events = mutableListOf<String>()

        val result: String? = registry.reuseParkedResources(
            LightweightWindowId(2),
            onMissing = { events += "missing" },
            disposeOutgoing = { events += "dispose" },
            build = {
                events += "build"
                "unreachable"
            },
        )

        assertNull(result, "reuse must return null when nothing is parked for the id")
        assertEquals(listOf("missing"), events, "no wrapper may be disposed or built on a miss")
    }
}
