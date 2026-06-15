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
private data class UIKitCursor(val id: String): PointerIcon

internal actual val pointerIconDefault: PointerIcon = UIKitCursor("default")
internal actual val pointerIconCrosshair: PointerIcon = UIKitCursor("crosshair")
internal actual val pointerIconText: PointerIcon = UIKitCursor("text")
internal actual val pointerIconHand: PointerIcon = UIKitCursor("hand")
internal actual val pointerIconColResize: PointerIcon = UIKitCursor("col-resize")
internal actual val pointerIconRowResize: PointerIcon = UIKitCursor("row-resize")
internal actual val pointerIconNResize: PointerIcon = UIKitCursor("n-resize")
internal actual val pointerIconEResize: PointerIcon = UIKitCursor("e-resize")
internal actual val pointerIconSResize: PointerIcon = UIKitCursor("s-resize")
internal actual val pointerIconWResize: PointerIcon = UIKitCursor("w-resize")
internal actual val pointerIconNeResize: PointerIcon = UIKitCursor("ne-resize")
internal actual val pointerIconNwResize: PointerIcon = UIKitCursor("nw-resize")
internal actual val pointerIconSeResize: PointerIcon = UIKitCursor("se-resize")
internal actual val pointerIconSwResize: PointerIcon = UIKitCursor("sw-resize")
internal actual val pointerIconNSResize: PointerIcon = UIKitCursor("ns-resize")
internal actual val pointerIconEWResize: PointerIcon = UIKitCursor("ew-resize")
internal actual val pointerIconNeSwResize: PointerIcon = UIKitCursor("nesw-resize")
internal actual val pointerIconNwSeResize: PointerIcon = UIKitCursor("nwse-resize")
