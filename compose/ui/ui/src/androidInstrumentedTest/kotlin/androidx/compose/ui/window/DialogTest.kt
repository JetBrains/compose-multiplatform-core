/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.compose.ui.window

import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_UP
import android.view.View
import android.view.Window
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.gesture.MotionEvent
import androidx.compose.ui.gesture.PointerProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerCoords
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isNotDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalComposeUiApi::class)
@MediumTest
@RunWith(AndroidJUnit4::class)
class DialogTest {
    @get:Rule val rule = createComposeRule()

    private val defaultText = "dialogText"
    private val testTag = "tag"
    private lateinit var dispatcher: OnBackPressedDispatcher

    @Before
    fun setup() {
        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            val activeDeviceIds = InputDevice.getDeviceIds()

            println(
                "POINTER_INPUT_DEBUG_LOG_TAG DialogTest.setup(), " +
                    "activeDeviceIds = $activeDeviceIds"
            )
        }
    }

    @After
    fun tearDown() {
        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            val activeDeviceIds = InputDevice.getDeviceIds()

            println(
                "POINTER_INPUT_DEBUG_LOG_TAG DialogTest.tearDown(), " +
                    "activeDeviceIds = $activeDeviceIds"
            )
        }
    }

    @Test
    fun dialogTest_isShowingContent() {
        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println("POINTER_INPUT_DEBUG_LOG_TAG DialogTest.dialogTest_isShowingContent() START")
        }
        setupDialogTest(closeDialogOnDismiss = false)
        rule.onNodeWithTag(testTag).assertIsDisplayed()

        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println("POINTER_INPUT_DEBUG_LOG_TAG DialogTest.dialogTest_isShowingContent() END")
        }
    }

    // Hits code path of b/399055247
    @Test
    fun dialogTest_isNotDismissed_whenClicked() {
        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dialogTest_isNotDismissed_whenClicked() START"
            )
        }

        var clickCount = 0
        setupDialogTest { DefaultDialogContent(Modifier.clickable { clickCount++ }) }

        assertThat(clickCount).isEqualTo(0)
        val interaction = rule.onNodeWithTag(testTag)
        interaction.assertIsDisplayed()

        // Click inside the dialog
        interaction.performClick()
        rule.waitForIdle()

        // Check that the Clickable was pressed and the Dialog is still visible.
        interaction.assertIsDisplayed()
        assertThat(clickCount).isEqualTo(1)

        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dialogTest_isNotDismissed_whenClicked() END"
            )
        }
    }

    // Hits code path of b/399055247
    @Test
    fun dialogTest_isNotDismissed_whenClicked_noClickableContent() {
        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dialogTest_isNotDismissed_whenClicked_noClickableContent() START"
            )
        }

        setupDialogTest { DefaultDialogContent() }

        val interaction = rule.onNodeWithTag(testTag)
        interaction.assertIsDisplayed()

        // Click inside the dialog
        interaction.performClick()
        rule.waitForIdle()

        // Check that the Clickable was pressed and the Dialog is still visible.
        interaction.assertIsDisplayed()

        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dialogTest_isNotDismissed_whenClicked_noClickableContent() END"
            )
        }
    }

    @Test
    fun dialogTest_isDismissed_whenSpecified() {
        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dialogTest_isDismissed_whenSpecified() START"
            )
        }

        setupDialogTest()
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        clickOutsideDialog()
        rule.waitForIdle()

        // Wait for the dialog to disappear AND events to fully propagate. The cancel event to
        // pointer input will wait until any other events (clicks) are finished before executing.
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1000)

        // Wait for the ui to disappear AND events to fully propagate through the non-standard
        // input system used in this test. We can't rely on waitForIdle() or other methods related
        // to the ui, because the input events aren't going through that standard input system..
        rule.waitUntil(timeoutMillis = 2000) { textInteraction.isNotDisplayed() }

        textInteraction.assertDoesNotExist()

        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dialogTest_isDismissed_whenSpecified() END"
            )
        }
    }

    @Test
    fun dialogTest_isDismissed_whenSpecified_decorFitsFalse() {
        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dialogTest_isDismissed_whenSpecified_decorFitsFalse() START"
            )
        }
        setupDialogTest(dialogProperties = DialogProperties(decorFitsSystemWindows = false))
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        clickOutsideDialog()
        rule.waitForIdle()

        // Wait for the dialog to disappear AND events to fully propagate. The cancel event to
        // pointer input will wait until any other events (clicks) are finished before executing.
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1000)

        // Wait for the ui to disappear AND events to fully propagate through the non-standard
        // input system used in this test. We can't rely on waitForIdle() or other methods related
        // to the ui, because the input events aren't going through that standard input system.
        rule.waitUntil(timeoutMillis = 2000) { textInteraction.isNotDisplayed() }

        textInteraction.assertDoesNotExist()
        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dialogTest_isDismissed_whenSpecified_decorFitsFalse() END"
            )
        }
    }

    @Test
    fun dialogTest_isNotDismissed_whenNotSpecified() {
        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dialogTest_isNotDismissed_whenNotSpecified() START"
            )
        }

        setupDialogTest(closeDialogOnDismiss = false)
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        clickOutsideDialog()
        // The Dialog should still be visible
        textInteraction.assertIsDisplayed()

        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dialogTest_isNotDismissed_whenNotSpecified() END"
            )
        }
    }

    @Test
    fun dialogTest_isNotDismissed_whenDismissOnClickOutsideIsFalse() {
        setupDialogTest(dialogProperties = DialogProperties(dismissOnClickOutside = false))
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        clickOutsideDialog()
        // The Dialog should still be visible
        textInteraction.assertIsDisplayed()
    }

    @Test
    fun dialogTest_isNotDismissed_whenDismissOnClickOutsideIsFalse_decorFitsFalse() {
        setupDialogTest(
            dialogProperties =
                DialogProperties(dismissOnClickOutside = false, decorFitsSystemWindows = false)
        )
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        clickOutsideDialog()
        // The Dialog should still be visible
        textInteraction.assertIsDisplayed()
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
    @Test
    fun dialogTest_isNotDismissed_whenPressOutside_releaseInside() {
        setupDialogTest(dialogProperties = DialogProperties())
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        clickDialog(pressOutside = true, releaseOutside = false)
        // The Dialog should still be visible
        textInteraction.assertIsDisplayed()
    }

    @Test
    fun dialogTest_isNotDismissed_whenPressInside_releaseOutside() {
        setupDialogTest(dialogProperties = DialogProperties())
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        clickDialog(pressOutside = false, releaseOutside = true)
        // The Dialog should still be visible
        textInteraction.assertIsDisplayed()
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
    @Test
    fun dialogTest_isNotDismissed_whenPressOutside_releaseInside_decorFitsFalse() {
        setupDialogTest(dialogProperties = DialogProperties(decorFitsSystemWindows = false))
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        clickDialog(pressOutside = true, releaseOutside = false)
        // The Dialog should still be visible
        textInteraction.assertIsDisplayed()
    }

    @Test
    fun dialogTest_isNotDismissed_whenPressInside_releaseOutside_decorFitsFalse() {
        setupDialogTest(dialogProperties = DialogProperties(decorFitsSystemWindows = false))
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        clickDialog(pressOutside = false, releaseOutside = true)
        // The Dialog should still be visible
        textInteraction.assertIsDisplayed()
    }

    @Test
    fun dialogTest_isDismissed_whenSpecified_backButtonPressed() {
        setupDialogTest()
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        pressBackViaKey()
        textInteraction.assertDoesNotExist()
    }

    @Test
    fun dialogTest_isDismissed_whenSpecified_backDispatched() {
        setupDialogTest()
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        dispatchBackButton()
        textInteraction.assertDoesNotExist()
    }

    @Test
    fun dialogTest_isNotDismissed_whenNotSpecified_backButtonPressed() {
        setupDialogTest(closeDialogOnDismiss = false)
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        pressBackViaKey()
        // The Dialog should still be visible
        textInteraction.assertIsDisplayed()
    }

    @Test
    fun dialogTest_isNotDismissed_whenNotSpecified_backDispatched() {
        setupDialogTest(closeDialogOnDismiss = false)
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        dispatchBackButton()
        // The Dialog should still be visible
        textInteraction.assertIsDisplayed()
    }

    @Test
    fun dialogTest_isNotDismissed_whenDismissOnBackPressIsFalse_backButtonPressed() {
        setupDialogTest(dialogProperties = DialogProperties(dismissOnBackPress = false))
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        pressBackViaKey()
        // The Dialog should still be visible
        textInteraction.assertIsDisplayed()
    }

    @Test
    fun dialogTest_isNotDismissed_whenDismissOnBackPressIsFalse_backDispatched() {
        setupDialogTest(dialogProperties = DialogProperties(dismissOnBackPress = false))
        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        dispatchBackButton()
        // The Dialog should still be visible
        textInteraction.assertIsDisplayed()
    }

    @Test
    fun dialogTest_backHandler_isCalled_backButtonPressed() {
        var clickCount = 0
        setupDialogTest(closeDialogOnDismiss = false) {
            BackHandler { clickCount++ }
            DefaultDialogContent()
        }

        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()
        assertThat(clickCount).isEqualTo(0)

        pressBackViaKey()
        textInteraction.assertIsDisplayed()
        assertThat(clickCount).isEqualTo(1)
    }

    @Test
    fun dialogTest_backHandler_isCalled_backDispatched() {
        var clickCount = 0
        setupDialogTest(closeDialogOnDismiss = false) {
            BackHandler { clickCount++ }
            DefaultDialogContent()
        }

        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()
        assertThat(clickCount).isEqualTo(0)

        dispatchBackButton()
        textInteraction.assertIsDisplayed()
        assertThat(clickCount).isEqualTo(1)
    }

    @Test
    fun dialogTest_isDismissed_escapePressed() {
        setupDialogTest()

        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        pressEscape()
        textInteraction.assertDoesNotExist()
    }

    @Test
    fun dialogTest_isNotDismissed_whenNotSpecified_escapePressed() {
        setupDialogTest(closeDialogOnDismiss = false)

        val textInteraction = rule.onNodeWithTag(testTag)
        textInteraction.assertIsDisplayed()

        pressEscape()
        textInteraction.assertIsDisplayed()
    }

    @Test
    fun dialog_preservesCompositionLocals() {
        val compositionLocal = compositionLocalOf<Float> { error("unset") }
        var value = 0f
        rule.setContent {
            CompositionLocalProvider(compositionLocal provides 1f) {
                Dialog(onDismissRequest = {}) { value = compositionLocal.current }
            }
        }
        rule.runOnIdle { assertEquals(1f, value) }
    }

    @Test
    fun smallDialogHasSmallWindowDefaultWidthDecorFits() {
        lateinit var dialogView: View
        rule.setContent {
            Dialog(
                {},
                properties =
                    DialogProperties(usePlatformDefaultWidth = true, decorFitsSystemWindows = true)
            ) {
                dialogView = LocalView.current
                Box(Modifier.size(with(LocalDensity.current) { 100.toDp() }))
            }
        }
        rule.runOnIdle {
            var root = dialogView
            while (root.parent is View) {
                root = root.parent as View
            }
            assertThat(root.width).isEqualTo(100)
            assertThat(root.height).isEqualTo(100)
        }
    }

    @Test
    fun smallDialogHasSmallWindowNotDefaultWidthDecorFits() {
        lateinit var dialogView: View
        rule.setContent {
            Dialog(
                {},
                properties =
                    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
            ) {
                dialogView = LocalView.current
                Box(Modifier.size(with(LocalDensity.current) { 100.toDp() }))
            }
        }
        rule.runOnIdle {
            var root = dialogView
            while (root.parent is View) {
                root = root.parent as View
            }
            assertThat(root.height).isEqualTo(100)
        }
    }

    @Test
    fun smallDialogHasSmallWindowDefaultWidthNoDecorFits() {
        lateinit var dialogView: View
        rule.setContent {
            Dialog(
                {},
                properties =
                    DialogProperties(usePlatformDefaultWidth = true, decorFitsSystemWindows = false)
            ) {
                dialogView = LocalView.current
                Box(Modifier.size(with(LocalDensity.current) { 100.toDp() }))
            }
        }
        rule.runOnIdle {
            val point = Point()
            @Suppress("DEPRECATION") dialogView.display.getRealSize(point)
            var root = dialogView
            while (root.parent is View) {
                root = root.parent as View
            }
            // For some reason, when decorFitsSystemWindows = false, the window doesn't
            // WRAP_CONTENT the dialog, but the width should be less than the full width of the
            // screen.
            assertThat(root.width).isLessThan(point.x)
            assertThat(root.height).isEqualTo(100)
        }
    }

    @Test
    fun smallDialogHasSmallWindowNotDefaultWidthNoDecorFits() {
        lateinit var dialogView: View
        rule.setContent {
            Dialog(
                {},
                properties =
                    DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    )
            ) {
                dialogView = LocalView.current
                Box(Modifier.size(with(LocalDensity.current) { 100.toDp() }))
            }
        }
        rule.runOnIdle {
            var root = dialogView
            while (root.parent is View) {
                root = root.parent as View
            }
            assertThat(root.height).isEqualTo(100)
        }
    }

    @Test
    fun canFillScreenWidth_dependingOnProperty() {
        var box1Width = 0
        var box2Width = 0
        lateinit var displayMetrics: DisplayMetrics
        rule.setContent {
            displayMetrics = LocalView.current.context.resources.displayMetrics
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(Modifier.fillMaxSize().onSizeChanged { box1Width = it.width })
            }
            Dialog(onDismissRequest = {}) {
                Box(Modifier.fillMaxSize().onSizeChanged { box2Width = it.width })
            }
        }
        val expectedWidth = with(rule.density) { displayMetrics.widthPixels }
        assertThat(box1Width).isEqualTo(expectedWidth)
        assertThat(box2Width).isLessThan(box1Width)
    }

    @Test
    fun canChangeSize() {
        var width by mutableStateOf(10.dp)
        var usePlatformDefaultWidth by mutableStateOf(false)
        var actualWidth = 0

        rule.setContent {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(usePlatformDefaultWidth = usePlatformDefaultWidth)
            ) {
                Box(Modifier.size(width, 150.dp).onSizeChanged { actualWidth = it.width })
            }
        }

        rule.runOnIdle {
            with(rule.density) { assertThat(actualWidth).isEqualTo(10.dp.roundToPx()) }
        }

        width = 20.dp
        rule.runOnIdle {
            with(rule.density) { assertThat(actualWidth).isEqualTo(20.dp.roundToPx()) }
        }

        usePlatformDefaultWidth = true
        width = 30.dp
        rule.runOnIdle {
            with(rule.density) { assertThat(actualWidth).isEqualTo(30.dp.roundToPx()) }
        }

        width = 40.dp
        rule.runOnIdle {
            with(rule.density) { assertThat(actualWidth).isEqualTo(40.dp.roundToPx()) }
        }
    }

    @Test
    fun ensurePositionIsCorrect() {
        var positionInRoot by mutableStateOf(Offset.Zero)
        lateinit var view: View

        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Dialog(onDismissRequest = {}) {
                    view = LocalView.current
                    // I know this is weird, but this is how to reproduce the bug:
                    val dialogWindowProvider = view.parent as DialogWindowProvider
                    dialogWindowProvider.window.setGravity(Gravity.FILL)

                    var boxSize by remember { mutableStateOf(IntSize.Zero) }

                    Box(
                        modifier =
                            Modifier.fillMaxSize().onGloballyPositioned {
                                boxSize = it.size
                                positionInRoot = it.positionOnScreen()
                            },
                        contentAlignment = Alignment.TopStart,
                    ) {
                        PopupUsingPosition(positionInRoot)
                    }
                }
            }
        }

        val realPosition = intArrayOf(0, 0)
        rule.runOnIdle { view.getLocationOnScreen(realPosition) }

        rule.runOnIdle {
            assertThat(positionInRoot.x.roundToInt()).isEqualTo(realPosition[0])
            assertThat(positionInRoot.y.roundToInt()).isEqualTo(realPosition[1])
        }
    }

    // Hits code path of b/399055247
    @Test
    fun dismissWhenClickingOutsideContent() {
        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dismissWhenClickingOutsideContent() START"
            )
        }

        var dismissed = false
        var clicked = false
        lateinit var composeView: View
        val clickBoxTag = "clickBox"
        rule.setContent {
            Dialog(
                onDismissRequest = { dismissed = true },
                properties =
                    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
            ) {
                composeView = LocalView.current
                Box(Modifier.size(10.dp).testTag(clickBoxTag).clickable { clicked = true })
            }
        }

        // click inside the compose view
        rule.onNodeWithTag(clickBoxTag).performClick()
        rule.waitForIdle()

        assertThat(dismissed).isFalse()
        assertThat(clicked).isTrue()

        clicked = false

        // click outside the compose view
        rule.waitForIdle()
        var root = composeView
        while (root.parent is View) {
            root = root.parent as View
        }

        rule.runOnIdle {
            val x = 1f
            val y = 1f
            val down =
                MotionEvent(
                    eventTime = 0,
                    action = ACTION_DOWN,
                    numPointers = 1,
                    actionIndex = 0,
                    pointerProperties = arrayOf(PointerProperties(0)),
                    pointerCoords = arrayOf(PointerCoords(x, y)),
                    root
                )
            root.dispatchTouchEvent(down)
            val up =
                MotionEvent(
                    eventTime = 10,
                    action = ACTION_UP,
                    numPointers = 1,
                    actionIndex = 0,
                    pointerProperties = arrayOf(PointerProperties(0)),
                    pointerCoords = arrayOf(PointerCoords(x, y)),
                    root
                )
            root.dispatchTouchEvent(up)
        }
        rule.waitForIdle()

        assertThat(dismissed).isTrue()
        assertThat(clicked).isFalse()

        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dismissWhenClickingOutsideContent() END"
            )
        }
    }

    // Hits code path of b/399055247
    @Test
    fun dismissWhenClickingOutsideContentNoDecorFitsSystemWindows() {
        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dismissWhenClickingOutsideContentNoDecorFitsSystemWindows() START"
            )
        }

        var dismissed = false
        var clicked = false
        lateinit var composeView: View
        val clickBoxTag = "clickBox"
        rule.setContent {
            Dialog(
                onDismissRequest = { dismissed = true },
                properties =
                    DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    )
            ) {
                composeView = LocalView.current
                Box(Modifier.size(10.dp).testTag(clickBoxTag).clickable { clicked = true })
            }
        }

        // click inside the compose view
        rule.onNodeWithTag(clickBoxTag).performClick()

        rule.waitForIdle()

        assertThat(dismissed).isFalse()
        assertThat(clicked).isTrue()

        clicked = false

        // click outside the compose view
        rule.waitForIdle()
        var root = composeView
        while (root.parent is View) {
            root = root.parent as View
        }

        rule.runOnIdle {
            val x = 1f
            val y = 1f
            val down =
                MotionEvent(
                    eventTime = 0,
                    action = ACTION_DOWN,
                    numPointers = 1,
                    actionIndex = 0,
                    pointerProperties = arrayOf(PointerProperties(0)),
                    pointerCoords = arrayOf(PointerCoords(x, y)),
                    root
                )
            root.dispatchTouchEvent(down)
            val up =
                MotionEvent(
                    eventTime = 10,
                    action = ACTION_UP,
                    numPointers = 1,
                    actionIndex = 0,
                    pointerProperties = arrayOf(PointerProperties(0)),
                    pointerCoords = arrayOf(PointerCoords(x, y)),
                    root
                )
            root.dispatchTouchEvent(up)
        }
        rule.waitForIdle()

        assertThat(dismissed).isTrue()
        assertThat(clicked).isFalse()

        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dismissWhenClickingOutsideContentNoDecorFitsSystemWindows() END"
            )
        }
    }

    // Hits code path of b/399055247
    @Test
    fun dismissWhenClickingWithNaNEvent() {
        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " +
                    "DialogTest.dismissWhenClickingWithNaNEvent() START"
            )
        }

        var dismissed = false
        var clicked = false
        lateinit var composeView: View
        val clickBoxTag = "clickBox"
        rule.setContent {
            Dialog(
                onDismissRequest = { dismissed = true },
                properties =
                    DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    )
            ) {
                composeView = LocalView.current
                Box(Modifier.size(10.dp).testTag(clickBoxTag).clickable { clicked = true })
            }
        }

        // click inside the compose view
        rule.onNodeWithTag(clickBoxTag).performClick()

        rule.waitForIdle()

        assertThat(dismissed).isFalse()
        assertThat(clicked).isTrue()

        clicked = false

        // click outside the compose view
        rule.waitForIdle()
        var root = composeView
        while (root.parent is View) {
            root = root.parent as View
        }

        rule.runOnIdle {
            val x = Float.NaN
            val y = Float.NaN
            val down =
                MotionEvent(
                    eventTime = 0,
                    action = ACTION_DOWN,
                    numPointers = 1,
                    actionIndex = 0,
                    pointerProperties = arrayOf(PointerProperties(0)),
                    pointerCoords = arrayOf(PointerCoords(x, y)),
                    root
                )
            root.dispatchTouchEvent(down)
            val up =
                MotionEvent(
                    eventTime = 10,
                    action = ACTION_UP,
                    numPointers = 1,
                    actionIndex = 0,
                    pointerProperties = arrayOf(PointerProperties(0)),
                    pointerCoords = arrayOf(PointerCoords(x, y)),
                    root
                )
            root.dispatchTouchEvent(up)
        }
        rule.waitForIdle()

        assertThat(dismissed).isTrue()
        assertThat(clicked).isFalse()

        if (ComposeUiFlags.isHitPathTrackerLoggingEnabled) {
            println(
                "POINTER_INPUT_DEBUG_LOG_TAG " + "DialogTest.dismissWhenClickingWithNaNEvent() END"
            )
        }
    }

    @Test
    fun dialogInsetsWhenDecorFitsSystemWindows() {
        var top = -1
        var bottom = -1
        val focusRequester = FocusRequester()
        rule.setContent {
            Dialog(onDismissRequest = {}) {
                val density = LocalDensity.current
                val insets = WindowInsets.safeContent
                Box(
                    Modifier.fillMaxSize().onPlaced {
                        top = insets.getTop(density)
                        bottom = insets.getBottom(density)
                    }
                ) {
                    TextField(
                        "Hello World",
                        onValueChange = {},
                        Modifier.align(Alignment.BottomStart).focusRequester(focusRequester)
                    )
                }
            }
        }

        rule.runOnIdle {
            assertThat(top).isEqualTo(0)
            assertThat(bottom).isEqualTo(0)
            focusRequester.requestFocus()
        }

        rule.runOnIdle {
            assertThat(top).isEqualTo(0)
            assertThat(bottom).isEqualTo(0)
        }
    }

    @Test
    fun dialogDefaultGravityIsCenter() {
        lateinit var view: View
        rule.setContent {
            Dialog(onDismissRequest = {}) {
                view = LocalView.current
                Box(Modifier.size(10.dp))
            }
        }
        rule.runOnIdle {
            var provider = view
            while (provider !is DialogWindowProvider) {
                provider = view.parent as View
            }
            val window = provider.window
            assertThat(window.attributes.gravity).isEqualTo(Gravity.CENTER)
        }
    }

    @Test
    @SdkSuppress(maxSdkVersion = 34)
    fun dialogThemeCanBeOverridden() {
        lateinit var window: Window
        rule.setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    ComposeView(
                            ContextThemeWrapper(
                                context,
                                androidx.compose.ui.tests.R.style.CustomDialogTheme
                            )
                        )
                        .apply {
                            setContent {
                                Dialog(
                                    onDismissRequest = {},
                                    properties =
                                        DialogProperties(
                                            decorFitsSystemWindows = false,
                                            usePlatformDefaultWidth = false
                                        )
                                ) {
                                    var parent = LocalView.current
                                    while (parent !is DialogWindowProvider) {
                                        parent = parent.parent as View
                                    }
                                    window = (parent as DialogWindowProvider).window
                                    Box(Modifier.fillMaxSize().background(Color.Blue))
                                }
                            }
                        }
                }
            )
        }
        rule.runOnIdle {
            @Suppress("DEPRECATION") assertThat(window.statusBarColor).isEqualTo(Color.Red.toArgb())
        }
    }

    private fun setupDialogTest(
        closeDialogOnDismiss: Boolean = true,
        dialogProperties: DialogProperties = DialogProperties(),
        dialogContent: @Composable () -> Unit = { DefaultDialogContent() },
    ) {
        rule.setContent {
            var showDialog by remember { mutableStateOf(true) }
            val onDismiss: () -> Unit =
                if (closeDialogOnDismiss) {
                    { showDialog = false }
                } else {
                    {}
                }
            if (showDialog) {
                Dialog(onDismiss, dialogProperties, dialogContent)
            }
        }
    }

    @Composable
    private fun DefaultDialogContent(modifier: Modifier = Modifier) {
        BasicText(defaultText, modifier = modifier.testTag(testTag))
        dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
    }

    /** Presses and releases the back button via a key press. */
    private fun pressBackViaKey() {
        UiDevice.getInstance(getInstrumentation()).pressBack()
    }

    /** Dispatches the back button directly, shortcutting any key presses. */
    private fun dispatchBackButton() {
        rule.runOnUiThread { dispatcher.onBackPressed() }
    }

    private fun pressEscape() {
        UiDevice.getInstance(getInstrumentation()).pressKeyCode(KeyEvent.KEYCODE_ESCAPE)
    }

    /** Try to dismiss the dialog by clicking between the topLefts of the dialog and the root. */
    private fun clickOutsideDialog() {
        clickDialog()
    }

    private fun clickDialog(pressOutside: Boolean = true, releaseOutside: Boolean = true) {
        val dialogBounds = rule.onNode(isRoot().and(hasAnyChild(isDialog()))).boundsOnScreen()
        val rootBounds = rule.onNode(isRoot().and(hasAnyChild(isDialog()).not())).boundsOnScreen()
        val outsidePosition = lerp(dialogBounds.topLeft, rootBounds.topLeft, 0.5f).round()
        val insidePosition = lerp(dialogBounds.topLeft, dialogBounds.bottomRight, 0.5f).round()
        val uiDevice = UiDevice.getInstance(getInstrumentation())
        if (pressOutside && releaseOutside) {
            uiDevice.click(outsidePosition.x, outsidePosition.y)
        } else if (!pressOutside && !releaseOutside) {
            uiDevice.click(insidePosition.x, insidePosition.y)
        } else if (pressOutside) {
            uiDevice.drag(
                outsidePosition.x,
                outsidePosition.y,
                insidePosition.x,
                insidePosition.y,
                10
            )
        } else {
            uiDevice.drag(
                insidePosition.x,
                insidePosition.y,
                outsidePosition.x,
                outsidePosition.y,
                10
            )
        }
    }

    private fun SemanticsNodeInteraction.boundsOnScreen(): Rect {
        val bounds = with(rule.density) { getUnclippedBoundsInRoot().toRect() }
        val positionOnScreen = fetchSemanticsNode().positionOnScreen
        return bounds.translate(positionOnScreen)
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun enableComposeUiFlags() {
            ComposeUiFlags.isHitPathTrackerLoggingEnabled = true
            println("POINTER_INPUT_DEBUG_LOG_TAG DialogTest.enableComposeUiFlags()")
        }

        @AfterClass
        @JvmStatic
        fun disableComposeUiFlags() {
            println("POINTER_INPUT_DEBUG_LOG_TAG DialogTest.disableComposeUiFlags()")
            ComposeUiFlags.isHitPathTrackerLoggingEnabled = false
        }
    }
}

@Composable
private fun PopupUsingPosition(parentPositionInRoot: Offset) {
    // In split screen mode, the parents can have a y offset in vertical mode and a x offset in
    // vertical mode, which needs to be accounted for when calculating gravity and offset
    val popupPositionOffset =
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = anchorBounds.topLeft + parentPositionInRoot.round()
        }

    Popup(popupPositionProvider = popupPositionOffset) { Box(Modifier.fillMaxSize()) }
}
