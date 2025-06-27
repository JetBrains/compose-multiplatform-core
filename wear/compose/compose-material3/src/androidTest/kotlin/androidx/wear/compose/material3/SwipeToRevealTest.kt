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

package androidx.wear.compose.material3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.RevealValue
import androidx.wear.compose.foundation.rememberRevealState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class SwipeToRevealTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun swipeToReveal_undoPrimaryActionDisplayed_performsHaptics() {
        val results = mutableMapOf<HapticFeedbackType, Int>()
        val haptics = hapticFeedback(collectResultsFromHapticFeedback(results))
        val revealStateFlow = MutableStateFlow(RevealValue.RightRevealing)

        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                val revealState by revealStateFlow.collectAsStateWithLifecycle()

                Box(modifier = Modifier.fillMaxSize()) {
                    SwipeToReveal(
                        modifier = Modifier.testTag(TEST_TAG),
                        revealState =
                            rememberRevealState(
                                initialValue = revealState,
                                anchors = SwipeToRevealDefaults.anchors()
                            ),
                        actions = {
                            primaryAction(
                                {},
                                { Icon(Icons.Outlined.Close, contentDescription = "Clear") },
                                { Text("Clear") }
                            )
                        }
                    ) {
                        Button({}, Modifier.fillMaxWidth()) {
                            Text("This text should be partially visible.")
                        }
                    }
                }
            }
        }

        rule.runOnIdle { assertThat(results).isEmpty() }

        revealStateFlow.value = RevealValue.RightRevealed

        rule.runOnIdle {
            assertThat(results).hasSize(1)
            assertThat(results).containsKey(HapticFeedbackType.GestureThresholdActivate)
            assertThat(results[HapticFeedbackType.GestureThresholdActivate]).isEqualTo(1)
        }
    }
}
