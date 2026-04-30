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

package androidx.compose.ui.window.v2

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.plus
import androidx.compose.ui.unit.size
import androidx.compose.ui.window.runApplicationTest
import androidx.compose.ui.window.toDpInsets
import kotlin.test.Test
import kotlin.test.assertEquals

class DialogWindowV2StateTest {

    private fun runDialogSizeTest(
        testName: String,
        sizeProvider: WindowSizeProvider,
        content: @Composable () -> Unit,
        expectedWindowSizeSansInsets: DpSize,
    ) = runApplicationTest {
        val dialogState = DialogState(
            initialBoundsProvider = WindowBoundsProvider(sizeProvider)
        )
        lateinit var dialog: ComposeDialog
        launchTestApplication {
            DialogWindow(
                state = dialogState,
                onCloseRequest = {},
                title = testName
            ) {
                dialog = this.window
                content()
            }
        }
        awaitIdle()
        assertEquals(
            expectedWindowSizeSansInsets + dialog.insets.toDpInsets(),
            dialogState.bounds.size
        )
    }

    @Test
    fun dialogMinIntrinsicWidth() = runDialogSizeTest(
        testName = "windowMinIntrinsicWidth",
        sizeProvider = WindowSizeProvider.MinIntrinsicWidth(height = 500.dp),
        content = {
            BoxWithIntrinsicSize(
                minWidth = { 400.dp.roundToPx() }
            )
        },
        expectedWindowSizeSansInsets = DpSize(400.dp, 500.dp)
    )

    @Test
    fun windowMaxIntrinsicWidth() = runDialogSizeTest(
        testName = "windowMaxIntrinsicWidth",
        sizeProvider = WindowSizeProvider.MaxIntrinsicWidth(height = 500.dp),
        content = {
            BoxWithIntrinsicSize(
                maxWidth = { 400.dp.roundToPx() }
            )
        },
        expectedWindowSizeSansInsets = DpSize(400.dp, 500.dp)
    )

    @Test
    fun windowMinIntrinsicHeight() = runDialogSizeTest(
        testName = "windowMinIntrinsicHeight",
        sizeProvider = WindowSizeProvider.MinIntrinsicHeight(width = 500.dp),
        content = {
            BoxWithIntrinsicSize(
                minHeight = { 400.dp.roundToPx() }
            )
        },
        expectedWindowSizeSansInsets = DpSize(500.dp, 400.dp)
    )

    @Test
    fun windowMaxIntrinsicHeight() = runDialogSizeTest(
        testName = "windowMaxIntrinsicHeight",
        sizeProvider = WindowSizeProvider.MaxIntrinsicHeight(width = 500.dp),
        content = {
            BoxWithIntrinsicSize(
                maxHeight = { 400.dp.roundToPx() }
            )
        },
        expectedWindowSizeSansInsets = DpSize(500.dp, 400.dp)
    )

    @Test
    fun windowMinWidthWithMatchingMinHeight() = runDialogSizeTest(
        testName = "windowMinWidthWithMatchingMinHeight",
        sizeProvider = WindowSizeProvider.IntrinsicWidthWithMatchingIntrinsicHeight(
            intrinsicWidth = WindowIntrinsicSize.Min,
            intrinsicHeight = WindowIntrinsicSize.Min,
        ),
        content = {
            BoxWithIntrinsicSize(
                minWidth = { 400.dp.roundToPx() },
                minHeight = { it }  // Return width to make it a square
            )
        },
        expectedWindowSizeSansInsets = DpSize(400.dp, 400.dp)
    )

    @Test
    fun windowMaxHeightWithMatchingMaxWidth() = runDialogSizeTest(
        testName = "windowMaxHeightWithMatchingMaxWidth",
        sizeProvider = WindowSizeProvider.IntrinsicHeightWithMatchingIntrinsicWidth(
            intrinsicWidth = WindowIntrinsicSize.Max,
            intrinsicHeight = WindowIntrinsicSize.Max,
        ),
        content = {
            BoxWithIntrinsicSize(
                maxHeight = { 400.dp.roundToPx() },
                maxWidth = { it }  // Return height to make it a square
            )
        },
        expectedWindowSizeSansInsets = DpSize(400.dp, 400.dp)
    )

    @Test
    fun `requested size is rounded up`() = runDialogSizeTest(
        testName = "requested size is rounded up",
        sizeProvider = WindowSizeProvider.IntrinsicWidthWithMatchingIntrinsicHeight(
            intrinsicWidth = WindowIntrinsicSize.Min,
            intrinsicHeight = WindowIntrinsicSize.Min,
        ),
        content = {
            BoxWithIntrinsicSize(
                minWidth = { (density * 100 + 1).toInt() },
                minHeight = { it }
            )
        },
        expectedWindowSizeSansInsets = DpSize(101.dp, 101.dp)
    )
}