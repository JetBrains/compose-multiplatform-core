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

package org.jetbrains.compose.benchmarks.app

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun App(bechmarkCollectorUrl: String, testName: String) {
    BasicText(
        text = "bechmarkCollectorUrl: $bechmarkCollectorUrl\nTest: $testName",
        modifier = Modifier.padding(16.dp),
    )
}

internal fun parseArguments(args: Array<String>): Pair<String, String> {
    require(args.size == 2) { "Usage: benchmarks-app <bechmarkCollectorUrl> <test-name>" }
    return args[0] to args[1]
}
