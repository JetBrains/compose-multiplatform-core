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

package androidx.compose.ui.semantics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.elementOf
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@SmallTest
@RunWith(Parameterized::class)
class SemanticsModifierNodeTest(private val precomputedSemantics: Boolean) {
    @get:Rule val rule = createComposeRule()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "pre-computed semantics = {0}")
        fun initParameters() = listOf(false, true)
    }

    @Before
    fun setup() {
        @OptIn(ExperimentalComposeUiApi::class)
        ComposeUiFlags.isSemanticAutofillEnabled = precomputedSemantics
    }

    @Test
    fun applySemantics_calledWhenSemanticsIsRead() {
        // Arrange.
        var applySemanticsInvoked = false
        rule.setContent {
            Box(
                Modifier.elementOf(
                    TestSemanticsModifier {
                        testTag = "TestTag"
                        applySemanticsInvoked = true
                    }
                )
            )
        }

        // Act.
        rule.onNodeWithTag("TestTag").fetchSemanticsNode()

        // Assert.
        rule.runOnIdle { assertThat(applySemanticsInvoked).isTrue() }
    }

    @Test
    fun invalidateSemantics_applySemanticsIsCalled() {
        // Arrange.
        var applySemanticsInvoked: Boolean
        val semanticsModifier = TestSemanticsModifier {
            testTag = "TestTag"
            applySemanticsInvoked = true
        }
        rule.setContent { Box(Modifier.elementOf(semanticsModifier)) }
        applySemanticsInvoked = false

        // Act.
        rule.runOnIdle { semanticsModifier.invalidateSemantics() }

        // Assert - Apply semantics is not called when we calculate semantics lazily.
        if (precomputedSemantics) {
            assertThat(applySemanticsInvoked).isTrue()
        } else {
            assertThat(applySemanticsInvoked).isFalse()
        }
    }

    @Test
    fun invalidateSemantics_applySemanticsNotCalledAgain_whenSemanticsConfigurationIsRead() {
        // Arrange.
        lateinit var rootForTest: RootForTest
        var applySemanticsInvoked = false
        var invocationCount = 0
        val semanticsModifier = TestSemanticsModifier {
            testTag = "TestTag"
            text = AnnotatedString("Text ${invocationCount++}")
            applySemanticsInvoked = true
        }
        rule.setContent {
            rootForTest = LocalView.current as RootForTest
            Box(Modifier.elementOf(semanticsModifier))
        }
        val semanticsId = rule.onNodeWithTag("TestTag").semanticsId()
        rule.runOnIdle {
            semanticsModifier.invalidateSemantics()
            applySemanticsInvoked = false
        }

        // Act.
        val semanticsInfo = checkNotNull(rootForTest.semanticsOwner[semanticsId])
        val semanticsConfiguration = semanticsInfo.semanticsConfiguration

        // Assert - Configuration recalculated when we calculate semantics lazily.
        if (precomputedSemantics) {
            assertThat(applySemanticsInvoked).isFalse()
        } else {
            assertThat(applySemanticsInvoked).isTrue()
        }
        assertThat(semanticsConfiguration?.text()).containsExactly("Text 2")
    }

    @Test
    fun readingSemanticsConfigurationOfDeactivatedNode() {
        // Arrange.
        lateinit var lazyListState: LazyListState
        lateinit var rootForTest: RootForTest
        rule.setContent {
            rootForTest = LocalView.current as RootForTest
            lazyListState = rememberLazyListState()
            LazyRow(state = lazyListState, modifier = Modifier.size(10.dp)) {
                items(2) { index ->
                    Box(Modifier.size(10.dp).testTag("$index").elementOf(TestSemanticsModifier {}))
                }
            }
        }
        val semanticsId = rule.onNodeWithTag("0").semanticsId()
        val semanticsInfo = checkNotNull(rootForTest.semanticsOwner[semanticsId])

        // Act.
        rule.runOnIdle { lazyListState.requestScrollToItem(1) }
        val semanticsConfiguration = rule.runOnIdle { semanticsInfo.semanticsConfiguration }

        // Assert.
        rule.runOnIdle {
            assertThat(semanticsInfo.isDeactivated).isTrue()
            assertThat(semanticsConfiguration).isNull()
        }
    }

    @Test
    fun readingSemanticsConfigurationOfDeactivatedNode_afterCallingInvalidate() {
        // Arrange.
        lateinit var lazyListState: LazyListState
        lateinit var rootForTest: RootForTest
        val semanticsModifierNodes = List(2) { TestSemanticsModifier {} }
        rule.setContent {
            rootForTest = LocalView.current as RootForTest
            lazyListState = rememberLazyListState()
            LazyRow(state = lazyListState, modifier = Modifier.size(10.dp)) {
                items(2) { index ->
                    Box(
                        Modifier.size(10.dp)
                            .testTag("$index")
                            .elementOf(semanticsModifierNodes[index])
                    )
                }
            }
        }
        val semanticsId = rule.onNodeWithTag("0").semanticsId()
        val semanticsInfo = checkNotNull(rootForTest.semanticsOwner[semanticsId])

        // Act.
        rule.runOnIdle { lazyListState.requestScrollToItem(1) }
        semanticsModifierNodes[0].invalidateSemantics()
        val semanticsConfiguration = rule.runOnIdle { semanticsInfo.semanticsConfiguration }

        // Assert.
        rule.runOnIdle {
            assertThat(semanticsInfo.isDeactivated).isTrue()
            assertThat(semanticsConfiguration).isNull()
        }
    }

    fun SemanticsConfiguration.text() = getOrNull(SemanticsProperties.Text)?.map { it.text }

    class TestSemanticsModifier(
        private val onApplySemantics: SemanticsPropertyReceiver.() -> Unit
    ) : SemanticsModifierNode, Modifier.Node() {
        override fun SemanticsPropertyReceiver.applySemantics() {
            onApplySemantics.invoke(this)
        }
    }
}
