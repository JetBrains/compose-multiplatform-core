/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.ui.input.pointer

// uikit doesn't seem to have NSCursor.
private data class IosPointerIcon(val id: String): PointerIcon

internal actual val pointerIconDefault: PointerIcon = IosPointerIcon("default")
internal actual val pointerIconCrosshair: PointerIcon = IosPointerIcon("crosshair")
internal actual val pointerIconText: PointerIcon = IosPointerIcon("text")
internal actual val pointerIconHand: PointerIcon = IosPointerIcon("hand")
internal actual val pointerIconColResize: PointerIcon = IosPointerIcon("col-resize")
internal actual val pointerIconRowResize: PointerIcon = IosPointerIcon("row-resize")
internal actual val pointerIconNResize: PointerIcon = IosPointerIcon("n-resize")
internal actual val pointerIconEResize: PointerIcon = IosPointerIcon("e-resize")
internal actual val pointerIconSResize: PointerIcon = IosPointerIcon("s-resize")
internal actual val pointerIconWResize: PointerIcon = IosPointerIcon("w-resize")
internal actual val pointerIconNeResize: PointerIcon = IosPointerIcon("ne-resize")
internal actual val pointerIconNwResize: PointerIcon = IosPointerIcon("nw-resize")
internal actual val pointerIconSeResize: PointerIcon = IosPointerIcon("se-resize")
internal actual val pointerIconSwResize: PointerIcon = IosPointerIcon("sw-resize")
internal actual val pointerIconNSResize: PointerIcon = IosPointerIcon("ns-resize")
internal actual val pointerIconEWResize: PointerIcon = IosPointerIcon("ew-resize")
internal actual val pointerIconNeSwResize: PointerIcon = IosPointerIcon("nesw-resize")
internal actual val pointerIconNwSeResize: PointerIcon = IosPointerIcon("nwse-resize")
