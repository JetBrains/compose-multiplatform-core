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

package androidx.compose.ui.text.platform

import androidx.compose.runtime.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypefaceDirtyTrackerTest {
    @Test
    fun repeatedFontResolutionDoesNotAccumulateReads() {
        val tracker = TypefaceDirtyTracker()
        val font = CountingState(Any())
        repeat(1000) { tracker.track(font) }

        assertEquals(1, font.readCount)
        font.readCount = 0
        assertFalse(tracker.isStaleResolvedFont)
        assertEquals(1, font.readCount)
    }

    @Test
    fun resolvingUpdatedFontPreservesStaleState() {
        val tracker = TypefaceDirtyTracker()
        val font = CountingState(Any())
        tracker.track(font)
        assertFalse(tracker.isStaleResolvedFont)

        font.currentValue = Any()
        tracker.track(font)

        assertTrue(tracker.isStaleResolvedFont)
    }

    private class CountingState(var currentValue: Any) : State<Any> {
        var readCount = 0

        override val value: Any
            get() {
                readCount++
                return currentValue
            }
    }
}
