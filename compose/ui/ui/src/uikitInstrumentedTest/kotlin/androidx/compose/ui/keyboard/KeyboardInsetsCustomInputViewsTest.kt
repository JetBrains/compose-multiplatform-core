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

package androidx.compose.ui.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.window.KeyboardLayoutGuideGeometrySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectGetMinY
import platform.CoreGraphics.CGRectIsEmpty
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIInputView
import platform.UIKit.UIInputViewStyle
import platform.UIKit.UIScreen
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
internal class KeyboardInsetsCustomInputViewsTest {

    @Test
    fun testImePaddingReturnsToScreenAfterCustomInputKeyboardHides() = runUIKitInstrumentedTest {
        var isFocused = false
        var bottomContentRect: DpRect? = null
        var focusManager: FocusManager? = null
        val focusRequester = FocusRequester()
        val customInputView = customInputView(frame = CGRectMake(0.0, 0.0, 150.0, 300.0))
        val customInputAccessoryView = customInputAccessoryView(
            frame = CGRectMake(0.0, 0.0, 150.0, 100.0)
        )

        setContent {
            focusManager = LocalFocusManager.current
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                @Suppress("DEPRECATION")
                TextField(
                    value = "",
                    onValueChange = {},
                    keyboardOptions = KeyboardOptions(
                        platformImeOptions = PlatformImeOptions {
                            inputView(customInputView)
                            inputAccessoryView(customInputAccessoryView)
                        }
                    ),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused }
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "bottom content",
                    modifier = Modifier.onGloballyPositioned {
                        bottomContentRect = it.boundsInWindow().toDpRect(density)
                    }
                )
            }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }

        waitUntil("Custom input keyboard must be shown") {
            isFocused && keyboardOverlapHeight() >= 400.0
        }

        focusManager?.clearFocus()
        waitUntil("Custom input keyboard and IME padding must disappear") {
            !isFocused && keyboardOverlapHeight() == 0.0 &&
                bottomContentRect?.bottom == screenSize.height
        }
    }

    @Test
    fun testInputAccessoryViewHeightChangesKeyboardOverlapByItsHeight() = runUIKitInstrumentedTest {
        val inputAccessoryView = mutableStateOf(
            customInputAccessoryView(frame = CGRectZero.readValue())
        )
        val nonZeroInputAccessoryView = customInputAccessoryView(
            frame = CGRectMake(0.0, 0.0, 150.0, 100.0)
        )

        setContentWithChangingInputAccessoryView(inputAccessoryView)
        waitForIdle()
        val keyboardOverlapWithoutAccessory = keyboardOverlapHeight()

        inputAccessoryView.value = nonZeroInputAccessoryView
        waitUntil("Input accessory view must increase the IME overlap") {
            keyboardOverlapHeight() > keyboardOverlapWithoutAccessory
        }
        val keyboardOverlapWithAccessory = keyboardOverlapHeight()

        assertTrue(keyboardOverlapWithoutAccessory > 100.0)
        assertTrue(
            keyboardOverlapWithAccessory > keyboardOverlapWithoutAccessory,
            "Expected a nonzero input accessory view to increase keyboard overlap"
        )
        assertEquals(100.0, keyboardOverlapWithAccessory - keyboardOverlapWithoutAccessory)
    }

    @Test
    fun testZeroFrameInputViewDoesNotBlockIdle() = runUIKitInstrumentedTest {
        val customInputView = customInputView(frame = CGRectZero.readValue())

        setContentWithInputViews(customInputView = customInputView)
        waitForIdle()
    }

    @Test
    fun testNonZeroFrameInputViewHasMatchingKeyboardOverlap_iPhone() = runUIKitInstrumentedTest(
        ignoreIf = UIKitInstrumentedTest.isRunningOnIPad,
        ignoreNotes = "Run for iPhone only"
    ) {
        val customInputView = customInputView(frame = CGRectMake(0.0, 0.0, 150.0, 300.0))

        setContentWithInputViews(customInputView = customInputView)

        assertEquals(300.0, keyboardOverlapHeight())
    }

    @Test
    fun testNonZeroFrameInputViewHasAtLeastCustomViewHeight_iPad() = runUIKitInstrumentedTest(
        ignoreIf = !UIKitInstrumentedTest.isRunningOnIPad,
        ignoreNotes = "Run for iPad only"
    ) {
        val customInputView = customInputView(frame = CGRectMake(0.0, 0.0, 150.0, 300.0))

        setContentWithInputViews(customInputView = customInputView)

        assertKeyboardOverlapAtLeast(300.0)
    }

    @Test
    fun testNonZeroFrameInputAndZeroFrameAccessoryViewsHaveMatchingKeyboardOverlap_iPhone() = runUIKitInstrumentedTest(
        ignoreIf = UIKitInstrumentedTest.isRunningOnIPad,
        ignoreNotes = "Run for iPhone only"
    ) {
        val customInputView = customInputView(frame = CGRectMake(0.0, 0.0, 150.0, 300.0))
        val customInputAccessoryView = customInputAccessoryView(frame = CGRectZero.readValue())

        setContentWithInputViews(customInputView, customInputAccessoryView)

        assertEquals(300.0, keyboardOverlapHeight())
    }

    @Test
    fun testNonZeroFrameInputAndZeroFrameAccessoryViewsHaveAtLeastCustomViewHeight_iPad() = runUIKitInstrumentedTest(
        ignoreIf = !UIKitInstrumentedTest.isRunningOnIPad,
        ignoreNotes = "Run for iPad only"
    ) {
        val customInputView = customInputView(frame = CGRectMake(0.0, 0.0, 150.0, 300.0))
        val customInputAccessoryView = customInputAccessoryView(frame = CGRectZero.readValue())

        setContentWithInputViews(customInputView, customInputAccessoryView)

        assertKeyboardOverlapAtLeast(300.0)
    }

    @Test
    fun testNonZeroFrameInputAndAccessoryViewsHaveMatchingKeyboardOverlap_iPhone() = runUIKitInstrumentedTest(
        ignoreIf = UIKitInstrumentedTest.isRunningOnIPad,
        ignoreNotes = "Run for iPhone only"
    ) {
        val customInputView = customInputView(frame = CGRectMake(0.0, 0.0, 150.0, 300.0))
        val customInputAccessoryView = customInputAccessoryView(frame = CGRectMake(0.0, 0.0, 150.0, 100.0))

        setContentWithInputViews(customInputView, customInputAccessoryView)

        assertEquals(400.0, keyboardOverlapHeight())
    }

    @Test
    fun testNonZeroFrameInputAndAccessoryViewsHaveAtLeastCustomViewsHeight_iPad() = runUIKitInstrumentedTest(
        ignoreIf = !UIKitInstrumentedTest.isRunningOnIPad,
        ignoreNotes = "Run for iPad only"
    ) {
        val customInputView = customInputView(frame = CGRectMake(0.0, 0.0, 150.0, 300.0))
        val customInputAccessoryView = customInputAccessoryView(frame = CGRectMake(0.0, 0.0, 150.0, 100.0))

        setContentWithInputViews(customInputView, customInputAccessoryView)

        assertKeyboardOverlapAtLeast(400.0)
    }

    private fun UIKitInstrumentedTest.setContentWithInputViews(
        customInputView: UIInputView? = null,
        customInputAccessoryView: UIView? = null
    ) {
        val focusRequester = FocusRequester()

        setContent {
            @Suppress("DEPRECATION")
            TextField(
                value = "",
                onValueChange = {},
                keyboardOptions = KeyboardOptions(
                    platformImeOptions = PlatformImeOptions {
                        customInputView?.let(::inputView)
                        customInputAccessoryView?.let(::inputAccessoryView)
                    }
                ),
                modifier = Modifier.focusRequester(focusRequester)
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    }

    private fun UIKitInstrumentedTest.setContentWithChangingInputAccessoryView(
        inputAccessoryView: MutableState<UIView>
    ) {
        val focusRequester = FocusRequester()

        setContent {
            @Suppress("DEPRECATION")
            TextField(
                value = "",
                onValueChange = {},
                keyboardOptions = KeyboardOptions(
                    platformImeOptions = PlatformImeOptions {
                        inputAccessoryView(inputAccessoryView.value)
                    }
                ),
                modifier = Modifier.focusRequester(focusRequester)
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    }

    private fun customInputView(frame: CValue<CGRect>): UIInputView = object : UIInputView(
        frame = frame,
        inputViewStyle = UIInputViewStyle.UIInputViewStyleKeyboard
    ) {}

    private fun customInputAccessoryView(frame: CValue<CGRect>): UIView = object : UIView(
        frame = frame
    ) {}

    private fun UIKitInstrumentedTest.keyboardOverlapHeight(): Double {
        val keyboardFrame = KeyboardLayoutGuideGeometrySource(viewController.view).keyboardFrame
        if (CGRectIsEmpty(keyboardFrame)) return 0.0

        val screenHeight = UIScreen.mainScreen.bounds.useContents { size.height }
        return (screenHeight - CGRectGetMinY(keyboardFrame)).coerceAtLeast(0.0)
    }

    private fun UIKitInstrumentedTest.assertKeyboardOverlapAtLeast(minimum: Double) {
        waitUntil("Expected keyboard overlap >= $minimum") {
            keyboardOverlapHeight() >= minimum
        }
        val keyboardOverlap = keyboardOverlapHeight()
        assertTrue(
            keyboardOverlap >= minimum,
            "Expected keyboard overlap >= $minimum, actual=$keyboardOverlap"
        )
    }

}
