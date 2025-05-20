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

package androidx.xr.glimmer.samples

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.surface

@Preview
@Composable
fun ColorsSample() {
    val colors = GlimmerTheme.colors
    LazyColumn(
        contentPadding = PaddingValues(15.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item { ColorItem(colors.primary, colorName = "primary", Color.Black) }
        item { ColorItem(colors.secondary, colorName = "secondary", Color.Black) }
        item { ColorItem(colors.negative, colorName = "negative", Color.Black) }
        item { ColorItem(colors.positive, colorName = "positive", Color.Black) }
        item { ColorItem(colors.surface, colorName = "surface", Color.White) }
        item { ColorItem(colors.outline, colorName = "outline", Color.White) }
        item { ColorItem(colors.outlineVariant, colorName = "outlineVariant", Color.White) }
    }
}

@Composable
private fun ColorItem(
    color: Color,
    colorName: String,
    onColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .surface(
                shape = RectangleShape,
                color = color,
                border = BorderStroke(1.dp, color = Color.White)
            )
            .fillMaxWidth()
            .height(50.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = colorName,
            style = TextStyle(color = onColor),
            modifier = Modifier.padding(10.dp)
        )
        BasicText(
            text = color.toHexString(),
            style = TextStyle(color = onColor),
            modifier = Modifier.padding(10.dp)
        )
    }
}

/** Returns a hex string, dropping the leading alpha channel and any trailing zeroes. */
private fun Color.toHexString() = this.value.toString(16).substring(2, 8).uppercase()
