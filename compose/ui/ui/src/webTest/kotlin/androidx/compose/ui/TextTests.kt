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

package androidx.compose.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.test.Test

class TextTests : OnCanvasTests {

    companion object {
        private fun assertApproximatelyEqual(
            expected: Float,
            actual: Float,
            tolerance: Float = 1f
        ) {
            if (abs(expected - actual) > tolerance) {
                throw AssertionError("Expected $expected but got $actual. Difference is more than the allowed delta $tolerance")
            }
        }
    }

    @Test
    // https://github.com/JetBrains/compose-multiplatform/issues/4078
    fun baselineShouldBeNotZero() = runApplicationTest {
        val headingOnPositioned = mutableStateOf(10f)
        val subtitleOnPositioned = mutableStateOf(10f)

        createComposeWindow {
            val density = LocalDensity.current.density
            Row {
                Text(
                    "Heading",
                    modifier = Modifier.alignByBaseline()
                        .onGloballyPositioned {
                            headingOnPositioned.value = it[FirstBaseline] / density
                        },
                    style = MaterialTheme.typography.h4
                )
                Text(
                    " — Subtitle",
                    modifier = Modifier.alignByBaseline()
                        .onGloballyPositioned {
                            subtitleOnPositioned.value = it[FirstBaseline] / density
                        },
                    style = MaterialTheme.typography.subtitle1
                )
            }
        }

        awaitIdle()

        assertApproximatelyEqual(29f, headingOnPositioned.value)
        assertApproximatelyEqual(17.5f, subtitleOnPositioned.value)
    }
}