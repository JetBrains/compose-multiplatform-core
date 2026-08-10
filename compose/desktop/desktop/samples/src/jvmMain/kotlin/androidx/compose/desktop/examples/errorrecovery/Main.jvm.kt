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

package androidx.compose.desktop.examples.errorrecovery

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.runApplicationBlocking
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A manual test for recovering from an exception thrown out of a composable function.
 *
 * The window shows a continuously spinning bar and a single button. Pressing the button arms a
 * one-shot trigger, and the recomposition it schedules throws from [CrashOnDemand]. The exception
 * is thrown ONCE per press - the trigger disarms itself before throwing - so whatever recovers the
 * composition gets a chance to compose the same content successfully on the retry, instead of
 * hitting the same failure again and turning the sample into a crash loop.
 *
 * What to look for after a press:
 *  - the exception is reported once, not in a loop;
 *  - the bar keeps spinning (it restarts from 0 degrees, because recovery re-creates the
 *    composition and everything `remember`ed in it);
 *  - the window stays interactive and the button can be pressed again for another single crash.
 *
 * A frozen window with a dead `Exception in thread "... Main Thread (KDT)"` in the console means
 * the failure was never recovered from: the exception escaped the recomposer coroutine, which is
 * resumed from `BaseComposeScene.render`, so it is NOT delivered to the `catch` around
 * `composeScene.render` in `MacOsWindow.preparePicture` and no hot reload is triggered.
 *
 * Run: `./gradlew :compose:desktop:desktop:desktop-samples:runErrorRecovery`
 */
fun main() {
    runApplicationBlocking(
        identifier = System.getProperty("kdt.application.identifier") ?: "compose-error-recovery",
    ) {
        AppWindow()
    }
}

@Composable
private fun AppWindow() {
    var isWindowShown by remember { mutableStateOf(true) }
    if (isWindowShown) {
        Window(
            onCloseRequest = { _ ->
                isWindowShown = false
            },
            configure = {
                title = "Error recovery"
                requestSize(DpSize(420.dp, 320.dp))
            },
        ) {
            ErrorRecoveryContent()
        }
    }
}

/**
 * The one-shot crash trigger.
 *
 * [armed] and [thrownCount] are plain fields on purpose. The composition that throws is abandoned
 * and its snapshot is never applied, so a snapshot-state write made from the failing composable
 * would be rolled back - the trigger would still be armed on the retry and the sample would crash
 * in an endless loop instead of once. Plain fields survive that rollback, and they also survive the
 * hot reload that recovers the composition, which discards everything `remember`ed.
 */
private object CrashTrigger {
    /**
     * Snapshot state, read by [CrashOnDemand]: writing it from the button is what schedules the
     * recomposition that then throws.
     */
    var requests by mutableStateOf(0)
        private set

    private var armed = false
    private var thrownCount = 0

    val thrown: Int
        get() = thrownCount

    fun arm() {
        armed = true
        requests++
    }

    /** Returns `true` at most once per [arm] call. */
    fun consumeArmed(): Boolean {
        if (!armed) return false
        armed = false
        thrownCount++
        return true
    }
}

@Composable
private fun ErrorRecoveryContent() = MaterialTheme {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Spinner()
        Button(onClick = { CrashTrigger.arm() }) {
            Text("Throw from the next recomposition")
        }
        CrashOnDemand()
    }
}

/**
 * The liveness indicator: an infinite rotation read inside [graphicsLayer], so it animates without
 * recomposing anything. A recomposition here is therefore always the button's doing, and a spinning
 * bar after a crash means the scene really did recover rather than just keeping the last frame.
 */
@Composable
private fun Spinner() {
    val transition = rememberInfiniteTransition()
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing)
        ),
    )
    Box(
        Modifier
            .size(80.dp)
            .background(Color.Green),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .graphicsLayer { rotationZ = angle }
                .size(60.dp, 10.dp)
                .background(Color.Red)
        )
    }
}

/** The composable that fails. */
@Composable
private fun CrashOnDemand() {
    // Subscribes this scope to the button: arming the trigger schedules exactly one recomposition,
    // and that recomposition is where the exception comes from.
    val requests = CrashTrigger.requests
    if (CrashTrigger.consumeArmed()) {
        error("Simulated composition failure #${CrashTrigger.thrown} (request #$requests)")
    }
    // `thrown` is not snapshot state, so it is only re-read when something else recomposes this
    // scope - which the recovery of a crash always does.
    Text(
        text = "requests: $requests, exceptions thrown: ${CrashTrigger.thrown}",
        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
    )
}
