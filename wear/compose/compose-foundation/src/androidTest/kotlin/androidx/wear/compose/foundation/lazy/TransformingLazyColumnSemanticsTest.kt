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

package androidx.wear.compose.foundation.lazy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class TransformingLazyColumnSemanticsTest {
    @get:Rule val rule = createComposeRule()

    private val lazyListTag = "LazyList"

    @Test
    fun testScrollingToEndOfList_displaysLastItem() {
        rule.setContent {
            TransformingLazyColumn(
                modifier = Modifier.height(100.dp).semantics { testTag = lazyListTag }
            ) {
                items(10) { index ->
                    Box(modifier = Modifier.height(100.dp).semantics { testTag = "Item $index" })
                }
            }
        }
        rule.onNodeWithTag(lazyListTag).performScrollToNode(hasTestTag("Item 9"))
        rule.onNodeWithTag("Item 9").assertIsPlaced()
        rule.onNodeWithTag("Item 0").assertIsNotPlaced()
    }
}
