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

package androidx.xr.glimmer

import android.os.Build.VERSION.SDK_INT
import androidx.compose.ui.input.InputMode
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Enters non-touch mode for tests, so that Glimmer's clickables can be focused. */
internal fun nonTouchInputModeRule(): InputModeRule = InputModeRule(InputMode.Keyboard)

/**
 * Sets up a given [inputMode] before the test and guarantees to reset it afterwards. Useful for
 * tests with non-touch input like TV.
 */
class InputModeRule(val inputMode: InputMode) : ExternalResource() {

    override fun before() {
        // TODO(b/267253920): Add a compose test API to set/reset InputMode.
        when (inputMode) {
            InputMode.Touch -> InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
            InputMode.Keyboard -> InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            else -> error("Unexpected input mode [$inputMode].")
        }
    }

    override fun after() {
        // TODO(b/267253920): Add a compose test API to set/reset InputMode.
        if (SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().resetInTouchMode()
        } else {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        }
    }
}
