/*
 * Copyright 2023 The Android Open Source Project
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

import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import kotlin.test.Test
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class ProvidesLocalsTest {

    @Suppress("DEPRECATION")
    @Test
    fun providesWindowInfo() = runComposeUiTest {
        lateinit var windowInfo: WindowInfo
        setContent {
            windowInfo = LocalWindowInfo.current
        }
        assertNotEquals(0, windowInfo.containerSize.width)
        assertNotEquals(0, windowInfo.containerSize.height)
    }
}