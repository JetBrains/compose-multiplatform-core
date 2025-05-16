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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.xr.glimmer.GlimmerTheme

@Preview
@Composable
fun ColorsSample() {
    val colors = GlimmerTheme.colors
    LazyColumn(
        contentPadding = PaddingValues(15.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                ColorAndOnColorItem(
                    colors.primary,
                    colorName = "primary",
                    colors.onPrimary,
                    onColorName = "onPrimary",
                    modifier = Modifier.weight(1f)
                )
                ColorAndOnColorItem(
                    colors.secondary,
                    colorName = "secondary",
                    colors.onSecondary,
                    onColorName = "onSecondary",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                ColorAndOnColorItem(
                    colors.negative,
                    colorName = "negative",
                    colors.onNegative,
                    onColorName = "onNegative",
                    modifier = Modifier.weight(1f)
                )
                ColorAndOnColorItem(
                    colors.positive,
                    colorName = "positive",
                    colors.onPositive,
                    onColorName = "onPositive",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item { ColorItem(colors.surface, colorName = "surface", colors.onSurface) }
        item { ColorItem(colors.surfaceLow, colorName = "surfaceLow", colors.onSurface) }
        item { ColorItem(colors.onSurface, colorName = "onSurface", colors.surface) }
        item { ColorItem(colors.outline, colorName = "outline", colors.onSurface) }
        item { ColorItem(colors.outlineVariant, colorName = "outlineVariant", colors.onSurface) }
    }
}

@Composable
private fun ColorAndOnColorItem(
    color: Color,
    colorName: String,
    onColor: Color,
    onColorName: String,
    modifier: Modifier = Modifier
) {
    Column(modifier.border(1.dp, color = GlimmerTheme.colors.outlineVariant).fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(75.dp).background(color)) {
            BasicText(
                text = colorName,
                style = TextStyle(color = Color.White),
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
            )
            BasicText(
                text = color.toHexString(),
                style = TextStyle(color = Color.White),
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().height(50.dp).background(onColor),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = onColorName,
                style = TextStyle(color = Color.White),
                modifier = Modifier.padding(10.dp)
            )
            BasicText(
                text = onColor.toHexString(),
                style = TextStyle(color = Color.White),
                modifier = Modifier.padding(10.dp)
            )
        }
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
            .border(1.dp, color = GlimmerTheme.colors.outlineVariant)
            .fillMaxWidth()
            .height(50.dp)
            .background(color),
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
