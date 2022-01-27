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

package androidx.compose.ui.input.mouse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalComposeUiApi::class)
@RunWith(JUnit4::class)
class MouseHoverFilterTest {
    @Test
    fun `inside window`() = ImageComposeScene(
        width = 100,
        height = 100,
        density = Density(2f)
    ).use { scene ->
        var moveCount = 0
        var enterCount = 0
        var exitCount = 0

        scene.setContent {
            Box(
                modifier = Modifier
                    .pointerMove(
                        onMove = {
                            moveCount++
                        },
                        onEnter = {
                            enterCount++
                        },
                        onExit = {
                            exitCount++
                        }
                    )
                    .size(10.dp, 20.dp)
            )
        }

        scene.sendPointerEvent(PointerEventType.Enter, Offset(0f, 0f))
        scene.sendPointerEvent(PointerEventType.Move, Offset(10f, 20f))
        assertThat(enterCount).isEqualTo(1)
        assertThat(exitCount).isEqualTo(0)
        assertThat(moveCount).isEqualTo(1)

        scene.sendPointerEvent(PointerEventType.Move, Offset(10f, 15f))
        assertThat(enterCount).isEqualTo(1)
        assertThat(exitCount).isEqualTo(0)
        assertThat(moveCount).isEqualTo(2)

        scene.sendPointerEvent(PointerEventType.Move, Offset(30f, 30f))
        assertThat(enterCount).isEqualTo(1)
        assertThat(exitCount).isEqualTo(1)
        assertThat(moveCount).isEqualTo(2)
    }

    @Test
    fun `window enter`() = ImageComposeScene(
        width = 100,
        height = 100,
        density = Density(2f)
    ).use { scene ->
        var moveCount = 0
        var enterCount = 0
        var exitCount = 0

        scene.setContent {
            Box(
                modifier = Modifier
                    .pointerMove(
                        onMove = {
                            moveCount++
                        },
                        onEnter = {
                            enterCount++
                        },
                        onExit = {
                            exitCount++
                        }
                    )
                    .size(10.dp, 20.dp)
            )
        }

        scene.sendPointerEvent(PointerEventType.Enter, Offset(10f, 20f))
        assertThat(enterCount).isEqualTo(1)
        assertThat(exitCount).isEqualTo(0)
        assertThat(moveCount).isEqualTo(0)

        scene.sendPointerEvent(PointerEventType.Exit, Offset(-1f, -1f))
        assertThat(enterCount).isEqualTo(1)
        assertThat(exitCount).isEqualTo(1)
        assertThat(moveCount).isEqualTo(0)
    }

    @Test
    fun `move from one component to another`() = ImageComposeScene(
        width = 100,
        height = 100,
        density = Density(1f)
    ).use { scene ->
        var enterCount1 = 0
        var exitCount1 = 0
        var enterCount2 = 0
        var exitCount2 = 0

        scene.setContent {
            Column {
                Box(
                    modifier = Modifier
                        .pointerMove(
                            onEnter = { enterCount1++ },
                            onExit = { exitCount1++ }
                        )
                        .size(10.dp, 10.dp)
                )
                Box(
                    modifier = Modifier
                        .pointerMove(
                            onEnter = { enterCount2++ },
                            onExit = { exitCount2++ }
                        )
                        .size(10.dp, 10.dp)
                )
            }
        }

        scene.sendPointerEvent(PointerEventType.Enter, Offset(5f, 5f))
        assertThat(enterCount1).isEqualTo(1)
        assertThat(exitCount1).isEqualTo(0)
        assertThat(enterCount2).isEqualTo(0)
        assertThat(exitCount2).isEqualTo(0)

        scene.sendPointerEvent(PointerEventType.Move, Offset(5f, 15f))
        assertThat(enterCount1).isEqualTo(1)
        assertThat(exitCount1).isEqualTo(1)
        assertThat(enterCount2).isEqualTo(1)
        assertThat(exitCount2).isEqualTo(0)
    }

