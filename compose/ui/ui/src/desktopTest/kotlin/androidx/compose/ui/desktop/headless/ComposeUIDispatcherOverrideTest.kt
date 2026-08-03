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

import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.ComposeUIDispatcherOverride
import androidx.compose.ui.HeadlessTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(HeadlessTest::class)
class ComposeUIDispatcherOverrideTest {
    private object FakeMain : MainCoroutineDispatcher() {
        override val immediate: MainCoroutineDispatcher get() = this
        override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
    }

    @Test
    fun overrideTakesPrecedenceAndClearingRestoresThePlatformDispatcher() {
        try {
            ComposeUIDispatcherOverride = FakeMain
            assertSame(FakeMain, ComposeUIDispatcher)
        } finally {
            ComposeUIDispatcherOverride = null
        }
        // Constructing the platform dispatcher object is safe in a test JVM;
        // only dispatch() touches KDT natives.
        assertNotSame<CoroutineContext>(FakeMain, ComposeUIDispatcher)
    }
}
