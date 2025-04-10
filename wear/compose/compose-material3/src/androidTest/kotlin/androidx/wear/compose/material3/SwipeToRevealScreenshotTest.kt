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

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.testutils.assertAgainstGolden
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.screenshot.AndroidXScreenshotTestRule
import androidx.wear.compose.foundation.RevealActionType
import androidx.wear.compose.foundation.RevealValue
import androidx.wear.compose.foundation.rememberRevealState
import androidx.wear.compose.materialcore.screenWidthDp
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@MediumTest
@RunWith(TestParameterInjector::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class SwipeToRevealScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val screenshotRule = AndroidXScreenshotTestRule(SCREENSHOT_GOLDEN_PATH)

    @get:Rule val testName = TestName()

    @Test
    fun swipeToReveal_showsPrimaryAction(@TestParameter screenSize: ScreenSize) {
        verifyScreenshotForSize(screenSize) {
            Box(modifier = Modifier.fillMaxSize()) {
                SwipeToReveal(
                    modifier = Modifier.testTag(TEST_TAG),
                    revealState =
                        rememberRevealState(
                            initialValue = RevealValue.RightRevealing,
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

    @Test
    fun swipeToReveal_showsPrimaryAndSecondaryActions(@TestParameter screenSize: ScreenSize) {
        verifyScreenshotForSize(screenSize) {
            Box(modifier = Modifier.fillMaxSize()) {
                SwipeToReveal(
                    modifier = Modifier.testTag(TEST_TAG),
                    revealState =
                        rememberRevealState(
                            initialValue = RevealValue.RightRevealing,
                            anchors =
                                SwipeToRevealDefaults.anchors(
                                    anchorWidth = SwipeToRevealDefaults.DoubleActionAnchorWidth,
                                )
                        ),
                    actions = {
                        primaryAction(
                            {},
                            { Icon(Icons.Outlined.Close, contentDescription = "Clear") },
                            { Text("Clear") }
                        )
                        secondaryAction(
                            {},
                            { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
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

    @Test
    fun swipeToReveal_showsUndoPrimaryAction(@TestParameter screenSize: ScreenSize) {
        verifyScreenshotForSize(screenSize) {
            Box(modifier = Modifier.fillMaxSize()) {
                SwipeToReveal(
                    modifier = Modifier.testTag(TEST_TAG),
                    revealState =
                        rememberRevealState(
                            initialValue = RevealValue.RightRevealed,
                            anchors = SwipeToRevealDefaults.anchors()
                        ),
                    actions = {
                        primaryAction(
                            {}, /* Empty for testing */
                            {}, /* Empty for testing */
                            {} /* Empty for testing */
                        )
                        undoPrimaryAction({}, { Text("Undo Primary") })
                    }
                ) {
                    Button({}) { Text(/* Empty for testing */ "") }
                }
            }
        }
    }

    @Test
    fun swipeToReveal_showsUndoPrimaryAction_singleLineTruncated() {
        verifyScreenshotForSize(ScreenSize.SMALL) {
            Box(modifier = Modifier.fillMaxSize()) {
                SwipeToReveal(
                    modifier = Modifier.testTag(TEST_TAG),
                    revealState =
                        rememberRevealState(
                            initialValue = RevealValue.RightRevealed,
                            anchors = SwipeToRevealDefaults.anchors()
                        ),
                    actions = {
                        primaryAction(
                            {}, /* Empty for testing */
                            {}, /* Empty for testing */
                            {} /* Empty for testing */
                        )
                        undoPrimaryAction(
                            {},
                            {
                                Text(
                                    "Undo Delete action with an extremely long label that should truncate."
                                )
                            }
                        )
                    }
                ) {
                    Button({}) { Text(/* Empty for testing */ "") }
                }
            }
        }
    }

    @Test
    fun swipeToReveal_showsUndoSecondaryAction(@TestParameter screenSize: ScreenSize) {
        verifyScreenshotForSize(screenSize) {
            Box(modifier = Modifier.fillMaxSize()) {
                SwipeToReveal(
                    modifier = Modifier.testTag(TEST_TAG),
                    revealState =
                        rememberRevealState(
                                initialValue = RevealValue.RightRevealed,
                                anchors = SwipeToRevealDefaults.anchors()
                            )
                            .apply { lastActionType = RevealActionType.SecondaryAction },
                    actions = {
                        primaryAction(
                            {}, /* Empty for testing */
                            {}, /* Empty for testing */
                            {} /* Empty for testing */
                        )
                        undoPrimaryAction(
                            {}, /* Empty for testing */
                            {}, /* Empty for testing */
                        )
                        secondaryAction({}, /* Empty for testing */ {} /* Empty for testing */)
                        undoSecondaryAction({}, { Text("Undo Secondary") })
                    }
                ) {
                    Button({}) { Text(/* Empty for testing */ "") }
                }
            }
        }
    }

    @Test
    fun swipeToReveal_showsUndoSecondaryAction_singleLineTruncated() {
        verifyScreenshotForSize(ScreenSize.SMALL) {
            Box(modifier = Modifier.fillMaxSize()) {
                SwipeToReveal(
                    modifier = Modifier.testTag(TEST_TAG),
                    revealState =
                        rememberRevealState(
                                initialValue = RevealValue.RightRevealed,
                                anchors = SwipeToRevealDefaults.anchors()
                            )
                            .apply { lastActionType = RevealActionType.SecondaryAction },
                    actions = {
                        primaryAction(
                            {}, /* Empty for testing */
                            {}, /* Empty for testing */
                            {} /* Empty for testing */
                        )
                        undoPrimaryAction(
                            {}, /* Empty for testing */
                            {}, /* Empty for testing */
                        )
                        secondaryAction({}, /* Empty for testing */ {} /* Empty for testing */)
                        undoSecondaryAction(
                            {},
                            {
                                Text(
                                    "Undo Delete action with an extremely long label that should truncate."
                                )
                            }
                        )
                    }
                ) {
                    Button({}) { Text(/* Empty for testing */ "") }
                }
            }
        }
    }

    @Test
    fun swipeToReveal_showsContent(@TestParameter screenSize: ScreenSize) {
        verifyScreenshotForSize(screenSize) {
            Box(modifier = Modifier.fillMaxSize()) {
                SwipeToReveal(
                    modifier = Modifier.testTag(TEST_TAG),
                    actions = {
                        primaryAction(
                            {}, /* Empty for testing */
                            {}, /* Empty for testing */
                            {} /* Empty for testing */
                        )
                    }
                ) {
                    Button({}, Modifier.fillMaxWidth()) {
                        Text("This content should be fully visible.")
                    }
                }
            }
        }
    }

    @Test
    fun swipeToRevealCard_showsLargePrimaryAction(@TestParameter screenSize: ScreenSize) {
        verifyScreenshotForSize(screenSize) {
            Box(modifier = Modifier.fillMaxSize()) {
                SwipeToReveal(
                    modifier = Modifier.testTag(TEST_TAG),
                    revealState =
                        rememberRevealState(
                            initialValue = RevealValue.RightRevealing,
                            anchors = SwipeToRevealDefaults.anchors()
                        ),
                    actionButtonHeight = SwipeToRevealDefaults.LargeActionButtonHeight,
                    actions = {
                        primaryAction(
                            {},
                            { Icon(Icons.Outlined.Close, contentDescription = "Clear") },
                            { Text("Clear") }
                        )
                    }
                ) {
                    Card({}, Modifier.fillMaxWidth()) {
                        Text("This content should be partially visible.")
                    }
                }
            }
        }
    }

    @Test
    fun swipeToRevealCard_showsLargePrimaryAndSecondaryActions(
        @TestParameter screenSize: ScreenSize
    ) {
        verifyScreenshotForSize(screenSize) {
            Box(modifier = Modifier.fillMaxSize()) {
                SwipeToReveal(
                    modifier = Modifier.testTag(TEST_TAG),
                    revealState =
                        rememberRevealState(
                            initialValue = RevealValue.RightRevealing,
                            anchors =
                                SwipeToRevealDefaults.anchors(
                                    anchorWidth = SwipeToRevealDefaults.DoubleActionAnchorWidth,
                                )
                        ),
                    actionButtonHeight = SwipeToRevealDefaults.LargeActionButtonHeight,
                    actions = {
                        primaryAction(
                            {},
                            { Icon(Icons.Outlined.Close, contentDescription = "Clear") },
                            { Text("Clear") }
                        )
                        secondaryAction(
                            {},
                            { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                        )
                    }
                ) {
                    Card({}, Modifier.fillMaxWidth()) {
                        Text("This content should be partially visible.")
                    }
                }
            }
        }
    }

    @Test
    fun swipeToReveal_showsPrimaryAndSecondaryActionsLeft(@TestParameter screenSize: ScreenSize) {
        verifyScreenshotForSize(screenSize) {
            Box(modifier = Modifier.fillMaxSize()) {
                SwipeToReveal(
                    modifier = Modifier.testTag(TEST_TAG),
                    revealState =
                        rememberRevealState(
                            initialValue = RevealValue.LeftRevealing,
                            anchors =
                                SwipeToRevealDefaults.bidirectionalAnchors(
                                    anchorWidth = SwipeToRevealDefaults.DoubleActionAnchorWidth,
                                )
                        ),
                    actions = {
                        primaryAction(
                            {},
                            { Icon(Icons.Outlined.Close, contentDescription = "Clear") },
                            { Text("Clear") }
                        )
                        secondaryAction(
                            {},
                            { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
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

    @Test
    fun swipeToReveal_beforeButtonVisibleThreshold_doesNotShowActions(
        @TestParameter screenSize: ScreenSize
    ) {
        val swipeScreenPercent = 0.05f

        verifyScreenshotAfterSwipe(screenSize, testName.goldenIdentifier(), swipeScreenPercent)
    }

    @Test
    fun swipeToReveal_beforeButtonFadeInThreshold_showsActions(
        @TestParameter screenSize: ScreenSize
    ) {
        val swipeScreenPercent = 0.11f

        verifyScreenshotAfterSwipe(screenSize, testName.goldenIdentifier(), swipeScreenPercent)
    }

    private fun verifyScreenshotAfterSwipe(
        screenSize: ScreenSize,
        goldenIdentifier: String,
        swipeScreenPercent: Float
    ) {
        var screenWidthPx: Float? = null
        rule.setContentWithTheme {
            screenWidthPx = with(LocalDensity.current) { screenWidthDp().dp.toPx() }

            ScreenConfiguration(screenSize.size) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SwipeToReveal(
                        modifier = Modifier.testTag(TEST_TAG),
                        actions = {
                            primaryAction(
                                {},
                                { Icon(Icons.Outlined.Close, contentDescription = "Clear") },
                                { Text("Clear") }
                            )
                            secondaryAction(
                                {},
                                { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                            )
                        }
                    ) {
                        Button({}, Modifier.fillMaxWidth()) { Text("Some text.") }
                    }
                }
            }
        }

        rule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(center)
            moveTo(Offset(center.x - (screenWidthPx!! * swipeScreenPercent), center.y))
        }

        rule
            .onNodeWithTag(TEST_TAG)
            .captureToImage()
            .assertAgainstGolden(screenshotRule, goldenIdentifier)
    }

    private fun verifyScreenshotForSize(screenSize: ScreenSize, content: @Composable () -> Unit) {
        rule.verifyScreenshot(
            screenshotRule = screenshotRule,
            methodName = testName.goldenIdentifier()
        ) {
            ScreenConfiguration(screenSize.size) { content() }
        }
    }
}
