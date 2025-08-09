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

package androidx.compose.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.DpRectZero
import androidx.compose.ui.uikit.ComposeUIViewControllerConfiguration
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDpRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskLandscapeLeft
import platform.UIKit.UIInterfaceOrientationMaskLandscapeRight
import platform.UIKit.UIViewController
import platform.UIKit.attemptRotationToDeviceOrientation

class WindowInsetsPaddingTest {
    @Test
    fun composableDoesNotRecomposeOnWindowInsetsImeChange() = runUIKitInstrumentedTest {
        var compositionCount = 0

        setContent {
            val focusRequester = remember { FocusRequester() }
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()){
                Spacer(modifier = Modifier.weight(1f))
                TextField(
                    "",
                    {},
                    Modifier.focusRequester(focusRequester)
                )
                compositionCount++
            }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }

        assertEquals(1, compositionCount)
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testDisplayCutoutPadding_InterfaceOrientationLandscapeLeft() = runUIKitInstrumentedTest {
        var boxRect = DpRectZero()

        setContent(interfaceOrientations = UIInterfaceOrientationMaskLandscapeLeft) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .background(Color.Red)
                    .onGloballyPositioned({ boxRect = it.boundsInWindow().toDpRect(density) })
            ) {
                Text("TEXT")
            }
        }

        assertEquals(
            DpRect(DpOffset.Zero, size = DpSize(
                screenSize.width - with(density) { hostingViewController.safeAreaInsets.right.toDp() },
                screenSize.height
            )),
            boxRect
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testDisplayCutoutPadding_InterfaceOrientationLandscapeRight() = runUIKitInstrumentedTest {
        var boxRect = DpRectZero()

        setContent(interfaceOrientations = UIInterfaceOrientationMaskLandscapeRight) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .background(Color.Red)
                    .onGloballyPositioned({ boxRect = it.boundsInWindow().toDpRect(density) })
            ) {
                Text("TEXT")
            }
        }

        val xOffset = with(density) { hostingViewController.safeAreaInsets.left.toDp() }

        assertEquals(
            DpRect(
                DpOffset(xOffset, 0.dp),
                size = DpSize(screenSize.width - xOffset, screenSize.height)
            ),
            boxRect
        )
    }
}

private fun UIKitInstrumentedTest.setContent(
    configure: ComposeUIViewControllerConfiguration.() -> Unit = {},
    interfaceOrientations: UIInterfaceOrientationMask,
    content: @Composable () -> Unit
) {
    setContent(configure, content)
    if (appDelegate.supportedInterfaceOrientations != interfaceOrientations) {
        appDelegate.supportedInterfaceOrientations = interfaceOrientations
        UIViewController.attemptRotationToDeviceOrientation()
        delay(1000)
    }
}