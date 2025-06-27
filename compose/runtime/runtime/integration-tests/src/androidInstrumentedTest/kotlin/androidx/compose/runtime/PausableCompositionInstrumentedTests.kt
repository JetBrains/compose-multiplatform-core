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

package androidx.compose.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class PausableCompositionInstrumentedTests {
    @get:Rule val rule = createComposeRule()

    @Test
    fun changeTheKeyUsedInPrecomposition() {
        val state = SubcomposeLayoutState()
        var precompositionKey by mutableStateOf("1")
        var addSlot by mutableStateOf(false)
        lateinit var holder: SaveableStateHolder

        rule.setContent {
            holder = rememberSaveableStateHolder()
            SubcomposeLayout(state) {
                if (addSlot) {
                    subcompose("measurepass") { holder.SaveableStateProvider("1") {} }
                }
                layout(10, 10) {}
            }
        }

        val precomposition =
            rule.runOnIdle {
                val precomposition =
                    state.createPausedPrecomposition("precomposition") {
                        holder.SaveableStateProvider(precompositionKey) {}
                    }

                while (!precomposition.isComplete) {
                    precomposition.resume { false }
                }

                precompositionKey = "2"

                addSlot = true

                precomposition
            }

        rule.runOnIdle {
            while (!precomposition.isComplete) {
                precomposition.resume { false }
            }
            precomposition.apply()
        }
    }

    @Test
    fun pausedPrecompositionIsNotRecomposedOnItsOwn() {
        val state = SubcomposeLayoutState()
        var key by mutableStateOf("1")
        val composed = mutableListOf<String>()

        rule.setContent { SubcomposeLayout(state) { layout(10, 10) {} } }

        val precomposition =
            rule.runOnIdle {
                val precomposition = state.createPausedPrecomposition(Unit) { composed.add(key) }
                while (!precomposition.isComplete) {
                    precomposition.resume { false }
                }

                assertThat(composed).isEqualTo(listOf("1"))
                composed.clear()

                // recompose just composed composable (which wasn't yet applied)
                key = "2"

                precomposition
            }

        rule.runOnIdle {
            // check that recomposition didn't happen on its own.
            // we don't expect that for non applied compositions.
            assertThat(composed).isEqualTo(emptyList<String>())

            while (!precomposition.isComplete) {
                precomposition.resume { false }
            }
            precomposition.apply()

            // recomposition happened
            assertThat(composed).isEqualTo(listOf("2"))
        }
    }
}
