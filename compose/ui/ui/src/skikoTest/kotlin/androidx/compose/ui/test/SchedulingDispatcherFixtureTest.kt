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

package androidx.compose.ui.test

import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.ComposeUIDispatcherOverride
import androidx.compose.ui.asComposeUiMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SchedulingDispatcherFixtureTest {
    private val fixture = SchedulingDispatcherFixture()

    @AfterTest
    fun tearDown() {
        fixture.uninstall()
    }

    @Test
    fun installMakesTheDispatcherResolveAndUninstallRestoresANullPrevious() {
        ComposeUIDispatcherOverride = null
        fixture.install()
        // While installed, ComposeUIDispatcher resolves to exactly what the fixture put in the
        // override, not the KDT fallback.
        assertSame(ComposeUIDispatcherOverride, ComposeUIDispatcher)
        fixture.uninstall()
        assertNull(ComposeUIDispatcherOverride, "uninstall must restore the previous override")
    }

    @Test
    fun installMakesTheDispatcherResolveAndUninstallRestoresANonNullPrevious() {
        // A wrapped Dispatchers.IO is trivially identity-comparable and, unlike what install()
        // itself installs, serves as an unambiguous sentinel for "whatever the previous override
        // was". It must be main-typed to sit in ComposeUIDispatcherOverride.
        val sentinel = Dispatchers.IO.asComposeUiMainDispatcher()
        ComposeUIDispatcherOverride = sentinel

        fixture.install()
        // While installed, the override is the fixture's dispatcher, not the sentinel: a regression
        // that left the sentinel in place instead of swapping it would fail here.
        assertNotSame(sentinel, ComposeUIDispatcherOverride)

        fixture.uninstall()
        // A regression where uninstall() hardcodes null instead of restoring `previous` would fail
        // here: it would see null, not the sentinel.
        assertSame(
            sentinel,
            ComposeUIDispatcherOverride,
            "uninstall must restore the previous override",
        )
    }

    @Test
    fun secondInstallFollowedBySingleUninstallRestoresTheOriginalNotTheIntermediate() {
        val original = Dispatchers.IO.asComposeUiMainDispatcher()
        ComposeUIDispatcherOverride = original

        fixture.install()
        fixture.install()
        // Reentrant install: the override keeps resolving to a fixture-installed dispatcher, not
        // back to the original, and one uninstall() must still be enough to fully restore it.
        assertNotSame(original, ComposeUIDispatcherOverride)

        fixture.uninstall()
        assertSame(
            original,
            ComposeUIDispatcherOverride,
            "a single uninstall() after two install() calls must restore the original value, not the intermediate one",
        )
    }

    @Test
    fun reinstallingControlledBetweenInstallsStillRestoresTheOriginalNotTheIntermediate() {
        // installControlled() creates a fresh StandardTestDispatcher/scheduler pair on every call,
        // so this is where "not the intermediate" is actually distinguishable by reference.
        val original = Dispatchers.IO.asComposeUiMainDispatcher()
        ComposeUIDispatcherOverride = original

        fixture.installControlled()
        val afterFirstInstall = ComposeUIDispatcherOverride
        fixture.installControlled()
        assertNotSame(original, ComposeUIDispatcherOverride)
        assertNotSame(afterFirstInstall, ComposeUIDispatcherOverride)

        fixture.uninstall()
        assertSame(
            original,
            ComposeUIDispatcherOverride,
            "a single uninstall() after two installControlled() calls must restore the original, not the intermediate",
        )
    }

    @Test
    fun installRunsPostedWorkInlineNotDeferred() {
        fixture.install()
        var ran = false
        CoroutineScope(ComposeUIDispatcher).launch { ran = true }
        assertTrue(ran, "install() must run posted work inline, or scenes built inside a " +
            "blocking runOnUiThread/invokeAndWait call will deadlock")
    }

    @Test
    fun installControlledDefersPostedWorkUntilTheSchedulerAdvances() {
        val scheduler = fixture.installControlled()
        var ran = false
        CoroutineScope(ComposeUIDispatcher).launch { ran = true }
        assertFalse(ran, "installControlled() must defer posted work until the scheduler advances")
        scheduler.advanceUntilIdle()
        assertTrue(ran)
    }

    @Test
    fun schedulerIsUnavailableAfterAPlainInstall() {
        fixture.install()
        assertFailsWith<IllegalStateException> { fixture.scheduler }
    }
}
