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

package androidx.compose.mpp.demo.bugs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight

@Composable
fun A11yMissingAriaAttributesExample() {

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        val topSampleText1 = "This sentence is in "
        val bottomSampleText1 = "the left column."
        val topSampleText2 = "This sentence is"
        val bottomSampleText2 = "on the right."

        Text(
            "isTraversalGroup = true", fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                heading()
                contentDescription = "Heading content description - isTraversalGroup = true"
                stateDescription = "Heading state description - isTraversalGroup = true"
                testTag = "headingTestTag"
            })
        Row {
            CardBox(
                topSampleText1,
                bottomSampleText1,
                Modifier.semantics { isTraversalGroup = true }
            )
            CardBox(
                topSampleText2,
                bottomSampleText2,
                Modifier.semantics { isTraversalGroup = true }
            )
        }

        Text(
            "isTraversalGroup = false", fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                heading()
                contentDescription = "Heading content description - isTraversalGroup = false"
                stateDescription = "Heading state description - isTraversalGroup = false"
                testTag = "headingTestTag"
            })
        Row {
            CardBox(
                topSampleText1,
                bottomSampleText1,
                Modifier.semantics { isTraversalGroup = false }
            )
            CardBox(
                topSampleText2,
                bottomSampleText2,
                Modifier.semantics { isTraversalGroup = false }
            )
        }

        Text(
            "Inaccessible Text - invisible below", fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                heading()
                contentDescription = "Heading content description - Inaccessible Text"
                stateDescription = "Heading state description - Inaccessible Text"
                testTag = "headingTestTag"
            })
        Text(
            "This text is inaccessible",
            modifier = Modifier.alpha(0f)
                .semantics {
                    contentDescription = "Inaccessible text content description"
                    stateDescription = "Inaccessible text state description"
                    testTag = "inaccessibleTextFieldTestTag"
                }
        )

        Text(
            "mergeDescendants = true", fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                heading()
                contentDescription = "Heading content description - mergeDescendants = true"
                stateDescription = "Heading state description - mergeDescendants = true"
                testTag = "headingTestTag"
            })
        Box {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Common content description"
                    stateDescription = "Common state description"
                    testTag = "commonTestTag"
                }
            ) {
                Text("Text 1")
                Text("Text 2")
                Text("Text 3")
            }
        }

        Text(
            "mergeDescendants = false", fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                heading()
                contentDescription = "Heading content description - mergeDescendants = false"
                stateDescription = "Heading state description - mergeDescendants = false"
                testTag = "headingTestTag"
            })
        Box {
            Column(
                modifier = Modifier.semantics(mergeDescendants = false) {
                    contentDescription = "Common content description"
                    stateDescription = "Common state description"
                    testTag = "commonTestTag"
                }
            ) {
                Text("Text 1")
                Text("Text 2")
                Text("Text 3")
            }
        }
    }
}

@Composable
private fun CardBox(
    topSampleText: String,
    bottomSampleText: String,
    modifier: Modifier = Modifier
) {

    Column(modifier = modifier.wrapContentSize()) {
        Text(topSampleText)
        Text(bottomSampleText)
    }
}