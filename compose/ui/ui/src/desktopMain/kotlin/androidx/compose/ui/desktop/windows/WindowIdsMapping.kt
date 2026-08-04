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

package androidx.compose.ui.desktop.windows

import androidx.compose.ui.desktop.LightweightWindowId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.jetbrains.desktop.win32.Window
import org.jetbrains.desktop.win32.WindowId

private val nextWindowId = AtomicLong(0L)
private val heavyToLight = ConcurrentHashMap<WindowId, LightweightWindowId>()

fun Window.lightweightWindowId(): LightweightWindowId? {
    return heavyToLight[id]
}

fun WindowId.toLightweightWindowId(): LightweightWindowId? {
    return heavyToLight[this]
}

fun Window.assignNewLightweightWindowId(): LightweightWindowId {
    val lightweightWindowId = LightweightWindowId(nextWindowId.getAndIncrement())
    heavyToLight[id] = lightweightWindowId
    return lightweightWindowId
}

fun Window.destroyLightweightWindowId() {
    heavyToLight.remove(id)
}
