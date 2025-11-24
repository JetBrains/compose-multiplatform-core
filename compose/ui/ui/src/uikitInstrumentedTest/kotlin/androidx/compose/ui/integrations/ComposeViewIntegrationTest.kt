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

package androidx.compose.ui.integrations

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.scene.ComposeHostingView
import androidx.compose.ui.test.MockAppDelegate
import androidx.compose.ui.test.delay
import androidx.compose.ui.test.waitUntil
import androidx.compose.ui.uikit.embedSubview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import platform.UIKit.UIView
import platform.UIKit.UIViewController

class ComposeViewIntegrationTest {
    @Test
    fun testComposeViewSceneStartAndStop() {
        val appDelegate = MockAppDelegate()
        val controller = UIViewController()
        appDelegate.setUpWindow(controller)
        var launchesCount = 0

        val composeView = ComposeView(
            configure = {
                enforceStrictPlistSanityCheck = false
            }
        ) {
            LaunchedEffect(Unit) {
                launchesCount += 1
            }
        } as ComposeHostingView

        waitUntil { controller.view.window != null }

        controller.view.embedSubview(composeView)
        waitUntil { !composeView.hasInvalidations() }

        assertEquals(launchesCount, 1)

        composeView.removeFromSuperview()

        assertEquals(launchesCount, 1)
        waitUntil("Wait until compose view being disposed") {
            composeView.rootRedrawer == null
        }
    }

    @Test
    fun testComposeViewSceneMovedToNewSubview() {
        val appDelegate = MockAppDelegate()
        val controller = UIViewController()
        val anotherSubview = UIView()
        appDelegate.setUpWindow(controller)
        var compositionsCount = 0

        val composeView = ComposeView(
            configure = {
                enforceStrictPlistSanityCheck = false
            }
        ) {
            compositionsCount += 1
        } as ComposeHostingView

        waitUntil { controller.view.window != null }

        controller.view.embedSubview(composeView)
        waitUntil { !composeView.hasInvalidations() }
        assertEquals(compositionsCount, 1)

        composeView.removeFromSuperview()
        controller.view.embedSubview(anotherSubview)
        anotherSubview.embedSubview(composeView)

        // Long delay to be sure that the Compose scene is not disposed
        delay(1000)

        assertNotNull(composeView.rootRedrawer, "ComposeView should be alive")
        assertEquals(1, compositionsCount, "Compose view should not have extra recompositions")
    }
}