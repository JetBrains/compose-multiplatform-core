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

package androidx.compose.mpp.demo.components.text

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun TextAutoSizeDemo() {
    var useAutoSize by remember { mutableStateOf(true) }
    var requestedWidth by remember { mutableFloatStateOf(100f) }
    var fontSize by remember { mutableStateOf(16.sp) }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
        val maximumWidth = maxWidth.value.coerceAtLeast(1f)
        val minimumWidth = minOf(50f, maximumWidth)
        val width = requestedWidth.coerceIn(minimumWidth, maximumWidth)
        val sliderState =
            remember(minimumWidth, maximumWidth) {
                SliderState(value = width, trackRange = minimumWidth..maximumWidth)
            }
        sliderState.value = width

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier =
                    Modifier.toggleable(
                        value = useAutoSize,
                        role = Role.Checkbox,
                        onValueChange = { useAutoSize = it },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = useAutoSize, onCheckedChange = null)
                Text("Use autosize", Modifier.padding(start = 8.dp))
            }
            Text("Width: ${width.roundToInt()} dp")
            Slider(state = sliderState, onValueChange = { requestedWidth = it })
            Text("Font size: ${fontSize.value} sp")
            Text("60 digits in one line")
            Box(
                modifier =
                    Modifier.width(width.dp)
                        .heightIn(min = 48.dp)
                        .border(1.dp, Color.Gray)
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                key(useAutoSize) {
                    Text(
                        text = "123".repeat(20),
                        fontSize = 16.sp,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        autoSize =
                            if (useAutoSize) TextAutoSize.StepBased(minFontSize = 1.sp) else null,
                        onTextLayout = { fontSize = it.layoutInput.style.fontSize },
                    )
                }
            }
        }
    }
}
