/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.foundation.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFold
import kotlin.math.sqrt

internal actual fun CompositionLocalConsumerModifierNode.platformScrollConfig(): ScrollConfig =
    LinuxScrollConfig

private object LinuxScrollConfig : ScrollConfig {
    // See https://developer.apple.com/documentation/appkit/nsevent/1535387-scrollingdeltay
    override fun Density.calculateMouseWheelScroll(event: PointerEvent, bounds: IntSize): Offset {
        // 64 dp value is taken from ViewConfiguration.java, replace with better solution

        val verticalScrollFactor = -64.dp.toPx()

        val horizontalScrollFactor = -64.dp.toPx()

        return event.changes
            .fastFold(Offset.Zero) { acc, c -> acc + c.scrollDelta }
            .let { Offset(it.x * horizontalScrollFactor, it.y * verticalScrollFactor) }
    }
}
