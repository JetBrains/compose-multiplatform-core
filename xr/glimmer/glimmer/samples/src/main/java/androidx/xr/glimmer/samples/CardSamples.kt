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

import androidx.annotation.Sampled
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.xr.glimmer.Card
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.Icon
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.list.VerticalList

@Composable
fun CardSampleUsage() {
    VerticalList(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { CardSample() }
        item { CardWithTrailingIconSample() }
        item { CardWithTitleAndSubtitleAndLeadingIconSample() }
        item { CardWithLongText() }
        item { CardWithTitleAndSubtitleAndLeadingIconLongText() }
        item { CardWithTitleAndSubtitleAndLeadingIconAndTrailingIconLongText() }
    }
}

@Sampled
@Composable
fun CardSample() {
    Card { Text("This is a card") }
}

@Sampled
@Composable
fun CardWithTrailingIconSample() {
    Card(trailingIcon = { Icon(FavoriteIcon, "Localized description") }) {
        Text("This is a card with a trailing icon")
    }
}

@Sampled
@Composable
fun CardWithTitleAndSubtitleAndLeadingIconSample() {
    Card(
        title = { Text("Title") },
        subtitle = { Text("Subtitle") },
        leadingIcon = { Icon(FavoriteIcon, "Localized description") },
    ) {
        Text("This is a card with a title, subtitle, and leading icon")
    }
}

@Sampled
@Composable
fun ClickableCardSample() {
    Card(onClick = {}) { Text("This is a card") }
}

@Sampled
@Composable
fun ClickableCardWithTrailingIconSample() {
    Card(onClick = {}, trailingIcon = { Icon(FavoriteIcon, "Localized description") }) {
        Text("This is a card with a trailing icon")
    }
}

@Sampled
@Composable
fun ClickableCardWithTitleAndSubtitleAndLeadingIconSample() {
    Card(
        onClick = {},
        title = { Text("Title") },
        subtitle = { Text("Subtitle") },
        leadingIcon = { Icon(FavoriteIcon, "Localized description") },
    ) {
        Text("This is a card with a title, subtitle, and leading icon")
    }
}

@Composable
fun CardWithLongText() {
    Card {
        Text(
            "This is a card with a lot of text that will wrap to multiple lines. The maximum recommend number of lines of text for a card is 10.",
            maxLines = 10,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CardWithTitleAndSubtitleAndLeadingIconLongText() {
    Card(
        title = { Text("Title") },
        subtitle = { Text("Subtitle") },
        leadingIcon = { Icon(FavoriteIcon, "Localized description") },
    ) {
        Text(
            "This is a card with a lot of text that will wrap to multiple lines. The maximum recommend number of lines of text for a card is 10."
        )
    }
}

@Composable
fun CardWithTitleAndSubtitleAndLeadingIconAndTrailingIconLongText() {
    Card(
        title = { Text("Title") },
        subtitle = { Text("Subtitle") },
        leadingIcon = { Icon(FavoriteIcon, "Localized description") },
        trailingIcon = { Icon(FavoriteIcon, "Localized description") },
    ) {
        Text(
            "This is a card with a lot of text that will wrap to multiple lines. The maximum recommend number of lines of text for a card is 10."
        )
    }
}

@Preview
@Composable
private fun CardPreview() {
    GlimmerTheme { CardSample() }
}

@Preview
@Composable
private fun CardWithLongTextPreview() {
    GlimmerTheme { CardWithLongText() }
}

@Preview
@Composable
private fun CardWithTrailingIconPreview() {
    GlimmerTheme { CardWithTrailingIconSample() }
}

@Preview
@Composable
private fun CardWithTitleAndSubtitleAndLeadingIconPreview() {
    GlimmerTheme { CardWithTitleAndSubtitleAndLeadingIconSample() }
}

@Preview
@Composable
private fun CardWithTitleAndSubtitleAndLeadingIconLongTextPreview() {
    GlimmerTheme { CardWithTitleAndSubtitleAndLeadingIconLongText() }
}
