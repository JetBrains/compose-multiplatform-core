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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.xr.glimmer.GlimmerTheme

@Preview
@Composable
fun TypographySample() {
    val typography = GlimmerTheme.typography
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { TypeItem("titleLarge", style = typography.titleLarge) }
        item { TypeItem("titleMedium", style = typography.titleMedium) }
        item { TypeItem("titleSmall", style = typography.titleSmall) }
        item { TypeItem("bodyLarge", style = typography.bodyLarge) }
        item { TypeItem("bodyMedium", style = typography.bodyMedium) }
        item { TypeItem("bodySmall", style = typography.bodySmall) }
    }
}

@Composable
private fun TypeItem(name: String, style: TextStyle, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(text = name, style = style.copy(color = Color.White))
        val typeInformation =
            with(style) { "$fontSize / $lineHeight • ${fontWeight?.weight} • $letterSpacing" }
        BasicText(text = typeInformation, style = TextStyle(color = Color.White))
    }
}
