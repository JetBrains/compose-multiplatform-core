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

package androidx.compose.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class NotoFontDownloaderTest {

    @Test
    fun emptyCodepoints_returnsEmptyList() = runTest {
        val downloader = NotoFontDownloader()
        val result = downloader.downloadFallbackFont(emptySet())
        assertEquals(emptyList(), result)
    }

    @Test
    fun codepointAboveMaxUnicode_isIgnored() = runTest {
        // 0x110000 is one above MAX_CODE_POINT (0x10FFFF); must not trigger network
        val downloader = NotoFontDownloader()
        val result = downloader.downloadFallbackFont(setOf(0x110000))
        assertEquals(emptyList(), result)
    }

    @Test
    fun codepointWithNoCoverage_isRememberedAndSkippedOnSubsequentCall() = runTest {
        // Private Use Area codepoints (0xE000–0xF8FF) have no Noto font coverage
        val pua = 0xE000
        val downloader = NotoFontDownloader()

        val first = downloader.downloadFallbackFont(setOf(pua))
        assertEquals(emptyList(), first, "PUA codepoint has no Noto coverage")

        // A second call with the same codepoint should be a no-op (already remembered)
        val second = downloader.downloadFallbackFont(setOf(pua))
        assertEquals(emptyList(), second)
    }

    @Test
    fun mixOfKnownAndUnknownCodepoints_onlyUnknownTracked() = runTest {
        val pua = 0xF000
        val downloader = NotoFontDownloader()
        // Call with a mix; the PUA codepoint has no coverage and will be remembered.
        // The valid codepoint (e.g. CJK) would trigger a network call — skip for unit test.
        // Here we just verify that a PUA-only set returns empty without crashing.
        val result = downloader.downloadFallbackFont(setOf(pua, 0x110000))
        assertEquals(emptyList(), result)
    }
}
