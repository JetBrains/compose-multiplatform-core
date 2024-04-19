/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.ui.test

import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LifecycleInTestsTest {
    @Test
    fun lifecycleInComposeTest() = runComposeUiTest {
        var onCreatedEffectCalled = false
        var onResumeEffectCalled = false

        setContent {
            LifecycleEventEffect(Lifecycle.Event.ON_CREATE) {
                onCreatedEffectCalled = true
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                onResumeEffectCalled = true
            }
        }

        assertFalse(onCreatedEffectCalled)
        assertFalse(onResumeEffectCalled)

        sendLifecycleEvent(Lifecycle.Event.ON_START)
        assertTrue(onCreatedEffectCalled)
        assertFalse(onResumeEffectCalled)

        sendLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertTrue(onCreatedEffectCalled)
        assertTrue(onResumeEffectCalled)
    }
}