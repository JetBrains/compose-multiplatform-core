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

package androidx.compose.ui.draganddrop

import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.dragAndDrop
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.compose.ui.test.SchedulingDispatcherFixture
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@OptIn(ExperimentalTestApi::class)
class DragAndDropTest {

    private val schedulingDispatcher = SchedulingDispatcherFixture()

    @BeforeTest
    fun installSchedulingDispatcher() {
        schedulingDispatcher.install()
    }

    @AfterTest
    fun uninstallSchedulingDispatcher() {
        schedulingDispatcher.uninstall()
    }

    @Test
    fun mouseDragTriggersDragStart() = runComposeUiTest {
        var transferDataCreated = false
        setContent {
            Box(
                Modifier
                    .testTag("dragSource")
                    .size(100.dp)
                    .dragAndDropSource(
                        transferData = {
                            transferDataCreated = true
                            null
                        }
                    )
            )
        }

        assertFalse(transferDataCreated)

        onNodeWithTag("dragSource").performMouseInput {
            dragAndDrop(
                start = Offset(1f, 1f),
                end = Offset(99f, 99f),
            )
        }
        assertTrue(transferDataCreated)
    }

    @Test
    fun mouseLongPressDoesNotTriggerDragStart() = runComposeUiTest {
        var transferDataCreated = false
        setContent {
            Box(
                Modifier
                    .testTag("dragSource")
                    .size(100.dp)
                    .dragAndDropSource(
                        drawDragDecoration = {},
                        transferData = {
                            println("Triggered")
                            transferDataCreated = true
                            null
                        }
                    )
            )
        }

        assertFalse(transferDataCreated)

        onNodeWithTag("dragSource").performMouseInput {
            longClick()
        }
        assertFalse(transferDataCreated)
    }

    @Test
    fun touchLongPressTriggersDragStart() = runComposeUiTest {
        var transferDataCreated = false
        setContent {
            Box(
                Modifier
                    .testTag("dragSource")
                    .size(100.dp)
                    .dragAndDropSource(
                        transferData = {
                            transferDataCreated = true
                            null
                        }
                    )
            )
        }

        assertFalse(transferDataCreated)

        onNodeWithTag("dragSource").performTouchInput {
            longClick()
        }
        assertTrue(transferDataCreated)
    }

    @Test
    fun touchDragDoesNotTriggerDragStart() = runComposeUiTest {
        var transferDataCreated = false
        setContent {
            Box(
                Modifier
                    .testTag("dragSource")
                    .size(100.dp)
                    .dragAndDropSource(
                        drawDragDecoration = {},
                        transferData = {
                            println("Triggered")
                            transferDataCreated = true
                            null
                        }
                    )
            )
        }

        assertFalse(transferDataCreated)

        onNodeWithTag("dragSource").performTouchInput {
            down(Offset(1f, 1f))
            moveTo(Offset(99f, 99f))
            up()
        }
        assertFalse(transferDataCreated)
    }
}