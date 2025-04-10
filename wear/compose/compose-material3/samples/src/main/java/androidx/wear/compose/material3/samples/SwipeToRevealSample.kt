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

package androidx.wear.compose.material3.samples

import androidx.annotation.Sampled
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.RevealValue
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rememberRevealState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.SwipeToRevealDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

@Composable
@Sampled
fun SwipeToRevealSample() {
    SwipeToReveal(
        // Use the double action anchor width when revealing two actions
        revealState =
            rememberRevealState(
                anchors =
                    SwipeToRevealDefaults.anchors(
                        anchorWidth = SwipeToRevealDefaults.DoubleActionAnchorWidth,
                    )
            ),
        actions = {
            primaryAction(
                onClick = { /* This block is called when the primary action is executed. */ },
                icon = { Icon(Icons.Outlined.Delete, contentDescription = "Delete") },
                text = { Text("Delete") }
            )
            secondaryAction(
                onClick = { /* This block is called when the secondary action is executed. */ },
                icon = { Icon(Icons.Outlined.MoreVert, contentDescription = "Options") }
            )
            undoPrimaryAction(
                onClick = { /* This block is called when the undo primary action is executed. */ },
                text = { Text("Undo Delete") },
            )
        }
    ) {
        Button(
            modifier =
                Modifier.fillMaxWidth().semantics {
                    // Use custom actions to make the primary and secondary actions accessible
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Delete") {
                                /* Add the primary action click handler here */
                                true
                            },
                            CustomAccessibilityAction("Options") {
                                /* Add the secondary click handler here */
                                true
                            }
                        )
                },
            onClick = {}
        ) {
            Text("This Button has two actions", modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
@Sampled
fun SwipeToRevealSingleActionCardSample() {
    SwipeToReveal(
        actionButtonHeight = SwipeToRevealDefaults.LargeActionButtonHeight,
        actions = {
            primaryAction(
                onClick = { /* This block is called when the primary action is executed. */ },
                icon = { Icon(Icons.Outlined.Delete, contentDescription = "Delete") },
                text = { Text("Delete") }
            )
            undoPrimaryAction(
                onClick = { /* This block is called when the undo primary action is executed. */ },
                text = { Text("Undo Delete") },
            )
        }
    ) {
        Card(
            modifier =
                Modifier.fillMaxWidth().semantics {
                    // Use custom actions to make the primary action accessible
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Delete") {
                                /* Add the primary action click handler here */
                                true
                            },
                        )
                },
            onClick = {}
        ) {
            Text(
                "This Card has one action, and the revealed button is taller",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
@Sampled
fun SwipeToRevealNonAnchoredSample() {
    SwipeToReveal(
        revealState =
            rememberRevealState(
                anchors = SwipeToRevealDefaults.anchors(useAnchoredActions = false)
            ),
        actions = {
            primaryAction(
                onClick = { /* This block is called when the primary action is executed. */ },
                icon = { Icon(Icons.Outlined.Delete, contentDescription = "Delete") },
                text = { Text("Delete") }
            )
            undoPrimaryAction(
                onClick = { /* This block is called when the undo primary action is executed. */ },
                icon = { Icon(Icons.Outlined.Refresh, contentDescription = "Undo") },
                text = { Text("Undo") },
            )
        }
    ) {
        Button(
            modifier =
                Modifier.fillMaxWidth().semantics {
                    // Use custom actions to make the primary action accessible
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Delete") {
                                /* Add the primary action click handler here */
                                true
                            },
                        )
                },
            onClick = {}
        ) {
            Text("Swipe to execute the primary action.", modifier = Modifier.fillMaxSize())
        }
    }
}

@Preview
@Composable
@Sampled
fun SwipeToRevealWithTransformingLazyColumnSample() {
    val transformationSpec = rememberTransformationSpec()
    val tlcState = rememberTransformingLazyColumnState()

    TransformingLazyColumn(
        state = tlcState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        modifier = Modifier.background(Color.Black)
    ) {
        items(count = 100) { index ->
            val revealState =
                rememberRevealState(
                    anchors =
                        SwipeToRevealDefaults.anchors(
                            anchorWidth = SwipeToRevealDefaults.DoubleActionAnchorWidth,
                        )
                )

            // SwipeToReveal is covered on scroll.
            LaunchedEffect(tlcState.isScrollInProgress) {
                if (
                    tlcState.isScrollInProgress && revealState.currentValue != RevealValue.Covered
                ) {
                    revealState.animateTo(targetValue = RevealValue.Covered)
                }
            }

            SwipeToReveal(
                revealState = revealState,
                modifier =
                    Modifier.transformedHeight(this@items, transformationSpec).graphicsLayer {
                        with(transformationSpec) { applyContainerTransformation(scrollProgress) }
                        // Is needed to disable clipping.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        clip = false
                    },
                actions = {
                    primaryAction(
                        onClick = { /* Called when the primary action is executed. */ },
                        icon = { Icon(Icons.Outlined.Delete, contentDescription = "Delete") },
                        text = { Text("Delete") }
                    )
                }
            ) {
                TransformExclusion {
                    TitleCard(
                        onClick = {},
                        title = { Text("Message #$index") },
                        subtitle = { Text("Body of the message") },
                        modifier =
                            Modifier.semantics {
                                // Use custom actions to make the primary action accessible
                                customActions =
                                    listOf(
                                        CustomAccessibilityAction("Delete") {
                                            /* Add the primary action click handler here */
                                            true
                                        },
                                    )
                            }
                    )
                }
            }
        }
    }
}
