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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import kotlin.test.Test


/**
 * Tests the filters (e.g. [hasParent]) functionality of the testing framework.
 */
@OptIn(ExperimentalTestApi::class)
class FiltersTest {

    @Suppress("DEPRECATION")
    @Test
    fun testIsDialogOnDialog() = runComposeUiTest {
        setContent {
            Dialog(
                onDismissRequest = {}
            ) {
                Text(
                    text = "Text",
                    modifier = Modifier.testTag("tag")
                )

            }
        }

        onNodeWithTag("tag").assert(hasAnyAncestor(isDialog()))
    }

    @Suppress("DEPRECATION")
    @Test
    fun testIsPopup() = runComposeUiTest {
        setContent {
            Popup {
                Text(
                    text = "Text",
                    modifier = Modifier.testTag("tag")
                )
            }
        }

        onNodeWithTag("tag").assert(hasAnyAncestor(isPopup()))
    }

    @Suppress("DEPRECATION")
    @Test
    fun testHasAnyChild() = runComposeUiTest {
        setContent {
            Box(Modifier.testTag("box")) {
                Text(text = "text")
            }
        }

        onNodeWithTag("box").assert(hasAnyChild(hasText("text")))
    }

    @Suppress("DEPRECATION")
    @Test
    fun testHasAnyAncestor() = runComposeUiTest {
        setContent {
            Box(Modifier.testTag("ancestor1")) {
                Box(Modifier.testTag("ancestor2")) {
                    Text(text = "text")
                }
            }
        }

        onNodeWithText("text").assert(hasAnyAncestor(hasTestTag("ancestor1")))
        onNodeWithText("text").assert(hasAnyAncestor(hasTestTag("ancestor2")))
    }

    @Suppress("DEPRECATION")
    @Test
    fun testHasAnyParent() = runComposeUiTest {
        setContent {
            Box(Modifier.testTag("parent")) {
                Text(text = "text")
            }
        }

        onNodeWithText("text").assert(hasParent(hasTestTag("parent")))
    }

    @Suppress("DEPRECATION")
    @Test
    fun testHasAnySibling() = runComposeUiTest {
        setContent {
            Box {
                Text(text = "text1")
                Text(text = "text2")
            }
        }

        onNodeWithText("text1").assert(hasAnySibling(hasText("text2")))
    }

    @Suppress("DEPRECATION")
    @Test
    fun testIsRoot() = runComposeUiTest {
        setContent {
            Text(text = "text")
        }

        onNodeWithText("text").assert(hasParent(isRoot()))
    }

    @Suppress("DEPRECATION")
    @Test
    fun testHasSetTextAction() = runComposeUiTest {
        setContent {
            TextField(
                value = "text",
                onValueChange = {}
            )
        }

        onNodeWithText("text").assert(hasSetTextAction())
    }

    @Suppress("DEPRECATION")
    @Test
    fun testHasScrollAction() = runComposeUiTest {
        setContent {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("tag")
            ) {
                Text("1")
                Text("2")
            }
        }

        onNodeWithTag("tag").assert(hasScrollAction())
    }

    @Suppress("DEPRECATION")
    @Test
    fun testHasNoScrollAction() = runComposeUiTest {
        setContent {
            Column(
                Modifier.testTag("tag")
            ) {
                Text("1")
                Text("2")
            }
        }

        onNodeWithTag("tag").assert(hasNoScrollAction())
    }

    @Suppress("DEPRECATION")
    @Test
    fun testHasScrollToIndexAction() = runComposeUiTest {
        setContent {
            LazyColumn(
                Modifier.testTag("tag")
            ) {
                items(2) {
                    Text("$it")
                }
            }
        }

        onNodeWithTag("tag").assert(hasScrollToIndexAction())
    }

    @Suppress("DEPRECATION")
    @Test
    fun testHasScrollToKeyAction() = runComposeUiTest {
        setContent {
            LazyColumn(
                Modifier.testTag("tag")
            ) {
                items(2) {
                    Text("$it")
                }
            }
        }

        onNodeWithTag("tag").assert(hasScrollToKeyAction())
    }

    @Suppress("DEPRECATION")
    @Test
    fun testHasScrollToNodeAction() = runComposeUiTest {
        setContent {
            LazyColumn(
                Modifier.testTag("tag")
            ) {
                items(2) {
                    Text("$it")
                }
            }
        }

        onNodeWithTag("tag").assert(hasScrollToNodeAction())
    }
}