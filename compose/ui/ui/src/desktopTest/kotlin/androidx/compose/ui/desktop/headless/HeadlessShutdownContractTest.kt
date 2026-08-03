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

package androidx.compose.ui.desktop.headless

import androidx.compose.ui.HeadlessTest
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * stopAndJoin() poisons HeadlessApplication for the whole JVM (Noria contract), so this class
 * must NOT be mixed with other headless tests in one JVM. The desktopHeadlessTest task forks per
 * test CLASS via maxParallelForks/forkEvery — verify `forkEvery = 1` is set for this task in
 * compose/ui/ui/build.gradle (add `forkEvery = 1` to the desktopHeadlessTest registration if
 * missing) so every headless test class gets a fresh JVM.
 */
@Category(HeadlessTest::class)
class HeadlessShutdownContractTest {
    @Test
    fun stopAndJoinPoisonsTheProcessAndReportsPendingTasks() = runBlocking<Unit> {
        val app = HeadlessApplication.initialize(System.getProperty("java.io.tmpdir"))
        app.stopAndJoin()
        val failure = assertFailsWith<IllegalStateException> { HeadlessApplication.current }
        assertTrue(failure.message!!.contains("last closed event loop had 0 pending tasks"), failure.message)
        assertFailsWith<IllegalStateException> {
            HeadlessApplication.initialize(System.getProperty("java.io.tmpdir"))
        }
    }
}
