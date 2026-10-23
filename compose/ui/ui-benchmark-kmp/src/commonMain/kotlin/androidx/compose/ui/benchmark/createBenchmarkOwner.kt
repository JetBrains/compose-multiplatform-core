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

package androidx.compose.ui.benchmark

import androidx.compose.ui.node.RootNodeOwner
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import androidx.compose.ui.scene.ComposeSceneInputHandler
import androidx.compose.ui.scene.PointerEventResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers

internal fun createBenchmarkOwner(): RootNodeOwner {
    registerSkikoComposeImplementation()
    return RootNodeOwner(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        size = IntSize(16, 16),
        coroutineContext = Dispatchers.Unconfined,
        platformContext = PlatformContext.Empty(),
        inputHandler =
            ComposeSceneInputHandler(
                prepareForPointerInputEvent = {},
                processPointerInputEvent = { PointerEventResult(false) },
                cancelPointerInput = {},
                processKeyEvent = { false },
            ),
        invalidate = {},
        onChangedExecutor = { it() },
    )
}