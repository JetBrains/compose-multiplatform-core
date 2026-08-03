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

package androidx.compose.ui.desktop.headless

import androidx.compose.runtime.Composable
import androidx.compose.ui.ComposeSchedulingDispatcher
import androidx.compose.ui.ComposeUIDispatcherOverride
import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.desktop.Application
import androidx.compose.ui.desktop.ApplicationSession
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.Screen
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.activateApplication
import androidx.compose.ui.desktop.deactivateApplication
import androidx.compose.ui.platform.ClipEntry
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Guards [HeadlessApplication.initialize]'s exception-safety: activation is validated BEFORE the
 * event loop and dispatcher overrides are installed, so calling headless initialize while another
 * backend already owns the JVM must be rejected WITHOUT clobbering that live backend's dispatchers.
 *
 * This test fails against the pre-fix ordering (install-then-activate): there,
 * [ComposeUIDispatcherOverride] is overwritten before the rejecting `activateApplication(this)`
 * throws, so the assertions below observe a non-null override.
 */
@Category(HeadlessTest::class)
class HeadlessInitializeActivationOrderTest {

    /** A registry-only [Application] double: it is activated so the JVM has an owner, and is never invoked. */
    private object StubApplication : Application {
        override val systemTheme: SystemTheme = SystemTheme.Light
        override val windows: Map<LightweightWindowId, Window> = emptyMap()
        override val focusedWindow: Window? = null
        override val isActive: Boolean = false
        override val screens: Map<out Any, Screen> = emptyMap()
        override val nativeApplication: Any = Unit
        override val nativeClipboard: Any = Unit

        override fun createWindow(
            session: ApplicationSession,
            onCloseRequest: (WindowCloseRequestReason) -> Unit,
        ): Window = error("StubApplication members must never be invoked")

        override fun prepareNativeWindowResourcesForReuse(id: LightweightWindowId): Unit =
            error("StubApplication members must never be invoked")

        override fun reuseWindow(
            id: LightweightWindowId,
            session: ApplicationSession,
            onCloseRequest: (WindowCloseRequestReason) -> Unit,
        ): Window? = error("StubApplication members must never be invoked")

        override fun disposeReusableNativeWindowResources(id: LightweightWindowId): Unit =
            error("StubApplication members must never be invoked")

        override fun requestActivation(): Unit = error("StubApplication members must never be invoked")
        override fun showEmojiAndSymbolsPopup(): Unit = error("StubApplication members must never be invoked")
        override fun quit(): Unit = error("StubApplication members must never be invoked")
        override fun putQuitHandler(id: String, quitHandler: () -> Boolean): Unit =
            error("StubApplication members must never be invoked")

        override fun removeQuitHandler(id: String): Unit = error("StubApplication members must never be invoked")
        override suspend fun awaitWhenReady(): Unit = error("StubApplication members must never be invoked")
        override suspend fun stopAndJoin(): Unit = error("StubApplication members must never be invoked")
        override fun close(): Unit = error("StubApplication members must never be invoked")

        @Composable
        override fun withCompositionLocal(content: @Composable () -> Unit): Unit =
            error("StubApplication members must never be invoked")

        override suspend fun getClipEntry(): ClipEntry? = error("StubApplication members must never be invoked")
        override suspend fun setClipEntry(clipEntry: ClipEntry?): Unit =
            error("StubApplication members must never be invoked")

        override fun openUri(uri: String): Unit = error("StubApplication members must never be invoked")
    }

    @Test
    fun initializeDoesNotClobberDispatchersWhenAnotherApplicationIsActive() {
        val overrideBefore = ComposeUIDispatcherOverride
        val schedulingBefore = ComposeSchedulingDispatcher
        activateApplication(StubApplication)
        try {
            assertFailsWith<IllegalStateException> {
                HeadlessApplication.initialize(System.getProperty("java.io.tmpdir"))
            }
            // The rejected activation must run before any dispatcher install, so the live backend's
            // dispatcher globals stay exactly as they were.
            assertNull(
                ComposeUIDispatcherOverride,
                "initialize() clobbered ComposeUIDispatcherOverride despite the activation being rejected",
            )
            assertSame(schedulingBefore, ComposeSchedulingDispatcher)
        } finally {
            deactivateApplication(StubApplication)
            // Full global-state restore (defensive; both are null in a fresh forkEvery=1 JVM).
            ComposeUIDispatcherOverride = overrideBefore
            ComposeSchedulingDispatcher = schedulingBefore
        }
    }
}
