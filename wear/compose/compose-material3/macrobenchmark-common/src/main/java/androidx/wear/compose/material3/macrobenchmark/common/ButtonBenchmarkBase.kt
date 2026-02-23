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

package androidx.wear.compose.material3.macrobenchmark.common

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By

interface ButtonBenchmarkBase : MacrobenchmarkScreen {
    override val exercise: MacrobenchmarkScope.() -> Unit
        get() = {
            val buttons = buildList {
                repeat(4) { add(device.findObject(By.desc(numberedContentDescription(it)))) }
            }
            repeat(3) {
                for (button in buttons) {
                    button.click(50)
                    device.waitForIdle()
                }
                SystemClock.sleep(500)
            }
        }
}
