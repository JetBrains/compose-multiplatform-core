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

package androidx.compose.ui.interaction.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.findNodeWithTagOrNull
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.utils.hold
import androidx.compose.ui.test.utils.leftCenter
import androidx.compose.ui.test.utils.offsetBy
import androidx.compose.ui.test.utils.rightCenter
import androidx.compose.ui.test.utils.up
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import androidx.navigationevent.compose.NavigationEventState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class NavigationDrawerBackGestureInHostingViewTest : NavigationDrawerBackGestureTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = true, it) }
)

internal class NavigationDrawerBackGestureInHostingViewControllerTest : NavigationDrawerBackGestureTest(
    runUIKitInstrumentedTest = { runUIKitInstrumentedTest(useHostingView = false, it) }
)

internal abstract class NavigationDrawerBackGestureTest(
    private val runUIKitInstrumentedTest: (UIKitInstrumentedTest.() -> Unit) -> Unit
) {
    @Test
    fun edgeBackSwipeDoesNotRevealDrawer() = runUIKitInstrumentedTest {
        var drawerOffset = Float.NaN
        lateinit var backStack: SnapshotStateList<String>
        lateinit var navigationEventState: NavigationEventState<SceneInfo<String>>

        setContent {
            TestContent(
                onDrawerOffsetChanged = { offset ->
                    drawerOffset = offset
                },
                onBackStackChanged = { backStack = it },
                onNavigationEventStateChanged = { navigationEventState = it }
            )
        }

        waitUntil("navigation drawer screen should be visible") {
            backStack.size == 2 &&
                findNodeWithTagOrNull(NAVIGATION_DRAWER_SCREEN) != null &&
                !drawerOffset.isNaN()
        }

        val closedDrawerOffset = drawerOffset

        val backSwipe = swipeRightFromEdge().hold()

        waitUntil("back swipe should be in progress") {
            navigationEventState.transitionState is InProgress
        }

        assertFalse(
            drawerOffset > closedDrawerOffset,
            "Drawer moved away from its closed anchor during the back swipe"
        )
        assertEquals(
            2, backStack.size,
            "Back swipe should not pop the top screen before release"
        )

        backSwipe.up()

        waitUntil("back swipe should return to the base screen") {
            backStack.size == 1 && findNodeWithTagOrNull(BASE_SCREEN) != null
        }
    }

    @Test
    fun innerSwipeRightRevealsDrawer() = runUIKitInstrumentedTest {
        var drawerOffset = Float.NaN
        lateinit var backStack: SnapshotStateList<String>
        lateinit var navigationEventState: NavigationEventState<SceneInfo<String>>

        setContent {
            TestContent(
                onDrawerOffsetChanged = { offset ->
                    drawerOffset = offset
                },
                onBackStackChanged = { backStack = it },
                onNavigationEventStateChanged = { navigationEventState = it }
            )
        }

        waitUntil("navigation drawer screen should be visible") {
            backStack.size == 2 &&
                findNodeWithTagOrNull(NAVIGATION_DRAWER_SCREEN) != null &&
                !drawerOffset.isNaN()
        }

        val closedDrawerOffset = drawerOffset

        findNodeWithTag(NAVIGATION_DRAWER_SCREEN).swipe(
            fromPosition = { leftCenter().offsetBy(dx = 16.dp) },
            toPosition = { rightCenter().offsetBy(dx = (-16).dp) }
        )

        waitUntil("drawer should move away from the closed anchor") {
            drawerOffset > closedDrawerOffset
        }

        assertEquals(
            2, backStack.size,
            "Drawer swipe should not pop the top screen"
        )
        assertFalse(
            navigationEventState.transitionState is InProgress,
            "Drawer swipe should not start back navigation"
        )
    }
}

@Composable
private fun TestContent(
    onDrawerOffsetChanged: (Float) -> Unit,
    onBackStackChanged: (SnapshotStateList<String>) -> Unit,
    onNavigationEventStateChanged: (NavigationEventState<SceneInfo<String>>) -> Unit,
) {
    MaterialTheme {
        val backStack = remember { mutableStateListOf(BASE_SCREEN, NAVIGATION_DRAWER_SCREEN) }
        val onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
        val entries = rememberDecoratedNavEntries(backStack) { screen ->
            when (screen) {
                BASE_SCREEN ->
                    NavEntry(BASE_SCREEN) {
                        BaseScreen()
                    }

                NAVIGATION_DRAWER_SCREEN ->
                    NavEntry(NAVIGATION_DRAWER_SCREEN) {
                        NavigationDrawerScreen(
                            onDrawerOffsetChanged = onDrawerOffsetChanged
                        )
                    }

                else -> error("Unexpected screen: $screen")
            }
        }
        val sceneState = rememberSceneState(
            entries,
            listOf(SinglePaneSceneStrategy()),
            onBack = onBack
        )
        val navigationEventState = rememberNavigationEventState(sceneState)

        onBackStackChanged(backStack)
        onNavigationEventStateChanged(navigationEventState)

        NavigationBackHandler(
            sceneState = sceneState,
            state = navigationEventState,
            onBackCompleted = onBack
        )

        NavDisplay(
            sceneState = sceneState,
            navigationEventState = navigationEventState,
            modifier = Modifier.fillMaxSize().background(Color.White)
        )
    }
}

@Composable
private fun BaseScreen() {
    Box(
        modifier = Modifier.fillMaxSize().testTag(BASE_SCREEN),
    )
}

@Composable
private fun NavigationDrawerScreen(
    onDrawerOffsetChanged: (Float) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.currentOffset }.collect { onDrawerOffsetChanged(it) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.Red) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag(NAVIGATION_DRAWER_SCREEN),
        ) {}
    }
}

private const val BASE_SCREEN = "BaseScreen"
private const val NAVIGATION_DRAWER_SCREEN = "NavigationDrawerScreen"
