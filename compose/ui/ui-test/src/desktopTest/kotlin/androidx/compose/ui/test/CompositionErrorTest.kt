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

package androidx.compose.ui.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * An error thrown by the content fails the test with that error. The runner's recomposer is not
 * resilient: a resilient one would capture the error and reload the same content, which throws
 * the same way, without end.
 */
@OptIn(ExperimentalTestApi::class)
class CompositionErrorTest {
    @Test
    fun anErrorInTheFirstCompositionFailsTheTest() {
        var compositions = 0
        val error = assertFailsWith<IllegalStateException> {
            runComposeUiTest {
                setContent {
                    compositions++
                    error("broken content")
                }
            }
        }
        assertEquals("broken content", error.message)
        assertEquals(1, compositions, "the content must not be composed again after it failed")
    }

    @Test
    fun anErrorInARecompositionFailsTheTest() {
        var compositions = 0
        val error = assertFailsWith<IllegalStateException> {
            runComposeUiTest {
                var broken by mutableStateOf(false)
                setContent {
                    compositions++
                    if (broken) error("broken content")
                }
                broken = true
                waitForIdle()
            }
        }
        assertEquals("broken content", error.message)
        assertEquals(2, compositions, "the content must not be composed again after it failed")
    }
}
