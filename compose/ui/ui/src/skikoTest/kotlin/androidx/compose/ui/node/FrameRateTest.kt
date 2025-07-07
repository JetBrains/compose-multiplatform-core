/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.ui.node

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.background
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.InternalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(InternalTestApi::class, ExperimentalTestApi::class)
class FrameRateVotingTest {

    @Test
    fun testNoPreferredFrameRate() = runFrameRateComposeUiTest {
        setContent {
            Button(
                onClick = {},
                modifier = Modifier.testTag(BUTTON_TAG)
            ) {
                Text(text = "Click Me")
            }
        }

        onNodeWithTag(BUTTON_TAG).performClick()

        waitForIdle()

        assertTrue(votedFrameRate.isNaN())
        assertEquals(FrameRateCategory.High.value, votedFrameRateCategory)
    }

    @Test
    fun testRemovingNodeWithPreferredFrameRate() = runFrameRateComposeUiTest {
        var showTestButton by mutableStateOf(true)

        setContent {
            Column {
                Button(
                    onClick = {},
                    modifier = Modifier.testTag(BUTTON_TAG)
                ) {
                    Text(text = "Click Me")
                }
                if (showTestButton) {
                    FrameRatePreferenceButton(60f, tag = SECOND_BUTTON_TAG)
                }
            }
        }

        onNodeWithTag(BUTTON_TAG).performClick()

        waitForIdle()

        assertTrue(votedFrameRate.isNaN())
        assertEquals(FrameRateCategory.High.value, votedFrameRateCategory)

        onNodeWithTag(SECOND_BUTTON_TAG).performClick()

        waitForIdle()

        assertEquals(60f, votedFrameRate)
        assertEquals(0f, votedFrameRateCategory)

        showTestButton = false

        waitForIdle()

        assertTrue(votedFrameRate.isNaN())
        assertEquals(FrameRateCategory.High.value, votedFrameRateCategory)
    }

    @Test
    fun testAddingNodeWithPreferredFrameRate() = runFrameRateComposeUiTest {
        var showTestButton by mutableStateOf(false)

        setContent {
            Column {
                Button(
                    onClick = {},
                    modifier = Modifier.testTag(BUTTON_TAG)
                ) {
                    Text(text = "Click Me")
                }
                if (showTestButton) {
                    FrameRatePreferenceButton(60f, tag = SECOND_BUTTON_TAG)
                }
            }
        }

        onNodeWithTag(BUTTON_TAG).performClick()

        waitForIdle()

        assertTrue(votedFrameRate.isNaN())
        assertEquals(FrameRateCategory.High.value, votedFrameRateCategory)

        showTestButton = true

        waitForIdle()

        assertTrue(votedFrameRate.isNaN())
        assertEquals(FrameRateCategory.High.value, votedFrameRateCategory)

        onNodeWithTag(SECOND_BUTTON_TAG).performClick()

        waitForIdle()

        assertEquals(60f, votedFrameRate)
        assertEquals(0f, votedFrameRateCategory)
    }

    @Test
    fun testPreferredFrameRateLow() = runFrameRateComposeUiTest {
        setContent {
            FrameRatePreferenceButton(5f)
        }

        onNodeWithTag(BUTTON_TAG).performClick()

        waitForIdle()

        assertEquals(5f, votedFrameRate)
        assertEquals(0f, votedFrameRateCategory)
    }

    @Test
    fun testPreferredFrameRateHigh() = runFrameRateComposeUiTest {
        setContent {
            FrameRatePreferenceButton(120f)
        }

        onNodeWithTag(BUTTON_TAG).performClick()

        waitForIdle()

        assertEquals(120f, votedFrameRate)
        assertEquals(0f, votedFrameRateCategory)
    }

    @Test
    fun testPreferredFrameRateCategory() = runFrameRateComposeUiTest {
        setContent {
            FrameRatePreferenceButton(FrameRateCategory.Normal.value)
        }

        onNodeWithTag(BUTTON_TAG).performClick()

        waitForIdle()

        assertTrue(votedFrameRate.isNaN())
        assertEquals(FrameRateCategory.Normal.value, votedFrameRateCategory)
    }

    @Test
    fun testChangingPreferredFrameRate() = runFrameRateComposeUiTest {
        setContent {
            Column {
                FrameRatePreferenceButton(20f, tag = BUTTON_TAG)
                FrameRatePreferenceButton(5f, tag = SECOND_BUTTON_TAG)
            }
        }

        onNodeWithTag(BUTTON_TAG).performClick()

        waitForIdle()

        assertEquals(20f, votedFrameRate)
        assertEquals(0f, votedFrameRateCategory)

        onNodeWithTag(SECOND_BUTTON_TAG).performClick()

        waitForIdle()

        assertEquals(5f, votedFrameRate)
        assertEquals(0f, votedFrameRateCategory)
    }

    @Test
    fun testChangingPreferredFrameRateCategory() = runFrameRateComposeUiTest {
        setContent {
            Column {
                FrameRatePreferenceButton(FrameRateCategory.Normal.value, tag = BUTTON_TAG)
                FrameRatePreferenceButton(FrameRateCategory.High.value, tag = SECOND_BUTTON_TAG)
            }
        }

        onNodeWithTag(BUTTON_TAG).performClick()

        waitForIdle()

        assertTrue(votedFrameRate.isNaN())
        assertEquals(FrameRateCategory.Normal.value, votedFrameRateCategory)

        onNodeWithTag(SECOND_BUTTON_TAG).performClick()

        waitForIdle()

        assertTrue(votedFrameRate.isNaN())
        assertEquals(FrameRateCategory.High.value, votedFrameRateCategory)
    }