    @Test
    fun `don't send move with the same position`() = ImageComposeScene(
        width = 100,
        height = 100,
        density = Density(1f)
    ).use { scene ->
        var moveCount1 = 0
        var enterCount1 = 0
        var exitCount1 = 0
        var moveCount2 = 0
        var enterCount2 = 0
        var exitCount2 = 0

        scene.setContent {
            Column {
                Box(
                    modifier = Modifier
                        .pointerMove(
                            onMove = { moveCount1++ },
                            onEnter = { enterCount1++ },
                            onExit = { exitCount1++ }
                        )
                        .size(10.dp, 10.dp)
                )
                Box(
                    modifier = Modifier
                        .pointerMove(
                            onMove = { moveCount2++ },
                            onEnter = { enterCount2++ },
                            onExit = { exitCount2++ }
                        )
                        .size(10.dp, 10.dp)
                )
            }
        }

        scene.sendPointerEvent(PointerEventType.Enter, Offset(5f, 5f))
        assertThat(listOf(moveCount1, enterCount1, exitCount1)).isEqualTo(listOf(0, 1, 0))
        assertThat(listOf(moveCount2, enterCount2, exitCount2)).isEqualTo(listOf(0, 0, 0))

        scene.sendPointerEvent(PointerEventType.Move, Offset(5f, 5f))
        assertThat(listOf(moveCount1, enterCount1, exitCount1)).isEqualTo(listOf(0, 1, 0))
        assertThat(listOf(moveCount2, enterCount2, exitCount2)).isEqualTo(listOf(0, 0, 0))

        scene.sendPointerEvent(PointerEventType.Move, Offset(5f, 5f))
        assertThat(listOf(moveCount1, enterCount1, exitCount1)).isEqualTo(listOf(0, 1, 0))
        assertThat(listOf(moveCount2, enterCount2, exitCount2)).isEqualTo(listOf(0, 0, 0))

        scene.sendPointerEvent(PointerEventType.Move, Offset(5f, 15f))
        assertThat(listOf(moveCount1, enterCount1, exitCount1)).isEqualTo(listOf(0, 1, 1))
        assertThat(listOf(moveCount2, enterCount2, exitCount2)).isEqualTo(listOf(0, 1, 0))

        scene.sendPointerEvent(PointerEventType.Move, Offset(5f, 15f))
        assertThat(listOf(moveCount1, enterCount1, exitCount1)).isEqualTo(listOf(0, 1, 1))
        assertThat(listOf(moveCount2, enterCount2, exitCount2)).isEqualTo(listOf(0, 1, 0))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `send multiple move events with paused dispatcher`() {
        var moveCount = 0
        var enterCount = 0
        var exitCount = 0

        val dispatcher = TestCoroutineDispatcher().apply {
            pauseDispatcher()
        }

        ImageComposeScene(
            width = 100,
            height = 100,
            density = Density(1f),
            coroutineContext = dispatcher
        ).use { scene ->
            scene.setContent {
                Box(
                    modifier = Modifier
                        .pointerMove(
                            onMove = {
                                moveCount++
                            },
                            onEnter = {
                                enterCount++
                            },
                            onExit = {
                                exitCount++
                            }
                        )
                        .size(20.dp, 20.dp)
                )
            }
            dispatcher.advanceUntilIdle()

            scene.sendPointerEvent(PointerEventType.Enter, Offset(5f, 10f))
            scene.sendPointerEvent(PointerEventType.Move, Offset(6f, 10f))
            scene.sendPointerEvent(PointerEventType.Move, Offset(7f, 10f))
            scene.sendPointerEvent(PointerEventType.Press, Offset(9f, 10f))
            dispatcher.advanceUntilIdle()

            assertThat(enterCount).isEqualTo(1)
            assertThat(exitCount).isEqualTo(0)
            assertThat(moveCount).isEqualTo(2)

            scene.sendPointerEvent(PointerEventType.Move, Offset(100f, 10f))
            dispatcher.advanceUntilIdle()

            assertThat(enterCount).isEqualTo(1)
            assertThat(exitCount).isEqualTo(1)
            assertThat(moveCount).isEqualTo(2)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.pointerMove(
    onMove: () -> Unit = {},
    onExit: () -> Unit = {},
    onEnter: () -> Unit = {},
): Modifier = this
    .onPointerEvent(PointerEventType.Move) { onMove() }
    .onPointerEvent(PointerEventType.Exit) { onExit() }
    .onPointerEvent(PointerEventType.Enter) { onEnter() }