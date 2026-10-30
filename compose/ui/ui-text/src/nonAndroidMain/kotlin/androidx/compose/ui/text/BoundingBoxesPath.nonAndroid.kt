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

package androidx.compose.ui.text

import androidx.collection.MutableFloatList
import androidx.compose.ui.graphics.Path

// TODO(Merge) Major, Implement after merging 676490de054f88a3fc1c469c1d8dbe177e67b211
// Add non-Android paragraph box extraction and Path construction; until then, the range APIs
// produce empty geometry on non-Android targets.
internal actual fun Paragraph.fillRangeBoundingBoxes(
    startOffset: Int,
    endOffset: Int,
    paragraphTop: Float,
    target: MutableFloatList,
) {}

internal actual fun Path.addClockwiseRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
) {}

internal actual fun Path.addClockwiseRoundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radiusX: Float,
    radiusY: Float,
) {}