    @Test
    fun testChangingPreferredFrameRateCategoryAndFrameRate() = runFrameRateComposeUiTest {
        setContent {
            Column {
                FrameRatePreferenceButton(FrameRateCategory.Normal.value, tag = BUTTON_TAG)
                FrameRatePreferenceButton(5f, tag = SECOND_BUTTON_TAG)
            }
        }

        onNodeWithTag(BUTTON_TAG).performClick()

        waitForIdle()

        assertTrue(votedFrameRate.isNaN())
        assertEquals(FrameRateCategory.Normal.value, votedFrameRateCategory)

        onNodeWithTag(SECOND_BUTTON_TAG).performClick()

        waitForIdle()

        assertEquals(5f, votedFrameRate)
        assertEquals(0f, votedFrameRateCategory)
    }

    @Test
    fun testNestedFrameRatePreferences() = runFrameRateComposeUiTest {
        setContent {
            NestedFrameRatePreferencesUI(5f, 60f)
        }

        onNodeWithTag(BUTTON_TAG).performClick()

        waitUntil(timeoutMillis = 150) {
            votedFrameRate == 60f && votedFrameRateCategory == 0f
        }
    }

    @Test
    fun testNestedFrameRateCategoryPreferences() = runFrameRateComposeUiTest {
        setContent {
            NestedFrameRatePreferencesUI(FrameRateCategory.Default.value, FrameRateCategory.Normal.value)
        }

        onNodeWithTag(BUTTON_TAG).performClick()

        waitUntil(timeoutMillis = 150) {
            votedFrameRate.isNaN() && votedFrameRateCategory == FrameRateCategory.Normal.value
        }
    }

    @Test
    fun testNestedFrameRateCategoryDefaultPreferences() = runFrameRateComposeUiTest {
        setContent {
            NestedFrameRatePreferencesUI(FrameRateCategory.Default.value, FrameRateCategory.Default.value)
        }

        onNodeWithTag(BUTTON_TAG).performClick()

        waitUntil(timeoutMillis = 150) {
            votedFrameRate.isNaN() && votedFrameRateCategory.isNaN()
        }
    }

    @Test
    fun testNestedFrameRateAndFrameRateCategoryPreferences() = runFrameRateComposeUiTest {
        setContent {
            NestedFrameRatePreferencesUI(5f, FrameRateCategory.Normal.value)
        }

        onNodeWithTag(BUTTON_TAG).performClick()

        waitUntil(timeoutMillis = 150) {
            votedFrameRate == 5f && votedFrameRateCategory == FrameRateCategory.Normal.value
        }
    }
}

private val BUTTON_TAG = "buttonTag"
private val SECOND_BUTTON_TAG = "secondButtonTag"

@Composable
private fun FrameRatePreferenceButton(frameRate: Float, tag: String = BUTTON_TAG) {
    var targetAlpha by remember { mutableFloatStateOf(1f) }
    val alpha by
    animateFloatAsState(targetValue = targetAlpha, animationSpec = tween(durationMillis = 100))

    Button(
        onClick = { targetAlpha = if (targetAlpha == 1f) 0.2f else 1f },
        modifier = Modifier.testTag(tag)
    ) {
        Text(
            text = "Click Me for alpha change $frameRate",
            color = LocalContentColor.current.copy(alpha = alpha), // Adjust text alpha
            modifier = Modifier.preferredFrameRate(frameRate),
        )
    }
}

@Composable
fun NestedFrameRatePreferencesUI(firstFrameRate: Float, secondFrameRate: Float) {
    var targetAlpha by remember { mutableFloatStateOf(1f) }
    val alpha by
    animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 100),
    )

    Button(
        onClick = { targetAlpha = if (targetAlpha == 1f) 0.2f else 1f },
        modifier =
            Modifier.testTag(BUTTON_TAG)
                .preferredFrameRate(secondFrameRate)
                .background(LocalContentColor.current.copy(alpha = alpha)),
    ) {
        Text(
            text = "Click Me for alpha change $firstFrameRate",
            color = LocalContentColor.current.copy(alpha = alpha), // Adjust text alpha
            modifier = Modifier.preferredFrameRate(firstFrameRate),
        )
    }
}

@ExperimentalTestApi
private fun runFrameRateComposeUiTest(
    block: suspend FrameRateComposeUiTest.() -> Unit
) {
    kotlinx.coroutines.test.runTest {
        with(
            FrameRateComposeUiTest()
        ) {
            runTest { block() }
        }
    }
}

@ExperimentalTestApi
private class FrameRateComposeUiTest(
    width: Int = 1024,
    height: Int = 768,
    effectContext: CoroutineContext = EmptyCoroutineContext,
    runTestContext: CoroutineContext = EmptyCoroutineContext,
    testTimeout: Duration = Duration.INFINITE,
    density: Density = Density(1f),
) : SkikoComposeUiTest(
    width = width,
    height = height,
    effectContext = effectContext,
    runTestContext = runTestContext,
    testTimeout = testTimeout,
    density = density,
) {
    var votedFrameRate = Float.NaN
    var votedFrameRateCategory = 0f

    override val platformContext: PlatformContext get() = object: PlatformContext by super.platformContext {
        override fun voteFrameRate(frameRate: Float, frameRateCategory: Float) {
            votedFrameRate = frameRate
            votedFrameRateCategory = frameRateCategory
        }
    }
}