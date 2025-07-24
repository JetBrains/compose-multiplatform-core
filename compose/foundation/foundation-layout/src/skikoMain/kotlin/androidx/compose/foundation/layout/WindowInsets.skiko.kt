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

package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalPlatformWindowInsetsConfig
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.safeContent
import androidx.compose.ui.platform.safeDrawing
import androidx.compose.ui.platform.safeGestures
import kotlin.jvm.JvmName

actual val WindowInsets.Companion.captionBar: WindowInsets
    @Composable
    @OptIn(InternalComposeUiApi::class)
    get() = LocalPlatformWindowInsetsConfig.current.captionBar.toWindowInsets()

actual val WindowInsets.Companion.displayCutout: WindowInsets
    @Composable
    @OptIn(InternalComposeUiApi::class)
    get() = LocalPlatformWindowInsetsConfig.current.displayCutout.toWindowInsets()

actual val WindowInsets.Companion.ime: WindowInsets
    @Composable
    @OptIn(InternalComposeUiApi::class)
    get() = LocalPlatformWindowInsetsConfig.current.ime.toWindowInsets()

actual val WindowInsets.Companion.mandatorySystemGestures: WindowInsets
    @Composable
    @OptIn(InternalComposeUiApi::class)
    get() = LocalPlatformWindowInsetsConfig.current.mandatorySystemGestures.toWindowInsets()

actual val WindowInsets.Companion.navigationBars: WindowInsets
    @Composable
    @OptIn(InternalComposeUiApi::class)
    get() = LocalPlatformWindowInsetsConfig.current.navigationBars.toWindowInsets()

actual val WindowInsets.Companion.statusBars: WindowInsets
    @Composable
    @OptIn(InternalComposeUiApi::class)
    get() = LocalPlatformWindowInsetsConfig.current.statusBars.toWindowInsets()

actual val WindowInsets.Companion.systemBars: WindowInsets
    @Composable
    @OptIn(InternalComposeUiApi::class)
    get() = LocalPlatformWindowInsetsConfig.current.systemBars.toWindowInsets()

actual val WindowInsets.Companion.systemGestures: WindowInsets
    @Composable
    @OptIn(InternalComposeUiApi::class)
    get() = LocalPlatformWindowInsetsConfig.current.systemGestures.toWindowInsets()

actual val WindowInsets.Companion.tappableElement: WindowInsets
    @Composable
    @OptIn(InternalComposeUiApi::class)
    get() = LocalPlatformWindowInsetsConfig.current.tappableElement.toWindowInsets()

actual val WindowInsets.Companion.waterfall: WindowInsets
    @Composable
    @OptIn(InternalComposeUiApi::class)
    get() = LocalPlatformWindowInsetsConfig.current.waterfall.toWindowInsets()

actual val WindowInsets.Companion.safeDrawing: WindowInsets
    @Composable
    @OptIn(InternalComposeUiApi::class)
    get() = LocalPlatformWindowInsetsConfig.current.safeDrawing.toWindowInsets()

actual val WindowInsets.Companion.safeGestures: WindowInsets
    @Composable
    @OptIn(InternalComposeUiApi::class)
    get() = LocalPlatformWindowInsetsConfig.current.safeGestures.toWindowInsets()

actual val WindowInsets.Companion.safeContent: WindowInsets
    @Composable
    @OptIn(InternalComposeUiApi::class)
    get() = LocalPlatformWindowInsetsConfig.current.safeContent.toWindowInsets()

private fun PlatformInsets.toWindowInsets(): WindowInsets = WindowInsets(left, top, right, bottom)